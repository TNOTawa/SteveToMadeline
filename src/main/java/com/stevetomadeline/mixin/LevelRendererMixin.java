package com.stevetomadeline.mixin;

import com.stevetomadeline.Config;
import com.stevetomadeline.client.ParallaxRenderer;
import com.stevetomadeline.client.mode.RenderModeManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 2D 模式只保留玩家所在轴切片附近的实体，远处实体由雾隐藏。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void stevetomadeline$filterEntitiesInFlat2D(Entity entity, double x, double y, double z,
                                                        float partialTick, PoseStack poseStack,
                                                        MultiBufferSource bufferSource, CallbackInfo ci) {
        if (ParallaxRenderer.isRendering()) {
            ci.cancel();
            return;
        }
        if (!RenderModeManager.isFlat2D()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float yaw = Math.round(player.getYRot() / 90.0f) * 90.0f;
        double radians = Math.toRadians(yaw);
        boolean zPlane = Math.abs(Math.cos(radians)) >= Math.abs(Math.sin(radians));
        double plane = zPlane ? player.getZ() : player.getX();
        double entityAxis = zPlane ? entity.getZ() : entity.getX();
        if (Math.abs(entityAxis - plane) > Config.SLICE_HALF_THICKNESS.get()) {
            ci.cancel();
        }
    }
}
