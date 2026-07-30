#version 120

// Plain passthrough. Unlike the hole's own vertex stage this wants the texture
// coordinate as it comes — it is sampling two buffers that were rendered the same
// way up, so there is nothing to flip.

varying vec2 vUv;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    vUv = gl_MultiTexCoord0.xy;
}
