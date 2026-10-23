#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "materials/Atmosphere.glsllib"
uniform sampler2D m_ColorMap;
uniform vec4 m_Crop;
uniform float m_Brightness;
uniform float m_Sharpness;
uniform float m_FogStrength;
uniform float m_Opacity;
uniform float m_EdgeFade;
uniform vec3 g_CameraPosition;
in vec2 texCoord;
in vec3 worldPosition;
out vec4 fragColor;
void main() {
    vec2 uv = m_Crop.xy + texCoord * m_Crop.zw;
    vec4 source = texture(m_ColorMap, uv);
    float alpha = source.a * m_Opacity;
    if (m_EdgeFade > 0.0) {
        alpha *= smoothstep(0.0, m_EdgeFade, texCoord.x)
               * smoothstep(0.0, m_EdgeFade, 1.0 - texCoord.x);
    }
    if (alpha < 0.008) discard;
    vec2 pixel = 1.0 / vec2(textureSize(m_ColorMap, 0));
    vec3 neighbors = (texture(m_ColorMap, uv + vec2(pixel.x, 0.0)).rgb
                   + texture(m_ColorMap, uv - vec2(pixel.x, 0.0)).rgb
                   + texture(m_ColorMap, uv + vec2(0.0, pixel.y)).rgb
                   + texture(m_ColorMap, uv - vec2(0.0, pixel.y)).rgb) * 0.25;
    vec3 color = max(source.rgb + (source.rgb - neighbors) * m_Sharpness, vec3(0.0));
    color *= m_Brightness;
    color = mix(color, horizonColor(), seaHaze(worldPosition, g_CameraPosition) * m_FogStrength);
    fragColor = vec4(color, alpha);
}
