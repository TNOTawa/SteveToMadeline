package com.stevetomadeline.mixin;

import com.stevetomadeline.client.CameraController;
import com.stevetomadeline.client.mode.RenderModeManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 2D / 2.5D 模式下把主窗口摄像机改成侧视机位，代替 WindowX 独立窗口。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void stevetomadeline$applyCameraMode(BlockGetter level, Entity entity, boolean detached,
                                                 boolean thirdPersonReverse, float partialTick,
                                                 CallbackInfo ci) {
        if (!RenderModeManager.isCameraMode()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        setPosition(CameraController.position(player, partialTick));
        setRotation(CameraController.yaw(player), CameraController.pitch());
    }
}
