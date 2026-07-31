#version 120

// Crossfade between two traces of the hole.
//
// Doing this with two blended quads instead looks like it should work and does
// not. Premultiplied "over" applied twice is only a true interpolation where both
// images are opaque: wherever alpha is low the older trace is left at full
// strength under the newer one, so the pair sums to more than either. The photon
// ring is a thin bright line with soft edges — exactly that case — so it brightened
// across each dissolve and dropped back at the swap, seven times a second.
//
// Mixing premultiplied colour and alpha together, in one pass, is the interpolation
// the two-quad version was only approximating.

varying vec2 vUv;

uniform sampler2D uPrevious;
uniform sampler2D uCurrent;
uniform float uMix;        // 0 = entirely the previous trace, 1 = entirely the new
uniform float uIntensity;  // screen fade, applied on the way out as before
uniform vec2  uTap;        // one pixel of the *output*, in UV
uniform float uRingLift;   // 0 on a large window, 1 on a small one

float luma(vec4 c) {
    return dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
}

/**
 * Keeps the photon ring from thinning out of existence on a small window.
 *
 * The ring is the brightest thing in the picture and also the narrowest — a hair
 * either side of the critical impact parameter. Its width on screen scales with the
 * window, so past about 1000 pixels wide it covers three or four and reads clearly,
 * and below that it collapses onto a single one. Measured across the shadow's edge,
 * a 1296-wide window gave 118/174/199/145 and an 870-wide one gave a lone 218: the
 * ring never actually went missing, it just stopped being wide enough to see.
 *
 * Averaging it down is the physically honest answer and the wrong one to look at, so
 * the brightest of the immediate neighbours is allowed to stand in for the centre.
 * On a smooth gradient that shifts a pixel by one texel's worth of slope and is
 * invisible; on a one-texel spike it carries the spike outwards, which is the whole
 * point. Whole texels are compared and taken, never components, so premultiplied
 * colour stays consistent with its alpha.
 */
vec4 widenThinHighlights(sampler2D tex, vec2 uv) {
    vec4 centre = texture2D(tex, uv);
    if (uRingLift <= 0.0) {
        return centre;
    }

    // Three rings of taps at half a pixel apart, each dimmer than the last. A single
    // tap at exactly one pixel does not work: it jumps the gap and leaves a trough
    // between the peak and the copy of it, which measured as 106/89/223 across the
    // ring — a comb, not a wider line. Overlapping taps that fall off with distance
    // spread the highlight continuously instead, which is what a bright thin thing
    // does through a real lens anyway.
    //
    // Whole texels are scaled and compared, never components, so premultiplied colour
    // stays consistent with its alpha.
    vec4 best = centre;
    float bestLuma = luma(centre);

    for (int i = 1; i <= 3; i++) {
        vec2 d = uTap * (float(i) * 0.5);
        float falloff = 1.0 - float(i) * 0.25;

        vec4 e = texture2D(tex, uv + vec2(d.x, 0.0)) * falloff;
        vec4 w = texture2D(tex, uv - vec2(d.x, 0.0)) * falloff;
        vec4 n = texture2D(tex, uv + vec2(0.0, d.y)) * falloff;
        vec4 s = texture2D(tex, uv - vec2(0.0, d.y)) * falloff;

        float le = luma(e); if (le > bestLuma) { bestLuma = le; best = e; }
        float lw = luma(w); if (lw > bestLuma) { bestLuma = lw; best = w; }
        float ln = luma(n); if (ln > bestLuma) { bestLuma = ln; best = n; }
        float ls = luma(s); if (ls > bestLuma) { bestLuma = ls; best = s; }
    }

    return mix(centre, best, uRingLift);
}

void main() {
    vec4 previous = widenThinHighlights(uPrevious, vUv);
    vec4 current = widenThinHighlights(uCurrent, vUv);
    gl_FragColor = mix(previous, current, uMix) * uIntensity;
}
