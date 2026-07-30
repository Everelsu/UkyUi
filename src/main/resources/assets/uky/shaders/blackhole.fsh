#version 120

// Schwarzschild black hole, integrated per pixel.
//
// Same physics as the CPU table this replaces: null geodesics from the orbit
// equation d2u/dphi2 + u = 3Mu^2, written as the Cartesian acceleration
//     a = -3M * h^2 * r / |r|^5
// with h = r x v conserved, plus a volumetric accretion disk that the ray marches
// through accumulating emission and absorption.
//
// The coefficient matters: for a = -C*h^2/r^4 the orbit equation comes out as
// u'' + u = C*u^2, so matching 3Mu^2 needs C = 3M. The widely copied 1.5 quietly
// sets M = 1/2 and halves every deflection.
//
// Doing this per pixel instead of from a baked table is what allows the camera to
// move: elevation is just a uniform.

varying vec2 vUv;

uniform float uAspect;      // quad width / height
uniform float uFov;         // tan of half the vertical field of view
uniform float uElevation;   // camera pitch above the disk plane, radians
uniform float uSpin;        // disk rotation phase
uniform float uIntensity;   // master fade
uniform float uGain;        // disk brightness
uniform vec3  uHot;         // inner disk colour
uniform vec3  uMid;
uniform vec3  uCold;        // outer disk colour
uniform int   uSteps;       // integration budget, lowered on weak hardware

// Units: G = c = M = 1. Horizon at 2, photon sphere at 3, ISCO at 6.
const float HORIZON       = 2.02;
const float DISK_INNER    = 6.0;
const float DISK_OUTER    = 30.0;
const float THICKNESS      = 0.05;   // slab half-height as a fraction of radius
// Gaussian sigma as a fraction of the slab thickness. Thickening this was tried as
// a cure for the thin bright arc over the shadow and is not one — that arc is a
// lensing caustic, where the map from screen to disk goes singular, so it is real
// light in a genuinely sub-pixel place and supersampling is what resolves it.
// Thickening only washed the disk out.
const float SIGMA          = 0.10;
const float DENSITY_SCALE  = 900.0;
const float CAMERA_DIST    = 85.0;
// Once past this the ray is gone; the camera sits at 85, so there is no point
// following it much further out than it started.
const float ESCAPE         = 110.0;
/**
 * Impact parameter beyond which a ray bends too little to matter. The photon
 * sphere is at 3 and the disk ends at 30, so anything passing this wide either
 * misses the disk outright or grazes it on an almost straight line.
 */
const float FAR_FIELD      = 42.0;
/** How far before closest approach the integration actually starts. */
const float RUN_IN         = 45.0;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i + vec3(0, 0, 0)), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    return vnoise(p) * 0.5 + vnoise(p * 2.13) * 0.25 + vnoise(p * 4.31) * 0.125;
}

vec3 accel(vec3 p, float h2) {
    float r2 = dot(p, p);
    return -3.0 * h2 * p / (r2 * r2 * sqrt(r2));
}

// Emission and opacity of the disk at a point, or zero outside it.
void sampleDisk(vec3 p, vec3 dir, out vec3 emission, out float density) {
    emission = vec3(0.0);
    density = 0.0;

    float l = length(p.xy);
    if (l <= DISK_INNER || l >= DISK_OUTER) {
        return;
    }
    float thickness = THICKNESS * l;
    if (abs(p.z) > thickness * 0.5) {
        return;
    }

    float sigma = thickness * SIGMA;
    float vertical = exp(-(p.z * p.z) / (2.0 * sigma * sigma));

    // Zero at the inner edge, r^-3/2 falloff, linear taper to the rim.
    // r^-1.5 written as inversesqrt(l)/l: pow() with a non-constant base is one of
    // the most expensive things available, and this runs for every sample of every
    // pixel that touches the disk.
    float invSqrtL = inversesqrt(l);
    float radial = invSqrtL / l
                 * (1.0 - sqrt(DISK_INNER) * invSqrtL)
                 * (1.0 - (l - DISK_INNER) / (DISK_OUTER - DISK_INNER));
    if (radial <= 0.0) {
        return;
    }

    // Keplerian shear: the pattern winds up because the inner disk laps the outer.
    // The sample point is rotated directly rather than going through atan() and
    // back out through cos/sin — the components of the angle are already in p.
    float omega = uSpin * invSqrtL / l * 12.0;
    float cw = cos(omega);
    float sw = sin(omega);
    float turbulence = fbm(vec3((p.x * cw - p.y * sw) * 0.35,
                                (p.x * sw + p.y * cw) * 0.35,
                                p.z * 6.0 + l * 0.2));

    density = vertical * radial * DENSITY_SCALE * (0.35 + turbulence * 1.4);

    // Doppler and gravitational shift. The reference simulator leaves this out,
    // but without it the disk is symmetric and stops reading as spinning.
    float speed = invSqrtL;
    vec2 orbit = vec2(-p.y, p.x) * invSqrtL / l;
    float toward = -dot(normalize(dir.xy + vec2(1e-6)), orbit);
    float gamma = inversesqrt(max(1.0 - speed * speed, 1e-4));
    float g = (1.0 / (gamma * (1.0 - toward))) * sqrt(max(1.0 - 2.0 / l, 0.0));
    float boost = min(g * g * g, 6.0);

    float t = (l - DISK_INNER) / (DISK_OUTER - DISK_INNER);
    vec3 tint = t < 0.35 ? mix(uHot, uMid, t / 0.35) : mix(uMid, uCold, (t - 0.35) / 0.65);

    emission = tint * (5.0 / l) * boost * uGain;
}

