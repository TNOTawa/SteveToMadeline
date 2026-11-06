#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
out vec4 litColor;
out vec2 atlasUV;
out float fogDistance;
out float viewDepth;

void main() {
    vec3 relativePosition = Position + ChunkOffset;
    vec4 viewPosition = ModelViewMat * vec4(relativePosition, 1.0);
    gl_Position = ProjMat * viewPosition;
    viewDepth = -viewPosition.z;
    fogDistance = fog_distance(relativePosition, FogShape);
    litColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    atlasUV = UV0;
}
