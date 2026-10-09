#version 330
#extension GL_ARB_separate_shader_objects : require

// The figure's eyes (BRIGHT and GLOW styles). Runs after the vanilla core/entity vertex shader built with
// EMISSIVE, NO_OVERLAY and NO_CARDINAL_LIGHTING: no lightmap, no per-face shading, so the eye pixels are the
// texture's full white whatever the light. Fog works like the terrain's, at a reduced strength: the vertex
// colour's alpha carries 1 - eyeFogResistance (the renderer's tint), so the eyes fade into fog later than the body.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 6) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    color.rgb *= vertexColor.rgb * ColorModulator.rgb;
    float fogStrength = clamp(vertexColor.a, 0.0, 1.0);
    vec4 fogColor = vec4(FogColor.rgb, FogColor.a * fogStrength);
    fragColor = apply_fog(vec4(color.rgb, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
