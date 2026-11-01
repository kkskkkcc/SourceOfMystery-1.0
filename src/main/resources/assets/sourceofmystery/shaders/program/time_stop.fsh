#version 150
uniform sampler2D DiffuseSampler;
uniform float Elapsed;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 delta = texCoord - vec2(0.5);
    float radius = length(delta);
    float pulse = max(0.0, 1.0 - Elapsed);
    float ring = exp(-120.0 * pow(radius - Elapsed * 0.9, 2.0));
    float angle = pulse * ring * 0.55;
    mat2 twist = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    vec2 uv = clamp(vec2(0.5) + twist * delta + normalize(delta + vec2(0.0001)) * ring * pulse * 0.025, 0.001, 0.999);
    vec4 source = texture(DiffuseSampler, uv);
    float gray = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 color = mix(source.rgb, vec3(gray), smoothstep(0.0, 0.8, Elapsed));
    color = mix(color, vec3(1.0) - color, pulse * ring * 0.7);
    fragColor = vec4(color, 1.0);
}
