#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
void main(){
    vec4 pattern=texture(Sampler0,texCoord0);
    float gray=0.06+0.46*dot(pattern.rgb,vec3(0.2126,0.7152,0.0722));
    fragColor=vec4(vec3(gray),pattern.a*vertexColor.a)*ColorModulator;
}
