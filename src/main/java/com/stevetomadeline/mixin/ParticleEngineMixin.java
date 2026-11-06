package com.stevetomadeline.mixin;

import com.stevetomadeline.client.windowx.WindowXCompat;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$skipBackgroundParticles(CallbackInfo ci) {
        if (WindowXCompat.isRenderingBackground()) {
            ci.cancel();
        }
    }
}
