package com.stevetomadeline.mixin;

import com.stevetomadeline.client.CameraController;
import com.stevetomadeline.client.CameraFog;
import net.minecraft.world.level.material.FogType;
import com.stevetomadeline.client.mode.RenderModeManager;
import com.stevetomadeline.client.windowx.WindowXCompat;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 2D / 2.5D 模式下把主窗口摄像机改成侧视机位，代替 WindowX 独立窗口。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "getFluidInCamera", at = @At("RETURN"), cancellable = true, require = 1)
    private void stevetomadeline$noLiquidFog(CallbackInfoReturnable<FogType> cir) {
        if (RenderModeManager.isCameraMode() && !WindowXCompat.isRendering() && CameraFog.liquid(cir.getReturnValue()))
            cir.setReturnValue(FogType.NONE);
    }

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static RenderModeManager.Mode LAST_LOGGED;

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void stevetomadeline$applyCameraMode(BlockGetter level, Entity entity, boolean detached,
                                                 boolean thirdPersonReverse, float partialTick,
                                                 CallbackInfo ci) {
        if (!RenderModeManager.isCameraMode() || WindowXCompat.isRendering()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Vec3 position = CameraController.position(player, partialTick);
        setPosition(position);
        setRotation(CameraController.renderYaw(partialTick), CameraController.pitch());
        // 强制第三人称语义，让玩家模型始终参与渲染。
        this.detached = true;
        if (LAST_LOGGED != RenderModeManager.mode()) {
            LAST_LOGGED = RenderModeManager.mode();
            LOG.info("[STM] camera mixin active: mode={}, pos={}, yaw={}, pitch={}",
                    RenderModeManager.mode(), position, CameraController.yaw(player), CameraController.pitch());
        }
    }
}
