"""Synthesises the title intro's riser and impact as Ogg Vorbis.

Needs numpy and soundfile: pip install numpy soundfile, then python sounds/synth_intro.py

Writes impact/riser pairs to sounds/variants/ to listen to outside the game,
and copies the chosen one (DEFAULT) into the mod's assets.

Each riser is its own impact's reverb, reversed: the sound is sucked in backwards
and lands on the hit, so the two always belong together. The riser is timed to
TitleIntro.GATHER + IMPLODE (1.27 s) and ends in a short silence.
"""
import os
import shutil

import numpy as np
import soundfile as sf

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
VARIANTS = os.path.join(HERE, "variants")
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "uky", "sounds")
DEFAULT = "deep_full"

RISE = 1.27
VACUUM = 0.09  # silence between the suck-in and the hit
rng = np.random.default_rng(7)


def t_of(seconds):
    return np.arange(int(seconds * SR)) / SR


def lowpass_fft(x, cutoff, slope=1.0):
    """Smooth low-pass (no brick wall, so no ringing), per channel."""
    spec = np.fft.rfft(x, axis=0)
    f = np.fft.rfftfreq(x.shape[0], 1 / SR)
    spec *= (1.0 / (1.0 + (f / cutoff) ** (4 * slope)))[:, None] if x.ndim == 2 else 1.0 / (1.0 + (f / cutoff) ** (4 * slope))
    return np.fft.irfft(spec, x.shape[0], axis=0)


def highpass_fft(x, cutoff):
    spec = np.fft.rfft(x, axis=0)
    f = np.fft.rfftfreq(x.shape[0], 1 / SR)
    g = 1.0 - 1.0 / (1.0 + (f / cutoff) ** 4)
    spec *= g[:, None] if x.ndim == 2 else g
    return np.fft.irfft(spec, x.shape[0], axis=0)


def lowpass_sweep(x, cutoffs):
    """One-pole-cascade low-pass whose cutoff moves per sample (the brass 'bwaa')."""
    a = 1.0 - np.exp(-2 * np.pi * cutoffs / SR)
    y = np.zeros_like(x)
    for _ in range(3):  # three poles
        s = 0.0
        out = np.empty_like(x)
        for i in range(len(x)):
            s += a[i] * (x[i] - s)
            out[i] = s
        x = out
    return x


def reverb(x, seconds, cutoff, wet):
    """Convolution with decaying stereo noise: a big dark hall."""
    ir_t = t_of(seconds)
    ir = rng.standard_normal((len(ir_t), 2)) * np.exp(-ir_t * 6.9 / seconds)[:, None]
    ir = lowpass_fft(ir, cutoff)
    ir[: int(0.02 * SR)] *= np.linspace(0, 1, int(0.02 * SR))[:, None]  # pre-delay softness
    ir /= np.sqrt((ir ** 2).sum(0))
    n = x.shape[0] + len(ir_t)
    size = 1 << (n - 1).bit_length()
    X = np.fft.rfft(x, size, axis=0)
    H = np.fft.rfft(ir, size, axis=0)
    tail = np.fft.irfft(X * H, size, axis=0)[:n]
    dry = np.zeros((n, 2))
    dry[: x.shape[0]] = x
    tail *= np.abs(dry).max() / (np.abs(tail).max() + 1e-9)
    return dry * (1 - wet) + tail * wet


def stereo(m, width=0.0):
    return np.stack([m, m], 1) if width == 0 else np.stack([m * (1 - width), m * (1 + width)], 1)


def normalise(x, peak):
    x = np.tanh(x / np.abs(x).max() * 1.2) / np.tanh(1.2)
    return x / np.abs(x).max() * peak


def fade_out(x, seconds):
    n = int(seconds * SR)
    x[-n:] *= np.linspace(1, 0, n)[:, None] ** 2
    return x


# ------------------------------------------------------------------ layers --

def sub_drop(t, f0, f1, rate, decay):
    f = f1 + (f0 - f1) * np.exp(-rate * t)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * decay) * (1 - np.exp(-t * 60))


def thump(t, decay=14.0):
    n = rng.standard_normal(len(t))
    return lowpass_fft(n, 180) * np.exp(-t * decay) * (1 - np.exp(-t * 200))


def punch(t):
    """A kick-drum knock on top of the sub: the part of a hit you hear, not just feel."""
    knock = np.sin(2 * np.pi * np.cumsum(55 + 110 * np.exp(-t * 28)) / SR) * np.exp(-t * 9)
    click = lowpass_fft(rng.standard_normal(len(t)), 1800) * np.exp(-t * 70) * 0.25
    return (knock + click) * (1 - np.exp(-t * 400))


def overtones(x):
    """Asymmetric saturation of the sub: adds the 2nd/3rd harmonics small speakers can
    play, so the drop is still heard where 30 Hz itself is not."""
    y = np.tanh(x * 3.0 + 0.4) - np.tanh(0.4)
    return lowpass_fft(y - x * 0.9, 900)


