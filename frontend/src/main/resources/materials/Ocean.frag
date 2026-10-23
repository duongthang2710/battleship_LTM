#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "materials/Atmosphere.glsllib"
#import "materials/OceanWaves.glsllib"

uniform vec3 g_CameraPosition;
uniform float m_Time;
uniform float m_WaveAmplitude;
uniform float m_Basin;
uniform vec3 m_RadiantLight;
uniform vec3 m_AbyssalLight;
uniform vec4 m_Wake0;
uniform vec4 m_Wake1;
uniform vec4 m_Wake2;
uniform vec4 m_Wake3;
uniform vec4 m_Wake4;
uniform vec4 m_Wake5;
uniform vec4 m_Wake6;
uniform vec4 m_Wake7;
uniform vec4 m_Wake8;
uniform vec4 m_Wake9;
uniform vec4 m_Wake10;
uniform vec4 m_Wake11;
#ifdef OCEAN_SKY
uniform sampler2D m_SkyMap;
#endif
#ifdef OCEAN_REFLECTION
uniform sampler2D m_ReflectionMap;
in vec4 reflectionPosition;
#endif
in vec3 worldPosition;
out vec4 fragColor;

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    vec4 seeds = vec4(dot(cell,vec2(127.1,311.7)), dot(cell+vec2(1,0),vec2(127.1,311.7)),
                      dot(cell+vec2(0,1),vec2(127.1,311.7)), dot(cell+vec2(1,1),vec2(127.1,311.7)));
    vec4 value = fract(sin(seeds) * 43758.5453);
    return mix(mix(value.x,value.y,f.x), mix(value.z,value.w,f.x), f.y);
}

float vesselWake(vec4 source, vec2 position, float t) {
    vec2 delta = position - source.xy;
    float reach = 2.2*source.w+13.0;
    float breadth = 4.2*source.w;
    // Reject pixels outside the complete wake footprint before evaluating rotation and foam.
    if (dot(delta,delta)>reach*reach+breadth*breadth) return 0.0;
    float s = sin(source.z), c = cos(source.z);
    vec2 local = vec2(c*delta.x-s*delta.y, s*delta.x+c*delta.y);
    float along = (-local.y-2.2*source.w)/13.0;
    float across = abs(local.x)/(4.2*source.w);
    if (along<=0.0 || along>=1.0 || across>=1.0) return 0.0;
    float branch = 0.16+along*0.64;
    float line = exp(-pow((across-branch)/0.035,2.0));
    float body = 0.2*exp(-across*across/(0.08+along*0.2))*(1.0-along);
    float breakup = 0.65+0.35*sin(along*80.0-t*2.0+local.x*4.0);
    return (line+body)*smoothstep(0.0,0.08,along)*pow(1.0-along,1.6)*breakup;
}

