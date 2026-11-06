package com.stevetomadeline.mixin;

import com.stevetomadeline.client.ClientPlaneController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlanePacketMixin {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), require = 1)
    private void stevetomadeline$retainSnapVelocity(CallbackInfo ci) {
        // The vanilla handler may first be invoked on the network thread before enqueueing itself.
        if (Minecraft.getInstance().isSameThread()) ClientPlaneController.beforePositionCorrection();
    }
}
