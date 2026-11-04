package com.stevetomadeline.mixin;

import com.stevetomadeline.client.mode.RenderModeManager;
import com.stevetomadeline.client.windowx.WindowXCameraBridge;
import com.xutrain.windowx.camera.CameraRenderManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 2D 模式不画任何 3D 区块：窗口里的画面完全由 {@code CameraRenderHandler}
 * 的方块贴图切片 + 实体组成，避免透视到山体内部或天空盒。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void stevetomadeline$skipChunksInFlat2D(RenderType renderType, double x, double y, double z,
                                                    Matrix4f projection, Matrix4f pose, CallbackInfo ci) {
        if (RenderModeManager.isFlat2D()
                && WindowXCameraBridge.isActive()
                && CameraRenderManager.isRendering()) {
            ci.cancel();
        }
    }
}