void main() {
    vec2 p = worldPosition.xz;
    float t = m_Time;
    vec3 view = normalize(g_CameraPosition - worldPosition);
    float detail = 1.0 / (1.0 + dot(fwidth(p), fwidth(p)) * 12.0);
    if (m_Basin > 0.5) {
        vec3 waves = boardWaves(p, t);
        vec2 gradient = m_WaveAmplitude * waves.yz;
        gradient += 0.005 * detail * vec2(sin(p.x*15.0+p.y*9.0+t*0.7),cos(p.y*17.0-p.x*7.0-t*0.6));
        vec3 n = normalize(vec3(-gradient.x,1,-gradient.y));
        vec3 halfVector = normalize(normalize(vec3(-0.45,0.82,-0.38))+view);
        float fresnel = 0.025 + 0.975 * pow(1.0-max(dot(n,view),0.0),5.0);
        float a = dot(p,vec2(0.65,0.28)) + t*0.45;
        float b = dot(p,vec2(-0.24,0.90)) - t*0.32;
        vec3 color = vec3(0.002,0.012,0.055) * (0.88+0.22*(0.5+0.5*sin(a)*cos(b)));
        color += vec3(0.018,0.070,0.18)*fresnel;
        color += vec3(0.014,0.030,0.068)*pow(max(dot(n,halfVector),0.0),48.0);
        color += vec3(0.65,0.76,0.95)*pow(max(dot(n,halfVector),0.0),380.0)*0.55;
        float veins = sin(p.x*4.6+sin(p.y*3.8+t*0.19))+cos(p.y*5.1+sin(p.x*3.3-t*0.16));
        color += vec3(0.012,0.048,0.15)*pow(max(1.0-abs(veins)*2.8,0.0),8.0);
        color += vec3(0.019,0.001,0.045)*smoothstep(0.0,5.0,p.x);
        fragColor = vec4(color,1);
        return;
    }

    vec3 waves = oceanWaves(p,t);
    vec2 gradient = m_WaveAmplitude * oceanFade(p) * waves.yz;
    float ripplePhase = (noise(p*0.38+vec2(t*0.025,-t*0.018))-0.5)*5.0;
    gradient += 0.016 * detail * vec2(sin(dot(p,vec2(6.2,8.3))+t*0.65+ripplePhase),
                                     sin(dot(p,vec2(-8.1,4.7))-t*0.83-ripplePhase));
    gradient += 0.007 * detail * vec2(sin(dot(p,vec2(15.7,-10.3))+t*1.2+ripplePhase),
                                     cos(dot(p,vec2(11.2,18.6))-t*0.94));
    vec3 n = normalize(vec3(-gradient.x,1,-gradient.y));
    float fresnel = 0.035 + 0.965 * pow(1.0-max(dot(n,view),0.0),5.0);
    vec3 reflected = reflect(-view,n);
    vec3 reflection = vec3(0.035,0.08,0.15);
#ifdef OCEAN_SKY
    vec2 skyUV = vec2(atan(reflected.z,reflected.x)/6.2831853+0.5,
                     asin(clamp(reflected.y,-1.0,1.0))/3.14159265+0.5);
    reflection = mix(reflection,texture(m_SkyMap,skyUV).rgb*0.32,0.55);
#endif
#ifdef OCEAN_REFLECTION
    vec2 reflectionUV = reflectionPosition.xy/reflectionPosition.w*0.5+0.5;
    float viewDistance = length(g_CameraPosition-worldPosition);
    vec2 distortion = gradient*mix(0.009,0.026,smoothstep(15.0,160.0,viewDistance));
    vec2 sampleUV = reflectionUV+distortion;
    float edge = smoothstep(0.0,0.04,sampleUV.x)*smoothstep(0.0,0.04,1.0-sampleUV.x)
               * smoothstep(0.0,0.04,sampleUV.y)*smoothstep(0.0,0.04,1.0-sampleUV.y);
    if (reflectionPosition.w>0.0) {
        vec3 sceneReflection = texture(m_ReflectionMap,clamp(sampleUV,0.001,0.999)).rgb;
        reflection = mix(reflection,sceneReflection*0.72,edge*0.82);
    }
#endif
    float swell = 0.5+0.25*waves.x;
    vec3 color = mix(vec3(0.002,0.012,0.031),vec3(0.006,0.032,0.055),swell);
    color += reflection * fresnel;
    vec3 halfVector = normalize(normalize(vec3(-0.45,0.82,-0.38))+view);
    float sparkle = pow(max(dot(n,halfVector),0.0),220.0);
    float glints = 0.35+0.65*noise(p*4.5-vec2(t*0.10,t*0.04));
    color += vec3(0.32,0.45,0.57)*sparkle*glints*0.38;
    float foam = smoothstep(0.9,1.4,waves.x)
               * smoothstep(0.57,0.82,noise(p*1.7+vec2(t*0.07,-t*0.04)));
    color += vec3(0.17,0.29,0.35)*foam*detail*0.28;
    float wake = vesselWake(m_Wake0,p,t)+vesselWake(m_Wake1,p,t)
               + vesselWake(m_Wake2,p,t)+vesselWake(m_Wake3,p,t)
               + vesselWake(m_Wake4,p,t)+vesselWake(m_Wake5,p,t)
               + vesselWake(m_Wake6,p,t)+vesselWake(m_Wake7,p,t)
               + vesselWake(m_Wake8,p,t)+vesselWake(m_Wake9,p,t)
               + vesselWake(m_Wake10,p,t)+vesselWake(m_Wake11,p,t);
    color += vec3(0.12,0.22,0.29)*wake*detail;
    vec3 moonDirection = normalize(vec3(62,33,-350)-worldPosition);
    float moonPath = pow(max(dot(reflected,moonDirection),0.0),95.0);
    color += vec3(0.42,0.53,0.75)*moonPath*fresnel*0.48;
    for (int i=0;i<2;i++) {
        vec3 source = i==0 ? m_RadiantLight : m_AbyssalLight;
        float path = pow(max(dot(reflected,normalize(source-worldPosition)),0.0),130.0);
        vec3 tint = i==0 ? vec3(0.8,0.49,0.18) : vec3(0.31,0.035,0.48);
        color += tint*path*fresnel*0.27;
    }
    color = mix(color,horizonColor(),seaHaze(worldPosition,g_CameraPosition));
    fragColor = vec4(color,1);
}
