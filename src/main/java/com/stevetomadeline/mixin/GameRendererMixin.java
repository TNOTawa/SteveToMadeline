package com.stevetomadeline.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stevetomadeline.Config;
import com.stevetomadeline.client.mode.RenderModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 2D / 2.5D 的主窗口正交投影：
 * <ul>
 *   <li>2D：近裁剪面 = 相机距离 - 切片半厚，只保留玩家所在轴的切片；更远的世界保留为远景层。</li>
 *   <li>2.5D：常规近平面，正常 3D 世界 + 下俯 12°。</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void stevetomadeline$noBobbing(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (RenderModeManager.isCameraMode()) {
            ci.cancel();
        }
    }

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
        float near = 0.05f;
        float far = (float) Math.max(512.0, distance + 512.0);
        if (RenderModeManager.isFlat2D()) {
            double slice = Config.SLICE_HALF_THICKNESS.get();
            near = (float) Math.max(0.05, distance - slice);
            if (Config.LAYERED_PARALLAX.get()) {
                // 远景交给背景缓冲，主画面只保留切片
                far = (float) (distance + slice);
            }
        }
        double perspectiveFov = Config.CAMERA_FOV.get();
        if (perspectiveFov > 0.5) {
            cir.setReturnValue(new Matrix4f().perspective(
                    (float) Math.toRadians(perspectiveFov), aspect, near, far));
        } else {
            cir.setReturnValue(new Matrix4f().ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far));
        }
    }
}