def shockwave(t):
    """Pressure front rolling past, left to right, as the ring on screen expands."""
    n = len(t)
    noise = rng.standard_normal(n)
    cut = 180 + 2200 * np.exp(-t * 2.4)
    swept = lowpass_sweep(noise, cut)
    env = (1 - np.exp(-t * 12)) * np.exp(-t * 1.6)
    pan = np.clip(t / 1.2, 0, 1)
    return np.stack([swept * env * (1.1 - 0.6 * pan), swept * env * (0.5 + 0.6 * pan)], 1)


def thunder(t):
    """Rolling rumble in the tail: low noise that swells and settles irregularly."""
    n = len(t)
    out = np.zeros((n, 2))
    for ch in range(2):
        rumble = lowpass_fft(highpass_fft(rng.standard_normal(n), 35), 260)
        wobble = lowpass_fft(rng.standard_normal(n), 3.0)
        wobble = 0.55 + 0.45 * wobble / (np.abs(wobble).max() + 1e-9)
        out[:, ch] = rumble * wobble * np.exp(-t * 0.8) * (1 - np.exp(-t * 4))
    return out / (np.abs(out).max() + 1e-9)


# ---------------------------------------------------------------- variants --

def deep_dry(t):
    body = stereo(lowpass_fft(rng.standard_normal(len(t)), 400) * np.exp(-t * 2.5) * (1 - np.exp(-t * 90)))
    sub = sub_drop(t, 90, 28, 1.6, 0.8)
    return sub, stereo(sub) * 1.2 + stereo(thump(t, 10.0)) * 1.0 + body * 0.6


def impact_deep():
    t = t_of(5.0)
    _, dry = deep_dry(t)
    return reverb(dry, 5.0, 1800, 0.5)


def impact_deep_punch():
    t = t_of(5.0)
    sub, dry = deep_dry(t)
    dry = dry + stereo(punch(t)) * 0.9 + stereo(overtones(sub)) * 0.5
    return reverb(dry, 5.0, 1800, 0.45)


def impact_deep_shock():
    t = t_of(5.0)
    _, dry = deep_dry(t)
    dry = dry + shockwave(t) * 0.45 + thunder(t) * 0.5
    return reverb(dry, 5.0, 1800, 0.5)


def impact_deep_full():
    t = t_of(5.0)
    sub, dry = deep_dry(t)
    dry = (dry + stereo(punch(t)) * 0.9 + stereo(overtones(sub)) * 0.5
           + shockwave(t) * 0.45 + thunder(t) * 0.5)
    return reverb(dry, 5.0, 1800, 0.45)


def riser_from(impact):
    """The impact's own tail, reversed, into a vacuum, over a quickening heartbeat."""
    n = int(RISE * SR)
    body = int((RISE - VACUUM) * SR)
    rev = impact[: int(3.0 * SR)][::-1]
    rev = rev[-body:] if len(rev) >= body else np.pad(rev, ((body - len(rev), 0), (0, 0)))
    rev = rev * (np.linspace(0, 1, body) ** 2.2)[:, None]
    # Heartbeat thumps quickening into the collapse (phase = t^2, like the visuals).
    t = t_of(RISE)
    beats = np.zeros(n)
    phase = t * t * 9.0 / (2 * np.pi)
    for i in np.where(np.diff(np.floor(phase)) > 0)[0]:
        if t[i] > RISE - VACUUM - 0.12:
            break
        tt = np.arange(min(int(0.18 * SR), n - i)) / SR
        beats[i:i + len(tt)] += (np.sin(2 * np.pi * (45 + 30 * np.exp(-tt * 30)) * tt)
                                 * np.exp(-tt * 20) * (0.35 + 0.65 * t[i] / RISE))
    out = np.zeros((n, 2))
    out[:body] = rev / (np.abs(rev).max() + 1e-9)
    out += stereo(beats) * 0.55
    out[body:] = 0.0
    return out


def write(x, path, peak, fade):
    x = highpass_fft(x, 22)  # nothing speakers cannot play; keeps headroom honest
    x = fade_out(normalise(x, peak), fade)
    # In blocks: libsndfile's Vorbis encoder overflows the stack on one big write.
    with sf.SoundFile(path, "w", SR, 2, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(x), 8192):
            f.write(x[i:i + 8192].astype(np.float32))


os.makedirs(VARIANTS, exist_ok=True)
for name, make in (("deep", impact_deep), ("deep_punch", impact_deep_punch),
                   ("deep_shock", impact_deep_shock), ("deep_full", impact_deep_full)):
    impact = make()
    riser = riser_from(impact)
    write(impact, os.path.join(VARIANTS, name + "_impact.ogg"), 0.8, 1.0)
    write(riser, os.path.join(VARIANTS, name + "_riser.ogg"), 0.6, 0.005)
    # Riser straight into impact, the way the intro plays them.
    both = np.concatenate([normalise(riser, 0.6), normalise(impact, 0.8)])
    write(both, os.path.join(VARIANTS, name + "_together.ogg"), 0.8, 1.0)
    print(name, "done")

for part in ("impact", "riser"):
    shutil.copyfile(os.path.join(VARIANTS, DEFAULT + "_" + part + ".ogg"),
                    os.path.join(ASSETS, "intro_" + part + ".ogg"))
print("default:", DEFAULT)
