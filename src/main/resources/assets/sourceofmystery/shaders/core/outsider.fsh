#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 surfaceNormal;
in vec3 viewDirection;
out vec4 fragColor;
void main(){
    vec4 base=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;
    if(base.a<0.1)discard;
    vec3 N=normalize(surfaceNormal),V=normalize(viewDirection);
    if(dot(N,V)<0.0)N=-N;
    vec3 L=normalize(vec3(-0.4,0.7,0.8));
    float diffuse=0.62+0.38*smoothstep(-0.15,0.8,dot(N,L));
    float rim=pow(1.0-max(dot(N,V),0.0),3.0);
    float highlight=pow(max(dot(N,normalize(L+V)),0.0),32.0);
    // Soft face lighting; dark fabric/metal retains a visible violet edge.
    float dark=1.0-smoothstep(0.12,0.5,dot(base.rgb,vec3(0.2126,0.7152,0.0722)));
    vec3 ambient=max(lightMapColor.rgb,vec3(0.19,0.17,0.23));
    vec3 lit=base.rgb*diffuse*ambient;
    lit+=vec3(0.18,0.06,0.30)*rim*(0.15+0.85*dark);
    lit+=vec3(0.20,0.18,0.25)*highlight*dark*0.30;
    lit=mix(overlayColor.rgb,lit,overlayColor.a);
    fragColor=linear_fog(vec4(lit,base.a),vertexDistance,FogStart,FogEnd,FogColor);
}
