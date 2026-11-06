package com.stevetomadeline.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface ForegroundLayerAccess {
    @Invoker("renderSectionLayer")
    void stevetomadeline$renderLayer(RenderType layer, double x, double y, double z, Matrix4f view, Matrix4f projection);
}
