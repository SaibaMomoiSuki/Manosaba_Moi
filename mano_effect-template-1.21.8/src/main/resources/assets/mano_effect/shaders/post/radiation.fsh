#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

// Pseudo-random hash function for TV static noise
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// 2D value noise for dust spots
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

// Fractional Brownian Motion for softer dust clusters
float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    for (int i = 0; i < 4; i++) {
        value += amplitude * valueNoise(p * frequency);
        frequency *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    vec4 original = texture(InSampler, texCoord);

    // === 1. TV Static Noise (类似雪花电视) ===
    // Multiple overlapping hash layers at different scales for rich noise
    float noise1 = hash(texCoord * vec2(1200.0, 800.0) + vec2(0.1, 0.3));
    float noise2 = hash(texCoord * vec2(800.0, 600.0) + vec2(0.7, 0.2));
    float noise3 = hash(texCoord * vec2(400.0, 300.0) + vec2(0.4, 0.9));
    float noise4 = hash(texCoord * vec2(200.0, 150.0) + vec2(0.2, 0.6));

    // Blend noise layers - moderate amount, doesn't block screen
    float staticNoise = (noise1 * 0.4 + noise2 * 0.3 + noise3 * 0.2 + noise4 * 0.1);
    float noiseStrength = 0.06;
    vec3 noiseColor = vec3(0.55, 0.55, 0.55) * staticNoise * noiseStrength;

    // === 2. Gray Dust Particles (灰色灰尘，略有模糊) ===
    // Multiple dust spots at fixed screen positions, soft and blur-like
    float dustTotal = 0.0;

    // Create ~10 dust particles distributed across the screen
    for (int d = 0; d < 10; d++) {
        float di = float(d);
        // Unique position for each dust particle (deterministic, spread across screen)
        vec2 dustPos = vec2(
            fract(sin(di * 73.417) * 3847.213),
            fract(sin(di * 157.833) * 4721.559)
        );

        // Distance from current pixel to dust center
        vec2 dustUV = texCoord - dustPos;

        // Wrap-around handling (dust near screen edges)
        vec2 wrapped = dustUV;
        if (abs(dustUV.x) > 0.5) wrapped.x = dustUV.x - sign(dustUV.x);
        if (abs(dustUV.y) > 0.5) wrapped.y = dustUV.y - sign(dustUV.y);

        float dist = length(wrapped);

        // Soft circular dust spot with blur-like edge
        float spotRadius = 0.04 + hash(vec2(di, 0.5)) * 0.06; // Varying sizes
        float dustSpot = smoothstep(spotRadius, 0.0, dist); // Sharp core
        float dustSoft = smoothstep(spotRadius * 2.5, 0.0, dist) * 0.35; // Soft outer glow

        // Varying opacity per dust particle
        float opacity = 0.06 + hash(vec2(di, 1.0)) * 0.08;

        dustTotal += (dustSpot * 0.55 + dustSoft * 0.45) * opacity;
    }

    vec3 dustColor = vec3(0.35, 0.35, 0.35) * dustTotal;

    // === 3. Elliptical Vignette (radiation zone indicator) ===
    // Aspect-corrected elliptical distance from screen center
    vec2 center = vec2(0.5, 0.5);
    vec2 aspectCorrect = (texCoord - center) * vec2(1.0, 0.5625); // 16:9 aspect ratio correction
    float ellipseDist = length(aspectCorrect) * 1.6; // scale so corners ≈ 1.0

    // Vignette: darken edges progressively, center stays clear
    float vignette = smoothstep(0.35, 0.85, ellipseDist);
    vec3 vignetteColor = vec3(0.12, 0.12, 0.12) * vignette * 0.40;

    // Subtle elliptical border glow at the transition zone
    float borderInner = smoothstep(0.45, 0.52, ellipseDist);
    float borderOuter = smoothstep(0.62, 0.55, ellipseDist);
    float border = borderInner * borderOuter;
    vec3 borderColor = vec3(0.55, 0.45, 0.15) * border * 0.12; // warm dark-gold glow

    // === Combine ===
    fragColor = original;
    fragColor.rgb += noiseColor + dustColor + vignetteColor + borderColor;
    fragColor.a = 1.0;
}
