#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;
in vec3 inPosition;
in vec2 inTexCoord;
out vec2 boardPosition;
out vec3 worldPosition;
void main() {
    boardPosition = inTexCoord * 10.0 - 5.0;
    worldPosition = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
