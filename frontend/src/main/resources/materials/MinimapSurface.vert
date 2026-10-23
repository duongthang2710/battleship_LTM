#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_WorldViewProjectionMatrix;
in vec3 inPosition;
in vec2 inTexCoord;
out vec2 mapUv;
void main() {
    mapUv = inTexCoord;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
