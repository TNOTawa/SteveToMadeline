package com.stevetomadeline.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stevetomadeline.Config;
import com.stevetomadeline.client.CameraCulling;
import com.stevetomadeline.client.CameraController;
import com.stevetomadeline.client.DepthProjection;
import com.stevetomadeline.client.ParallaxRenderer;
import com.stevetomadeline.client.TerrainClipping;
import com.stevetomadeline.client.ForegroundFadeRenderer;
import com.stevetomadeline.client.mode.RenderModeManager;
import com.stevetomadeline.client.windowx.WindowXCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 2D / 2.5D 主窗口投影，绘制与 CPU 剔除保持独立：
 * <ul>
 *   <li>DEPTH：按玩家平面校准透视比例，保留连续远景，前景由地形裁切策略决定。</li>
 *   <li>正交 2D：近裁剪面 = 相机距离 - 切片半厚；旧分层纹理就绪时缩短远裁剪面。</li>
 *   <li>2.5D：常规近平面，正常 3D 世界 + 下俯 12°。</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),
            index = 2, require = 1)
    private Matrix4f stevetomadeline$cameraCulling(Matrix4f projection) {
        if (!RenderModeManager.isCameraMode() || WindowXCompat.isRendering() || Config.cameraFov() > 0.5) {
            return projection;
        }
        Window window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / Math.max(1, window.getHeight());
        if (ParallaxRenderer.usesDepthPerspective()) {
            float distance = (float) CameraController.distance();
            return DepthProjection.culling(Config.ORTHO_HALF_HEIGHT.get().floatValue(), aspect,
                    (float) CameraController.projectionDistance(), distance + 512.0f);
        }
        return CameraCulling.orthographic(Config.ORTHO_HALF_HEIGHT.get().floatValue(), aspect,
                (float) (Config.cameraDistance() + 512.0));
    }

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 1)
    private void stevetomadeline$prepareParallax(DeltaTracker tracker, CallbackInfo ci) {
        if (WindowXCompat.isRendering()) return;
        CameraController.beginFrame(tracker.getGameTimeDeltaPartialTick(false));
        TerrainClipping.beginFrame(tracker.getGameTimeDeltaPartialTick(false));
        ForegroundFadeRenderer.beginFrame(tracker.getGameTimeDeltaPartialTick(false));
        ParallaxRenderer.beginFrame(tracker.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void stevetomadeline$noBobbing(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (RenderModeManager.isCameraMode() && !WindowXCompat.isRendering()) {
            ci.cancel();
        }
    }

    @Inject(method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;", at = @At("RETURN"),
            cancellable = true, require = 0)
    private void stevetomadeline$orthographic(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (!RenderModeManager.isCameraMode() || WindowXCompat.isRendering()) {
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / (float) Math.max(1, window.getHeight());
        float halfHeight = (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
        float halfWidth = halfHeight * aspect;
        double distance = CameraController.distance();
        float near = 0.05f;
        float far = (float) Math.max(512.0, distance + 512.0);
        if (RenderModeManager.isFlat2D()) {
            double slice = Config.SLICE_HALF_THICKNESS.get();
            near = TerrainClipping.nearPlane(distance, slice);
            if (ParallaxRenderer.hasBackground()) {
                // 远景交给背景缓冲，主画面只保留切片
                far = (float) (distance + slice);
            }
        }
        double perspectiveFov = Config.cameraFov();
        if (ParallaxRenderer.usesDepthPerspective()) {
            cir.setReturnValue(DepthProjection.create(halfHeight, aspect, (float) CameraController.projectionDistance(), near, far));
        } else if (perspectiveFov > 0.5) {
            cir.setReturnValue(new Matrix4f().perspective(
                    (float) Math.toRadians(perspectiveFov), aspect, near, far));
        } else {
            cir.setReturnValue(new Matrix4f().ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far));
        }
    }
}
