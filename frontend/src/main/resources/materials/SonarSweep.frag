#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform float m_Elapsed;
in vec2 mapUv;
out vec4 fragColor;

void main() {
    // The world-space quad clips the waves and beam to the nine scanned cells.
    vec2 point = (mapUv - vec2(0.5)) * 3.0;
    float distanceFromCenter = length(point);
    float waves = 0.0;
    for (int index = 0; index < 5; index++) {
        float progress = (m_Elapsed - float(index) * 0.2) / 2.0;
        if (progress >= 0.0 && progress <= 1.0) {
            float radius = progress * 2.121321;
            float ring = 1.0 - smoothstep(0.012, 0.035, abs(distanceFromCenter - radius));
            waves = max(waves, ring * pow(1.0 - progress, 0.6));
        }
    }
    float angle = m_Elapsed * 6.283185 / 2.8;
    vec2 direction = vec2(cos(angle), sin(angle));
    float acrossBeam = abs(point.x * direction.y - point.y * direction.x);
    float beam = (1.0 - smoothstep(0.014, 0.045, acrossBeam)) * step(0.0, dot(point, direction));
    float behindBeam = mod(angle - atan(point.y, point.x) + 6.283185, 6.283185);
    float trail = (1.0 - smoothstep(0.0, 0.35, behindBeam)) * 0.18;
    float fade = smoothstep(0.0, 0.15, m_Elapsed) * (1.0 - smoothstep(2.45, 2.8, m_Elapsed));
    float opacity = max(waves * 0.65, beam * 0.65 + trail) * fade;
    fragColor = vec4(vec3(0.25, 0.9, 0.57), opacity);
}
