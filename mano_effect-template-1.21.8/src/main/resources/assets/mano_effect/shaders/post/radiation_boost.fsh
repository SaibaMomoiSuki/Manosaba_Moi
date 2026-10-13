#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

// Hash for edge line segment variation
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec4 original = texture(InSampler, texCoord);

    // === 1. Edge White Lines (屏幕边缘白色短线条) ===
    float edgeMargin = 0.06;    // How thick the edge zone is
    float lineFadeDist = 0.15;  // How far lines extend inward before fully transparent

    // Distance to each edge
    float distLeft = texCoord.x;
    float distRight = 1.0 - texCoord.x;
    float distTop = 1.0 - texCoord.y;
    float distBottom = texCoord.y;

    float minDist = min(min(distLeft, distRight), min(distTop, distBottom));

    // Edge detection: 1.0 inside the edge zone, 0.0 outside
    float isLeftEdge = 1.0 - smoothstep(0.0, edgeMargin, distLeft);
    float isRightEdge = 1.0 - smoothstep(0.0, edgeMargin, distRight);
    float isTopEdge = 1.0 - smoothstep(0.0, edgeMargin, distTop);
    float isBottomEdge = 1.0 - smoothstep(0.0, edgeMargin, distBottom);

    // Short line segments along each edge
    // Left/Right: vertical short lines
    float vertLineL = abs(fract(texCoord.y * 30.0 + hash(vec2(0.1, floor(texCoord.y * 30.0))) * 0.3) - 0.5) * 2.0;
    float vertSegmentL = 1.0 - smoothstep(0.5, 1.0, vertLineL);

    float vertLineR = abs(fract(texCoord.y * 30.0 + hash(vec2(0.9, floor(texCoord.y * 30.0))) * 0.3) - 0.5) * 2.0;
    float vertSegmentR = 1.0 - smoothstep(0.5, 1.0, vertLineR);

    // Top/Bottom: horizontal short lines
    float horzLineT = abs(fract(texCoord.x * 30.0 + hash(vec2(floor(texCoord.x * 30.0), 0.9)) * 0.3) - 0.5) * 2.0;
    float horzSegmentT = 1.0 - smoothstep(0.5, 1.0, horzLineT);

    float horzLineB = abs(fract(texCoord.x * 30.0 + hash(vec2(floor(texCoord.x * 30.0), 0.1)) * 0.3) - 0.5) * 2.0;
    float horzSegmentB = 1.0 - smoothstep(0.5, 1.0, horzLineB);

    // Combine edge lines
    float linePattern = 0.0;
    linePattern += isLeftEdge * vertSegmentL;
    linePattern += isRightEdge * vertSegmentR;
    linePattern += isTopEdge * horzSegmentT;
    linePattern += isBottomEdge * horzSegmentB;

    // Edge fade: gradient from edge (1.0) to lineFadeDist inward (0.0)
    float edgeFade = 1.0 - smoothstep(0.0, lineFadeDist, minDist);
    float lineAlpha = linePattern * edgeFade;

    // === 2. White Screen Overlay (50% transparency) ===
    float whiteOverlay = 0.5;

    // === Combine ===
    fragColor = original;

    // Apply white overlay (50%)
    fragColor.rgb = mix(fragColor.rgb, vec3(1.0), whiteOverlay);

    // Apply edge lines on top
    fragColor.rgb = mix(fragColor.rgb, vec3(1.0), lineAlpha * 0.9);

    fragColor.a = 1.0;
}
