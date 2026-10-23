#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "materials/Atmosphere.glsllib"
uniform sampler2D m_SkyMap;
in vec3 skyDirection;
out vec4 fragColor;
void main() {
    vec3 direction = normalize(skyDirection);
    vec2 uv = vec2(0.5 + atan(direction.x, direction.z) / 6.2831853,
                   0.5 + asin(clamp(direction.y, -1.0, 1.0)) / 3.1415927);
    vec3 sky = texture(m_SkyMap, uv).rgb;
    float seam = 1.0 - smoothstep(0.0, 0.025, min(uv.x, 1.0 - uv.x));
    vec3 edgeAverage = (texture(m_SkyMap, vec2(0.02, uv.y)).rgb
                     + texture(m_SkyMap, vec2(0.98, uv.y)).rgb) * 0.5;
    sky = mix(sky, edgeAverage, seam);
    // A continuous spherical sky, with its horizon matched to the ocean's atmospheric fog.
    vec3 color = mix(horizonColor(), sky * 0.60, smoothstep(-0.015, 0.12, direction.y));
    fragColor = vec4(color, 1.0);
}
