package com.stevetomadeline.mixin;

import com.stevetomadeline.client.windowx.WindowXCameraBridge;
import com.xutrain.windowx.camera.CameraRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.xutrain.windowx.camera.CameraRenderManager", remap = false)
public abstract class WindowXRenderMixin {
    @Inject(method = "updateWin", at = @At("HEAD"), require = 1)
    private void stevetomadeline$published(CameraRenderManager.CameraBinding binding, CallbackInfo ci) {
        WindowXCameraBridge.onPublished(binding);
    }
}
