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

void main() {
    vec4 previous = texture2D(uPrevious, vUv);
    vec4 current = texture2D(uCurrent, vUv);
    gl_FragColor = mix(previous, current, uMix) * uIntensity;
}
