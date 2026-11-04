package com.stevetomadeline.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 Camera 的 protected setter，供分层视差临时移动摄像机。 */
@Mixin(Camera.class)
public interface CameraPoseAccess {
    @Invoker("setPosition")
    void stevetomadeline$setPosition(Vec3 position);

    @Invoker("setRotation")
    void stevetomadeline$setRotation(float yaw, float pitch);
}
