#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_WorldViewProjectionMatrix;
in vec3 inPosition;
out vec3 skyDirection;
void main() {
    skyDirection = normalize(inPosition);
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
    gl_Position.z = gl_Position.w;
}
