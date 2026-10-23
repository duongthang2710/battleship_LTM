#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform vec3 g_CameraPosition;
uniform float m_Time;
in vec2 boardPosition;
in vec3 worldPosition;
out vec4 fragColor;

float noise(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    vec4 seeds = vec4(dot(cell,vec2(127.1,311.7)),dot(cell+vec2(1,0),vec2(127.1,311.7)),
        dot(cell+vec2(0,1),vec2(127.1,311.7)),dot(cell+vec2(1,1),vec2(127.1,311.7)));
    vec4 v = fract(sin(seeds)*43758.5453);
    return mix(mix(v.x,v.y,f.x),mix(v.z,v.w,f.x),f.y);
}

void main() {
    vec2 p = boardPosition;
    float t = m_Time*0.12;
    float depth = noise(p*0.63+vec2(t*0.05,-t*0.03));
    vec3 color = mix(vec3(0.005,0.026,0.082),vec3(0.014,0.095,0.225),depth);
    float veins = sin(p.x*4.3+sin(p.y*3.1+t))+cos(p.y*3.8+sin(p.x*2.7-t));
    float caustic = pow(max(1.0-abs(veins)*3.8,0.0),7.0);
    float facets = abs(noise(p*1.8)-noise(p*1.8+vec2(0.18,0.23)));
    color += vec3(0.025,0.105,0.200)*caustic;
    color += vec3(0.004,0.020,0.065)*facets;
    // Beveled crystalline cells stay below the separate geometry grid.
    vec2 cell = fract(p) - 0.5;
    float edge = max(abs(cell.x),abs(cell.y));
    float bevel = smoothstep(0.39,0.49,edge);
    vec2 slope = sign(cell)*smoothstep(vec2(0.39),vec2(0.49),abs(cell))*0.014;
    color += vec3(0.003,0.014,0.026)*bevel;
    vec3 normal = normalize(vec3(slope.x + 0.024*sin(p.x*3.2+p.y*1.1+t),1,
        slope.y + 0.024*cos(p.y*3.8-p.x*0.8-t)));
    vec3 view = normalize(g_CameraPosition-worldPosition);
    float facing = max(dot(normal,view),0.0);
    float fresnel = 0.035 + 0.965*pow(1.0-facing,4.0);
    vec3 reflected = reflect(-view,normal);
    // Analytic studio reflection: no extra render pass or environment changes.
    float sky = smoothstep(-0.10,0.85,reflected.y);
    vec3 reflection = mix(vec3(0.016,0.045,0.105),vec3(0.10,0.23,0.38),sky);
    float ribbon = exp(-pow((reflected.x+reflected.z*0.48-0.10)*3.8,2.0));
    reflection += vec3(0.14,0.20,0.26)*ribbon;
    color = mix(color,reflection,fresnel*0.48);

    vec3 key = normalize(vec3(-0.38,0.86,-0.33));
    vec3 rim = normalize(vec3(0.65,0.40,0.70));
    float keySpec = pow(max(dot(normal,normalize(key+view)),0.0),160.0);
    float rimSpec = pow(max(dot(normal,normalize(rim+view)),0.0),65.0);
    float softSpec = pow(max(dot(normal,normalize(key+view)),0.0),18.0);
    color += vec3(0.65,0.52,0.33)*keySpec*0.36;
    color += vec3(0.10,0.23,0.46)*rimSpec*0.27;
    color += vec3(0.032,0.052,0.070)*softSpec;

    float sweep = exp(-pow((dot(p,vec2(0.72,0.36))-sin(m_Time*0.16)*7.0)*1.35,2.0));
    color += vec3(0.010,0.029,0.045)*sweep*(0.25+fresnel)* (0.4+caustic);
    // Restrained glints along engraved cell edges, brightest at grazing views.
    float shimmer = pow(max(sin(p.x*2.1+p.y*1.7+m_Time*0.32),0.0),12.0);
    color += vec3(0.009,0.025,0.038)*bevel*shimmer*fresnel;
    fragColor = vec4(color,1);
}
