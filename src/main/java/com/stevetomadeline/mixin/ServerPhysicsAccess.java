package com.stevetomadeline.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerPhysicsAccess {
    @Accessor("awaitingPositionFromClient") Vec3 stevetomadeline$pendingTeleport();
    @Accessor("clientIsFloating") void stevetomadeline$setFloating(boolean value);
}
