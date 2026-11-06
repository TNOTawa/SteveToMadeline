#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float SliceDepth;
uniform float ForegroundOpacity;
uniform float AlphaCutoff;
uniform int ForegroundPass;
in vec4 litColor;
in vec2 atlasUV;
in float fogDistance;
in float viewDepth;
out vec4 fragColor;

void main() {
    bool removed = viewDepth < SliceDepth;
    if (removed != (ForegroundPass != 0)) discard;
    vec4 surface = texture(Sampler0, atlasUV) * litColor * ColorModulator;
    if (surface.a < AlphaCutoff) discard;
    fragColor = linear_fog(surface, fogDistance, FogStart, FogEnd, FogColor);
    if (removed) fragColor.a *= ForegroundOpacity;
    if (fragColor.a <= 0.0) discard;
}
