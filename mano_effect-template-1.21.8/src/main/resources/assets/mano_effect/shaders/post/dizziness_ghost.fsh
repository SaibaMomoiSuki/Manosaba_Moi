#version 150

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float edge = smoothstep(0.18, 0.72, length(centered * vec2(1.0, 0.72)));
    vec2 drift = vec2(centered.y, -centered.x) * (0.002 + edge * 0.003);

    float r = texture(InSampler, texCoord + drift + vec2(0.003, 0.002)).r;
    float g = texture(InSampler, texCoord).g;
    float b = texture(InSampler, texCoord - drift + vec2(-0.003, -0.002)).b;
    vec4 center = vec4(r, g, b, 1.0);

    vec4 ghost1 = texture(InSampler, texCoord + drift * 2.8 + vec2(0.006, 0.003));
    vec4 ghost2 = texture(InSampler, texCoord - drift * 2.2 + vec2(-0.005, 0.002));

    vec3 color = center.rgb * 0.76 + ghost1.rgb * 0.14 + ghost2.rgb * 0.10;
    float vignette = smoothstep(0.38, 0.82, length(centered * vec2(1.0, 0.7)));
    color *= 1.0 - vignette * 0.16;

    fragColor = vec4(color, 1.0);
}
