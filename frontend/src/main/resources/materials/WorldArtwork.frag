#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "materials/Atmosphere.glsllib"
uniform sampler2D m_ColorMap;
uniform vec4 m_Crop;
uniform float m_Mode;
uniform float m_Opacity;
uniform float m_Brightness;
uniform float m_EdgeFade;
uniform vec3 g_CameraPosition;
in vec2 texCoord;
in vec3 worldPosition;
out vec4 fragColor;
void main() {
    vec4 artwork = texture(m_ColorMap, m_Crop.xy + texCoord * m_Crop.zw);
    float alpha = artwork.a;
    if (m_Mode > 0.5 && m_Mode < 2.5) {
        float radius = length((texCoord - 0.5) * 2.0);
        alpha *= 1.0 - smoothstep(0.48, 0.97, radius);
        if (m_Mode < 1.5)
            alpha *= smoothstep(0.018, 0.16, max(artwork.r, max(artwork.g, artwork.b)));
    } else if (m_Mode < 0.5) {
        alpha *= smoothstep(0.0, 0.035, texCoord.y);
    }
    alpha *= m_Opacity;
    if (m_EdgeFade > 0.0)
        alpha *= smoothstep(0.0, m_EdgeFade, texCoord.x)
               * smoothstep(0.0, m_EdgeFade, 1.0-texCoord.x);
    if (alpha < 0.005) discard;
    float haze = seaHaze(worldPosition, g_CameraPosition);
    vec3 color = mix(artwork.rgb * 0.85 * m_Brightness, horizonColor(), haze * 0.8);
    fragColor = vec4(color, alpha);
}
