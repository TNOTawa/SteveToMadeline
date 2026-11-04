package com.stevetomadeline.mixin;

import com.stevetomadeline.Config;
import com.stevetomadeline.client.mode.RenderModeManager;
import com.stevetomadeline.client.windowx.WindowXCameraBridge;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 2D 模式把 WindowX 正交摄像机的近/远裁剪面收成一层切片，
 * 只保留玩家所在轴前后 sliceHalfThickness 内的区块与实体，
 * 再配合 {@code CameraRenderHandler} 的方块贴图层，得到泰拉瑞亚式的 2D 观感。
 *
 * <p>注入点找不到时（require=0）自动退回原生投影，不会崩游戏。
 */
@Mixin(targets = "com.xutrain.windowx.camera.CameraRenderManager")
public abstract class CameraRenderManagerMixin {
    @Redirect(
            method = "doRender(Lnet/minecraft/client/Minecraft;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4f;ortho(FFFFFF)Lorg/joml/Matrix4f;"),
            require = 0)
    private Matrix4f stevetomadeline$applySlice(
            Matrix4f matrix, float left, float right, float bottom, float top, float zNear, float zFar) {
        float near = zNear;
        float far = zFar;
        try {
            if (WindowXCameraBridge.isActive() && RenderModeManager.isFlat2D()) {
                double distance = Config.CAMERA_DISTANCE.get();
                double half = Config.SLICE_HALF_THICKNESS.get();
                float sliceNear = (float) Math.max(0.05, distance - half);
                float sliceFar = (float) Math.max(sliceNear + 0.1, distance + half);
                near = sliceNear;
                far = Math.min(far, sliceFar);
            }
        } catch (Throwable ignored) {
            // 配置尚未加载时保持原生投影。
        }
        return matrix.ortho(left, right, bottom, top, near, far);
    }
}
