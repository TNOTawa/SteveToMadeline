package com.stevetomadeline.mixin;

import com.stevetomadeline.Config;
import com.stevetomadeline.client.windowx.WindowXCameraBridge;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 WindowX 正交摄像机的近裁剪面从固定 0.05 提高到可配置的“前景切片距离”。
 *
 * <p>效果：摄像机和玩家之间那一层（几格）的方块不再绘制，等价于把世界在这个位置
 * 切开，让被墙挡住的目标可见。WindowX 没有公开投影接口，所以这里只能 Redirect
 * {@code Matrix4f.ortho}；注入点找不到时（require=0）自动退回原生行为，不会崩游戏。
 */
@Mixin(targets = "com.xutrain.windowx.camera.CameraRenderManager")
public abstract class CameraRenderManagerMixin {
    @Redirect(
            method = "doRender(Lnet/minecraft/client/Minecraft;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4f;ortho(FFFFFF)Lorg/joml/Matrix4f;"),
            require = 0)
    private Matrix4f stevetomadeline$applyForegroundCut(
            Matrix4f matrix, float left, float right, float bottom, float top, float zNear, float zFar) {
        float near = zNear;
        try {
            if (WindowXCameraBridge.isActive()) {
                double distance = Config.CAMERA_DISTANCE.get();
                double cut = Math.min(Config.FOREGROUND_CUT.get(), Math.max(0.0, distance - 0.75));
                if (cut > 0.05 && cut < zFar - 1.0) {
                    near = (float) cut;
                }
            }
        } catch (Throwable ignored) {
            // 配置尚未加载时保持原生近平面。
        }
        return matrix.ortho(left, right, bottom, top, near, zFar);
    }
}
