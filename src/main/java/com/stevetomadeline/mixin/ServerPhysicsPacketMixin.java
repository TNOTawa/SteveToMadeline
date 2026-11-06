package com.stevetomadeline.mixin;

import com.stevetomadeline.core.physics.PhysicsController;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPhysicsPacketMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$authoritativePosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (player.serverLevel().getServer().isSameThread() && PhysicsController.active(player)) {
            // Position and onGround come exclusively from the server simulation.
            // Rotation remains useful for interaction and is independently validated.
            float yaw = packet.getYRot(player.getYRot()), pitch = packet.getXRot(player.getXRot());
            if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
                player.setYRot(yaw);
                player.setXRot(Math.clamp(pitch, -90, 90));
            }
            ci.cancel();
        }
    }
}
