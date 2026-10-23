#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;
in vec3 inPosition;
in vec2 inTexCoord;
out vec2 texCoord;
out vec3 worldPosition;
void main() {
    texCoord = inTexCoord;
    worldPosition = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
