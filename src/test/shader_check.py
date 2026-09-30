"""Self-check for blackhole.fsh optimisations: ports the ray loop to numpy and runs the
old and new variants over the same pixels, comparing work done and the output.

    python src/test/shader_check.py        (needs numpy)

Fails (assert) if the new shader's picture differs from the old one by more than one
8-bit level anywhere, or if it stops saving work.
"""
import numpy as np

HORIZON, DISK_INNER, DISK_OUTER = 2.02, 6.0, 30.0
THICKNESS, SIGMA, DENSITY_SCALE = 0.05, 0.10, 900.0
CAMERA_DIST, ESCAPE, FAR_FIELD, RUN_IN = 85.0, 110.0, 42.0, 45.0
DISK_REACH, SLAB_CUTOFF = 37.0, SIGMA * 4.0
SUB = 10
FOV = 3 * np.sqrt(3) / 85 * 5.6          # SHADOW_ANGULAR * FRAME_HALF_H
ASPECT = 10.0 / 5.6
STEPS = 260                               # balanced preset
GAIN = 1.0
SPIN = 0.7


def hash3(p):
    p = np.mod(p * 0.3183099 + np.array([0.1, 0.2, 0.3]), 1.0) * 17.0
    return np.mod(p[..., 0] * p[..., 1] * p[..., 2] * p.sum(-1), 1.0)


def vnoise(x):
    i = np.floor(x)
    f = x - i
    f = f * f * (3 - 2 * f)
    h = lambda o: hash3(i + np.array(o, float))
    lerp = lambda a, b, t: a + (b - a) * t
    return lerp(lerp(lerp(h([0, 0, 0]), h([1, 0, 0]), f[..., 0]), lerp(h([0, 1, 0]), h([1, 1, 0]), f[..., 0]), f[..., 1]),
                lerp(lerp(h([0, 0, 1]), h([1, 0, 1]), f[..., 0]), lerp(h([0, 1, 1]), h([1, 1, 1]), f[..., 0]), f[..., 1]), f[..., 2])


def fbm(p):
    return vnoise(p) * 0.5 + vnoise(p * 2.13) * 0.25 + vnoise(p * 4.31) * 0.125


def accel(p, h2):
    r2 = (p * p).sum(-1, keepdims=True)
    return -3.0 * h2[:, None] * p / (r2 * r2 * np.sqrt(r2))


def sample_disk(q, d, cutoff, counter):
    """Returns (emission brightness, density); counts full (noise) evaluations."""
    l = np.linalg.norm(q[:, :2], axis=1)
    th = THICKNESS * l
    ok = (l > DISK_INNER) & (l < DISK_OUTER) & (np.abs(q[:, 2]) <= th * cutoff)
    em = np.zeros(len(q))
    den = np.zeros(len(q))
    if not ok.any():
        return em, den
    q, d, l, th = q[ok], d[ok], l[ok], th[ok]
    sig = th * SIGMA
    vertical = np.exp(-(q[:, 2] ** 2) / (2 * sig * sig))
    isl = 1 / np.sqrt(l)
    radial = isl / l * (1 - np.sqrt(DISK_INNER) * isl) * (1 - (l - DISK_INNER) / (DISK_OUTER - DISK_INNER))
    good = radial > 0
    counter[0] += int(good.sum())
    om = np.mod(SPIN * isl / l * 12.0, 2 * np.pi)
    cw, sw = np.cos(om), np.sin(om)
    turb = fbm(np.stack([(q[:, 0] * cw - q[:, 1] * sw) * 0.35, (q[:, 0] * sw + q[:, 1] * cw) * 0.35, q[:, 2] * 6 + l * 0.2], 1))
    dens = vertical * radial * DENSITY_SCALE * (0.35 + turb * 1.4)
    orbit = np.stack([-q[:, 1], q[:, 0]], 1) * (isl / l)[:, None]
    dxy = d[:, :2] + 1e-6
    toward = -(dxy / np.linalg.norm(dxy, axis=1, keepdims=True) * orbit).sum(1)
    gamma = 1 / np.sqrt(np.maximum(1 - isl * isl, 1e-4))
    g = 1 / (gamma * (1 - toward)) * np.sqrt(np.maximum(1 - 2 / l, 0))
    e = (5 / l) * np.minimum(g ** 3, 6.0) * GAIN
    idx = np.where(ok)[0]
    em[idx] = np.where(good, e, 0)
    den[idx] = np.where(good, dens, 0)
    return em, den


