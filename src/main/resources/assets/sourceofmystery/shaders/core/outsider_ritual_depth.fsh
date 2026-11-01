#version 150
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
void main(){
    const float bayer[16]=float[16](0.5,8.5,2.5,10.5,12.5,4.5,14.5,6.5,3.5,11.5,1.5,9.5,15.5,7.5,13.5,5.5);
    ivec2 p=ivec2(gl_FragCoord.xy)%4;
    float alpha=texture(Sampler0,texCoord0).a*vertexColor.a;
    if(alpha<0.1||vertexColor.a<bayer[p.y*4+p.x]/16.0)discard;
    // Real glyph depth clips background clouds. Black input prevents an
    // additive sky color from washing out the later emissive engraving.
    fragColor=vec4(0.0,0.0,0.0,1.0);
}
