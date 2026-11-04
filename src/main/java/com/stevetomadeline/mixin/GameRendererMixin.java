package com.stevetomadeline.mixin;

import com.mojang.blaze3d.platform.Window;
import com.stevetomadeline.Config;
import com.stevetomadeline.client.mode.RenderModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 2D / 2.5D 模式下把主窗口的透视投影替换成正交投影。
 * 正交半高沿用配置 orthoHalfHeight（WindowX 里这个值的语义也是方块半高）。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;", at = @At("RETURN"),
            cancellable = true, require = 0)
    private void stevetomadeline$orthographic(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (!RenderModeManager.isCameraMode()) {
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / (float) Math.max(1, window.getHeight());
        float halfHeight = (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
        float halfWidth = halfHeight * aspect;
        double distance = Config.CAMERA_DISTANCE.get();
        float far = (float) Math.max(512.0, distance + 512.0);
        cir.setReturnValue(new Matrix4f().ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, 0.05f, far));
    }
}
