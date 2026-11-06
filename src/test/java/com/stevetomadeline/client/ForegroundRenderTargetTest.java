package com.stevetomadeline.client;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class ForegroundRenderTargetTest {
    @Test void terrainRenderHooksResolveToExactlyOneCallInTheInstalledMinecraft() throws Exception {
        var level = new ClassNode();
        try (InputStream stream = getClass().getResourceAsStream("/net/minecraft/client/renderer/LevelRenderer.class")) {
            assertNotNull(stream); new ClassReader(stream).accept(level, 0);
        }
        var method = level.methods.stream().filter(m -> m.name.equals("renderSectionLayer")
                && m.desc.equals("(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V")).findFirst().orElseThrow();
        int shaderCalls = 0, eventCalls = 0;
        for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals("com/mojang/blaze3d/systems/RenderSystem") && call.name.equals("getShader")
                    && call.desc.equals("()Lnet/minecraft/client/renderer/ShaderInstance;")) shaderCalls++;
            if (call.owner.equals("net/neoforged/neoforge/client/ClientHooks") && call.name.equals("dispatchRenderStage")
                    && call.desc.equals("(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V")) eventCalls++;
        }
        assertEquals(1, shaderCalls); assertEquals(1, eventCalls);
    }
}
