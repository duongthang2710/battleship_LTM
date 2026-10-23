#import "Common/ShaderLib/GLSLCompat.glsllib"
in vec2 mapUv;
out vec4 fragColor;

float noise(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    vec4 n = fract(sin(vec4(dot(cell, vec2(127.1, 311.7)),
        dot(cell + vec2(1, 0), vec2(127.1, 311.7)),
        dot(cell + vec2(0, 1), vec2(127.1, 311.7)),
        dot(cell + vec2(1, 1), vec2(127.1, 311.7)))) * 43758.5453);
    return mix(mix(n.x, n.y, f.x), mix(n.z, n.w, f.x), f.y);
}

void main() {
    vec2 p = mapUv * 10.0;
    vec2 edge = min(fract(p), 1.0 - fract(p));
    float distanceToEdge = min(edge.x, edge.y);
    vec3 color = mix(vec3(0.004, 0.015, 0.041), vec3(0.010, 0.042, 0.085), noise(p * 0.51));
    float veins = sin(p.x * 2.8 + sin(p.y * 1.7)) + cos(p.y * 2.9 + sin(p.x * 2.1));
    float caustic = pow(max(1.0 - abs(veins) * 3.8, 0.0), 6.0);
    color += vec3(0.010, 0.031, 0.042) * caustic;
    float bevel = 1.0 - smoothstep(0.015, 0.13, distanceToEdge);
    color += vec3(0.002, 0.009, 0.014) * bevel;
    float engraving = 1.0 - smoothstep(0.010, 0.034, distanceToEdge);
    color = mix(color, vec3(0.028, 0.115, 0.170), engraving * 0.72);
    float sheen = exp(-pow((mapUv.x + mapUv.y * 0.7 - 0.70) * 4.0, 2.0));
    color += vec3(0.005, 0.014, 0.022) * sheen;
    fragColor = vec4(color, 0.97);
}
