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
uniform float uJet;         // relativistic jets: 0 off, else their brightness

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
/**
 * Past this, a ray that is moving outward is done. Outside the photon sphere an
 * outgoing photon never turns back, and nothing of the disk (whose slab ends at
 * r = 30 and |z| < 0.05 l) can be sampled beyond about 36 — so the ~40 steps it
 * used to take on its way out to ESCAPE changed nothing in the pixel.
 */
const float DISK_REACH     = 37.0;
/** Where the vertical Gaussian has fallen to e^-8: sampling past it adds nothing. */
const float SLAB_CUTOFF    = SIGMA * 4.0;

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

/**
 * Three octaves of value noise, evaluated rather than looked up.
 *
 * This was briefly a fetch from a baked 128^3 volume, on the reasoning that the
 * field is constant and recomputing it per sample is waste. The reasoning was sound
 * and the resolution was not: sixteen world units across 128 texels is eight texels
 * per unit, while the finest octave here has features a quarter of a unit wide. That
 * is exactly Nyquist, and trilinear filtering finishes off what sampling at the limit
 * leaves — the top octave disappeared and the disk's banding went smooth. It was
 * reported as broken rings from the first second, which is precisely what it was.
 *
 * The lookup measured 19% off the trace. The trace is now a quarter of what it was,
 * so that 19% is worth about five percent of a frame — nothing like enough to pay for
 * the detail it was quietly eating.
 */
float fbm(vec3 p) {
    return vnoise(p) * 0.5 + vnoise(p * 2.13) * 0.25 + vnoise(p * 4.31) * 0.125;
}

// ---- relativistic jets ----------------------------------------------------
//
// Adapted from the "physical jet" model in Adriwin's black-hole renderer
// (https://github.com/Adriwin06/black-hole, shaders/raytracer/physics/jet.glsl):
// a parabolic funnel (Asada & Nakamura 2012), a fast spine inside a bright sheath,
// standing reconfinement knots, and the hot corona at the base. Integrated along the
// same bent rays as the disk, so the jets are lensed round the shadow like
// everything else rather than painted over it.
//
//   Copyright (c) 2015 Otto Seiskari
//   Copyright (c) 2026 Adriwin
//   Permission is hereby granted, free of charge, to any person obtaining a copy of
//   this software and associated documentation files (the "Software"), to deal in
//   the Software without restriction, including without limitation the rights to
//   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
//   the Software, and to permit persons to whom the Software is furnished to do so,
//   subject to the following conditions: The above copyright notice and this
//   permission notice shall be included in all copies or substantial portions of
//   the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
//
// Their units are Schwarzschild radii; ours are M, so every length is halved first.

/**
 * Widest the jets get, with margin, in M: the funnel tops out under 6 at full length
 * and the corona is gone by 4. Everything that stays outside this cylinder round the
 * axis cannot see them, which is what keeps them cheap.
 */
const float JET_BOUND = 7.0;
/**
 * How far up the axis the jets reach, in Schwarzschild radii. A constant, not a
 * uniform: as one it could reach the trace stale or not at all, and every jet then
 * came out as its base alone, a glow on the rim of the shadow. The jets grow in by
 * brightness instead.
 */
const float JET_LENGTH = 40.0;

const vec3 JET_SPINE = vec3(0.78, 0.88, 1.0);   // hot synchrotron, blue-white
const vec3 JET_FAR   = vec3(1.0, 0.94, 0.88);   // aged electrons, cooler

float jetEmissivity(vec3 p, out float zs) {
    zs = abs(p.z) * 0.5;
    if (zs < 0.8) {
        return 0.0;
    }
    float cyl = length(p.xy) * 0.5;
    float r3 = length(p) * 0.5;

    // Corona at the launch point. Started further out and softened from the
    // original: right at the photon sphere the rays are chaotic, and a 1/r^3 source
    // there came out as speckle rather than glow.
    float corona = smoothstep(1.6, 2.6, zs) * (1.0 - smoothstep(2.5, 5.0, zs))
                 * exp(-1.4 * cyl * cyl) * 0.6 / (r3 * r3 * r3 + 1.5);

    // Parabolic funnel: r ~ z^0.58 far out, a little wider at the base.
    float k = mix(0.72, 0.58, smoothstep(3.0, 8.0, zs));
    float jr = 0.30 * pow(zs, k);
    float rn = cyl / jr;
    if (rn > 1.15) {
        return corona;
    }
    float spine = exp(-4.5 * rn * rn);
    float sheath = 0.6 * exp(-15.0 * (rn - 0.82) * (rn - 0.82));
    float profile = (spine + sheath) * (1.0 - smoothstep(0.95, 1.05, rn));

    float onset = smoothstep(1.4, 3.5, zs);
    // Gentler than the model's z^-1.25: the menu lays a top tint and a vignette over
    // the sky, and under them the steeper falloff left only the glow at the base
    // visible — the jets seemed to live on the shadow's rim.
    float decay = pow(max(zs, 1.0), -0.75);
    float cutoff = 1.0 - smoothstep(JET_LENGTH * 0.7, JET_LENGTH, zs);

    // Standing shocks where the jet re-collimates: bright knots that stay put.
    float knotPhase = sin(3.14159265 * zs / 4.5);
    float knots = 1.0 + 1.6 * knotPhase * knotPhase
                * smoothstep(3.0, 7.0, zs) * (1.0 - smoothstep(JET_LENGTH * 0.6, JET_LENGTH * 0.85, zs));

    return profile * onset * decay * cutoff * knots + corona;
}

