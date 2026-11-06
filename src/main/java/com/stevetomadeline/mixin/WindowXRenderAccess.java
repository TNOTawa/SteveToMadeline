package com.stevetomadeline.mixin;

import com.xutrain.windowx.camera.CameraRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.Map;

@Pseudo
@Mixin(targets = "com.xutrain.windowx.camera.CameraRenderManager", remap = false)
public interface WindowXRenderAccess {
    @Accessor("bindings")
    static Map<String, CameraRenderManager.CameraBinding> stevetomadeline$bindings() {
        throw new AssertionError("Mixin not applied");
    }

    @Invoker("destroyBinding")
    static void stevetomadeline$destroyBinding(CameraRenderManager.CameraBinding binding) {
        throw new AssertionError("Mixin not applied");
    }
}
