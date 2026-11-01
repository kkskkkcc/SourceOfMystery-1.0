#version 150
uniform sampler2D Sampler0;
uniform sampler2D CloudMask;
uniform float RenderDistance;
uniform vec4 ColorModulator;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPosition;
out vec4 fragColor;
float coverageThreshold(){
    const float bayer[16]=float[16](0.5,8.5,2.5,10.5,12.5,4.5,14.5,6.5,3.5,11.5,1.5,9.5,15.5,7.5,13.5,5.5);
    ivec2 p=ivec2(gl_FragCoord.xy)%4;return bayer[p.y*4+p.x]/16.0;
}
void main(){
    vec4 ink=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;
    if(ink.a<0.1||vertexColor.a<coverageThreshold())discard;
    // Complementary's actual ray-marched cloud distance; 1 means clear sky.
    // Floating-point storage retains distances beyond the terrain view range.
    float cloud=texelFetch(CloudMask,ivec2(gl_FragCoord.xy),0).a;
    if(abs(cloud-1.0)>0.001&&cloud*cloud*RenderDistance<length(viewPosition)-2.0)discard;
    // Solid engraved strokes cover background clouds; clear sheet gaps remain
    // clear. Phase transitions use the matching dither in the depth pass.
    float opacity=smoothstep(0.1,0.4,ink.a/max(vertexColor.a,0.001));
    fragColor=vec4(ink.rgb*2.1,opacity);
}
