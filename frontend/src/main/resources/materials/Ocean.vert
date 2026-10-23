#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "materials/OceanWaves.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;
uniform float m_Time;
uniform float m_WaveAmplitude;
uniform float m_Basin;
in vec3 inPosition;
out vec3 worldPosition;
#ifdef OCEAN_REFLECTION
uniform mat4 m_ReflectionViewProjection;
out vec4 reflectionPosition;
#endif

void main() {
    vec3 p = inPosition;
    vec3 waves = m_Basin > 0.5 ? boardWaves(p.xz, m_Time) : oceanWaves(p.xz, m_Time);
    float fade = m_Basin > 0.5 ? 1.0 : oceanFade(p.xz);
    p.y += m_WaveAmplitude * fade * waves.x;
    worldPosition = (g_WorldMatrix * vec4(p, 1.0)).xyz;
#ifdef OCEAN_REFLECTION
    // Project onto the mean water plane; fragment normals disturb the reflection.
    reflectionPosition = m_ReflectionViewProjection * vec4(worldPosition.x,
        g_WorldMatrix[3].y, worldPosition.z, 1.0);
#endif
    gl_Position = g_WorldViewProjectionMatrix * vec4(p, 1.0);
}
