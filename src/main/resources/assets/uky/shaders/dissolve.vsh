#version 150

// Plain passthrough. Unlike the hole's own vertex stage this wants the texture
// coordinate as it comes — it is sampling two buffers that were rendered the same
// way up, so there is nothing to flip.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUv = UV0;
}