void main() {
    float ce = cos(uElevation);
    float se = sin(uElevation);
    vec3 camPos = vec3(0.0, -CAMERA_DIST * ce, CAMERA_DIST * se);

    vec3 fwd = normalize(-camPos);
    vec3 right = vec3(1.0, 0.0, 0.0);
    vec3 up = cross(right, fwd);
    vec3 dir = normalize(fwd + right * (vUv.x * uFov * uAspect) + up * (vUv.y * uFov));

    // Per-pixel offset in [0,1) used to break up the volume sampling.
    float dither = hash(vec3(gl_FragCoord.xy, 1.0));

    // Impact parameter of the undeflected ray. It is conserved along a straight
    // segment, so it can be had before any integration and used to throw work away.
    vec3 hv = cross(camPos, dir);
    float h2 = dot(hv, hv);
    float impact = sqrt(h2);

    // Most of this quad is empty sky well clear of the hole. Such a ray bends
    // negligibly, so whether it matters at all is a straight-line question: does
    // it cross the disk plane inside the rim? If not, it contributes nothing and
    // there is no reason to march it.
    if (impact > FAR_FIELD) {
        float denom = abs(dir.z) < 1e-5 ? 1e-5 : dir.z;
        float t = -camPos.z / denom;
        vec2 crossing = camPos.xy + dir.xy * t;
        float l = length(crossing);
        if (t <= 0.0 || l < DISK_INNER * 0.9 || l > DISK_OUTER * 1.1) {
            gl_FragColor = vec4(0.0);
            return;
        }
    }

    // Skip the long straight run-in. Deflection out here is negligible, and the
    // disk is entirely inside r = 30, so nothing can be missed by starting the
    // integration just before closest approach instead of at the camera.
    float tClosest = -dot(camPos, dir);
    vec3 p = camPos + dir * max(0.0, tClosest - RUN_IN);
    vec3 v = dir;

    // Rays that stay far out need far fewer steps to resolve.
    int budget = impact > FAR_FIELD ? uSteps / 3 : uSteps;

    vec3 colour = vec3(0.0);
    float transmission = 1.0;
    bool captured = false;

    for (int i = 0; i < 512; i++) {
        if (i >= budget) {
            break;
        }
        float r = length(p);
        if (r < HORIZON) {
            captured = true;
            break;
        }
        if (r > ESCAPE || transmission < 0.01) {
            break;
        }

        // Coarse far away, tight where the path bends and where the disk lives.
        // Continuous, with no branch on radius.
        //
        // This used to switch from 0.045*r to 0.10*r at exactly r = 14, and a step
        // size that jumps discontinuously puts a seam in the accumulated brightness
        // at that radius — one of the terraces visible across the rings. Scaling
        // smoothly with r keeps the sampling density proportional to how fast the
        // path is bending, without a discontinuity anywhere.
        float dt = clamp(0.038 * r, 0.025, 1.8);

        // Velocity Verlet: two force evaluations, stable enough at this step size
        // and half the cost of RK4, which matters when it runs per pixel.
        vec3 a = accel(p, h2);
        vec3 pNext = p + v * dt + 0.5 * a * dt * dt;
        vec3 aNext = accel(pNext, h2);
        vec3 vNext = v + 0.5 * (a + aNext) * dt;

        // March the segment when it can touch the slab.
        float lMid = length(mix(p, pNext, 0.5).xy);
        if (lMid > DISK_INNER * 0.8 && lMid < DISK_OUTER * 1.2
                && min(abs(p.z), abs(pNext.z)) < lMid * THICKNESS) {
            const int SUB = 10;
            float ds = distance(p, pNext) / float(SUB);
            for (int s = 0; s < SUB; s++) {
                // Jitter the sample position per pixel. Sampling a volume at fixed
                // offsets deposits light in shells, which is exactly the concentric
                // banding across the disk; scattering the offsets turns that
                // structured artefact into fine noise the eye ignores.
                vec3 q = mix(p, pNext, (float(s) + dither) / float(SUB));
                vec3 emission;
                float density;
                sampleDisk(q, vNext, emission, density);
                if (density > 0.0) {
                    colour += transmission * density * emission * ds;
                    transmission *= exp(-density * ds);
                }
            }
        }

        p = pNext;
        v = vNext;
    }

    // No tone mapping. A knee was added here to tame a jagged white arc over the
    // shadow and then removed again: the arc turned out not to be in the rendered
    // frame at all, and every curve strong enough to have fixed it also visibly
    // dimmed the disk. Letting the highlights clip is what gives the inner disk its
    // white-hot core.

    // The quad has to end somewhere, and the disk's glow does not. Without this
    // the boundary shows up as a hard rectangular edge across the outer haze.
    vec2 edge = abs(vUv);
    float window = smoothstep(1.0, 0.82, max(edge.x, edge.y));

    // Premultiplied output. Alpha is coverage only — how much of the background
    // this path blocks — and the emission rides on top through the ONE factor.
    // Folding brightness into alpha as well darkened everything around the disk.
    float alpha = captured ? 1.0 : clamp(1.0 - transmission, 0.0, 1.0);
    // Dither, to break up 8-bit quantisation.
    //
    // The disk is a very smooth, very dark gradient, so it crosses each of the 256
    // output levels over a wide band of pixels and the eye reads every boundary as a
    // contour line — the staircase running along the rings. Adding well under one
    // level of noise scatters each boundary into a dither pattern that reads as
    // continuous. This is the fix for banding; resolution is not, because the bands
    // are in the colour depth rather than in the geometry.
    colour += (dither - 0.5) * (2.0 / 255.0);

    gl_FragColor = vec4(colour * uIntensity * window, alpha * uIntensity * window);
}
