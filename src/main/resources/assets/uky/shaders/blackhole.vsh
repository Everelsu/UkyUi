#version 120

// Full-screen-ish quad. gl_Vertex arrives in the GUI's ortho space; the ray
// direction is built in the fragment stage from this normalised coordinate.
varying vec2 vUv;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    // The quad's texture coordinate runs 0 at the top edge to 1 at the bottom,
    // because the GUI's ortho projection puts +y downward. The fragment stage
    // builds the ray from a world-space "up" vector, so y is flipped here — left
    // as-is the whole scene came out mirrored vertically, with the near side of
    // the disk crossing above the shadow instead of below it.
    vUv = vec2(gl_MultiTexCoord0.x * 2.0 - 1.0, 1.0 - gl_MultiTexCoord0.y * 2.0);
}
