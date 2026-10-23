#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "materials/Atmosphere.glsllib"
uniform sampler2D m_ColorMap;
uniform vec4 m_Crop;
uniform float m_Opacity;
uniform float m_Brightness;
uniform vec3 g_CameraPosition;
in vec2 texCoord;
in vec3 worldPosition;
out vec4 fragColor;
void main() {
    vec2 uv = m_Crop.xy+texCoord*m_Crop.zw;
    vec4 source = texture(m_ColorMap, uv);
    if (source.a < 0.10) discard;
    float alpha = source.a * m_Opacity;
    if (alpha < 0.008) discard;
    vec2 pixel = 1.0 / vec2(textureSize(m_ColorMap, 0));
    vec4 left = texture(m_ColorMap, uv-vec2(pixel.x,0));
    vec4 right = texture(m_ColorMap, uv+vec2(pixel.x,0));
    vec4 down = texture(m_ColorMap, uv-vec2(0,pixel.y));
    vec4 up = texture(m_ColorMap, uv+vec2(0,pixel.y));
    // Transparent pixels must not create bright sharpening halos along roofs and towers.
    float weight = left.a+right.a+down.a+up.a;
    vec3 average = (left.rgb*left.a+right.rgb*right.a+down.rgb*down.a+up.rgb*up.a)/max(weight,0.001);
    vec3 color = max(source.rgb+(source.rgb-average)*0.20,vec3(0));
    color *= vec3(0.90,0.95,1.0)*m_Brightness;
    color = mix(color,horizonColor(),seaHaze(worldPosition,g_CameraPosition)*0.10);
    fragColor = vec4(color,alpha);
}
