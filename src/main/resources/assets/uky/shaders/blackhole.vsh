#version 150

// Full-screen-ish quad. Position arrives in the GUI's ortho space; the ray
// direction is built in the fragment stage from this normalised coordinate.
in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // The quad's texture coordinate runs 0 at the top edge to 1 at the bottom,
    // because the GUI's ortho projection puts +y downward. The fragment stage
    // builds the ray from a world-space "up" vector, so y is flipped here — left
    // as-is the whole scene came out mirrored vertically, with the near side of
    // the disk crossing above the shadow instead of below it.
    vUv = vec2(UV0.x * 2.0 - 1.0, 1.0 - UV0.y * 2.0);
}