def trace(uv, elevation, new):
    n = len(uv)
    ce, se = np.cos(elevation), np.sin(elevation)
    cam = np.array([0.0, -CAMERA_DIST * ce, CAMERA_DIST * se])
    fwd = -cam / np.linalg.norm(cam)
    right = np.array([1.0, 0, 0])
    up = np.cross(right, fwd)
    d = fwd + np.outer(uv[:, 0] * FOV * ASPECT, right) + np.outer(uv[:, 1] * FOV, up)
    d /= np.linalg.norm(d, axis=1, keepdims=True)
    dither = np.random.default_rng(1).random(n)
    h2 = (np.cross(cam, d) ** 2).sum(1)
    impact = np.sqrt(h2)

    alive = np.ones(n, bool)
    far = impact > FAR_FIELD
    t = -cam[2] / np.where(np.abs(d[:, 2]) < 1e-5, 1e-5, d[:, 2])
    cl = np.linalg.norm(cam[:2] + d[:, :2] * t[:, None], axis=1)
    alive &= ~(far & ((t <= 0) | (cl < DISK_INNER * 0.9) | (cl > DISK_OUTER * 1.1)))

    tc = -(d @ cam)
    p = cam + d * np.maximum(0, tc - RUN_IN)[:, None]
    v = d.copy()
    budget = np.where(far, STEPS // 3, STEPS)
    colour = np.zeros(n)
    trans = np.ones(n)
    captured = np.zeros(n, bool)
    work = {"steps": 0, "forces": 0, "noise": [0]}
    a = accel(p, h2)
    if new:
        work["forces"] += int(alive.sum())

    for i in range(512):
        alive &= i < budget
        r = np.linalg.norm(p, axis=1)
        cap = alive & (r < HORIZON)
        captured |= cap
        alive &= ~cap
        alive &= ~((r > ESCAPE) | (trans < 0.01))
        if new:
            alive &= ~((r > DISK_REACH) & ((p * v).sum(1) > 0))
        if not alive.any():
            break
        k = np.where(alive)[0]
        work["steps"] += len(k)
        pk, vk, hk = p[k], v[k], h2[k]
        dt = np.clip(0.038 * r[k], 0.025, 1.8)[:, None]
        if new:
            ak = a[k]
        else:
            ak = accel(pk, hk)
            work["forces"] += len(k)
        pn = pk + vk * dt + 0.5 * ak * dt * dt
        an = accel(pn, hk)
        work["forces"] += len(k)
        vn = vk + 0.5 * (ak + an) * dt
        lmid = np.linalg.norm(((pk + pn) * 0.5)[:, :2], axis=1)
        m = (lmid > DISK_INNER * 0.8) & (lmid < DISK_OUTER * 1.2) & (np.minimum(np.abs(pk[:, 2]), np.abs(pn[:, 2])) < lmid * THICKNESS)
        if m.any():
            j = k[m]
            ds = np.linalg.norm(pn[m] - pk[m], axis=1) / SUB
            for s in range(SUB):
                q = pk[m] + (pn[m] - pk[m]) * ((s + dither[j]) / SUB)[:, None]
                em, den = sample_disk(q, vn[m], SLAB_CUTOFF if new else 0.5, work["noise"])
                colour[j] += trans[j] * den * em * ds
                trans[j] *= np.exp(-den * ds)
        p[k], v[k], a[k] = pn, vn, an

    return np.clip(colour, 0, 1), np.where(captured, 1.0, np.clip(1 - trans, 0, 1)), work


def pixels(w, h):
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = 1 - (np.arange(h) + 0.5) / h * 2
    return np.stack(np.meshgrid(xs, ys), -1).reshape(-1, 2)


if __name__ == "__main__":
    uv = pixels(200, 112)
    for elevation in (0.115, 0.44):     # the title screen's pose, and looking down on it
        c0, a0, w0 = trace(uv, elevation, new=False)
        c1, a1, w1 = trace(uv, elevation, new=True)
        worst = max(np.abs(c1 - c0).max(), np.abs(a1 - a0).max()) * 255
        print("elevation %.3f: steps %d -> %d (%.0f%%), force evals %d -> %d (%.0f%%), "
              "noise evals %d -> %d (%.0f%%), worst pixel diff %.3f of 255"
              % (elevation, w0["steps"], w1["steps"], 100 * w1["steps"] / w0["steps"],
                 w0["forces"], w1["forces"], 100 * w1["forces"] / w0["forces"],
                 w0["noise"][0], w1["noise"][0], 100 * w1["noise"][0] / w0["noise"][0], worst))
        assert worst < 1.0, "picture changed"
        assert w1["forces"] < w0["forces"] and w1["noise"][0] < w0["noise"][0]
    print("ok")