/**
 * Relativistic beaming for a jet moving along +-z at Lorentz factor 3, seen along
 * the ray. Normalised to 1 side-on, so near edge-on it is a gentle asymmetry:
 * the jet leaning towards the camera brighter, the one leaning away dimmer.
 */
float jetBeaming(vec3 p, vec3 rayDir) {
    const float GAMMA = 3.0;
    const float BETA = 0.9428;
    float cosTheta = dot(-rayDir, vec3(0.0, 0.0, sign(p.z)));
    float d = 1.0 / (GAMMA * (1.0 - BETA * cosTheta));
    float d0 = 1.0 / GAMMA;
    float b = d / d0;
    return b * b * b;
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
    // Checked against where the density actually lives, not the slab's nominal
    // half-height: the outer fifth of the slab carries under 1e-3 of the peak and
    // was paying the full noise and trigonometry below for it.
    if (abs(p.z) > thickness * SLAB_CUTOFF) {
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
    // Reduced before the trigonometry: cosine and sine are periodic, so this changes
    // nothing about the result and keeps their argument small enough to stay accurate.
    float omega = mod(uSpin * invSqrtL / l * 12.0, 6.28318530718);
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
        bool missesDisk = t <= 0.0 || l < DISK_INNER * 0.9 || l > DISK_OUTER * 1.1;
        // Same question for the jets: does the straight line come near the axis?
        bool missesJets = true;
        if (uJet > 0.0) {
            vec2 n = vec2(dir.y, -dir.x);
            float nl = length(n);
            float axisDistance = nl < 1e-5 ? length(camPos.xy) : abs(dot(camPos.xy, n)) / nl;
            missesJets = axisDistance > JET_BOUND;
        }
        if (missesDisk && missesJets) {
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
    // Carried between steps: the force at the end of one step is the force at the
    // start of the next, so it is evaluated once per step rather than twice.
    vec3 a = accel(p, h2);

    for (int i = 0; i < 512; i++) {
        // A hard limit, deliberately. Letting rays still deep in the field run on to
        // finish their orbit does bring the ring back at low budgets, and it brings
        // it back speckled: the number of turns a ray completes is chaotic in its
        // impact parameter, so neighbouring pixels end up with different counts and
        // the ring breaks into dots. Cutting every ray at the same point is what
        // keeps neighbours agreeing with each other. The budget therefore has to be
        // large enough for the orbit outright, which is what the floor on
        // blackHoleQuality is for.
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
        // Leaving, and nothing out there to meet: past the disk, and either no jets
        // or clear of them and still moving away from the axis.
        if (r > DISK_REACH && dot(p, v) > 0.0
                && (uJet <= 0.0 || (length(p.xy) > JET_BOUND && dot(p.xy, v.xy) > 0.0))) {
            break; // leaving, and nothing out there to meet; see DISK_REACH
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

        // Velocity Verlet: stable enough at this step size and a fraction of the cost
        // of RK4, which matters when it runs per pixel. One new force evaluation per
        // step; the other is carried over from the step before.
        vec3 pNext = p + v * dt + 0.5 * a * dt * dt;
        vec3 aNext = accel(pNext, h2);
        vec3 vNext = v + 0.5 * (a + aNext) * dt;

        float seg = distance(p, pNext);
        if (uJet > 0.0 && min(length(p.xy), length(pNext.xy)) < JET_BOUND + seg) {
            // Three samples along the step: the funnel is narrow at its base, and one
            // sample per step there misses it between neighbouring pixels.
            for (int s = 0; s < 3; s++) {
                vec3 q = mix(p, pNext, (float(s) + 0.5) / 3.0);
                float zs;
                float j = jetEmissivity(q, zs);
                if (j > 0.0) {
                    vec3 tint = mix(JET_SPINE, JET_FAR, smoothstep(2.0, 15.0, zs));
                    colour += transmission * tint * j * jetBeaming(q, vNext) * uJet * seg / 3.0;
                }
            }
        }

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
        a = aNext;
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
