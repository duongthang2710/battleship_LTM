#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_ProjectionMatrix;
uniform mat4 g_WorldMatrix;
in vec3 inPosition;
in vec2 inTexCoord;
out vec2 texCoord;
out vec3 worldPosition;
void main() {
    texCoord = inTexCoord;
    worldPosition = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;
    vec4 projected = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
    vec4 anchor = g_WorldViewProjectionMatrix * vec4(0.0, 0.0, 0.0, 1.0);
    // Keep artwork upright; anchor depth still determines its scale and world-space parallax.
    if (anchor.w > 0.0) {
        projected.xy -= g_ProjectionMatrix[2].xy * (anchor.w - projected.w);
        projected.z *= anchor.w / projected.w;
        projected.w = anchor.w;
    }
    gl_Position = projected;
}
