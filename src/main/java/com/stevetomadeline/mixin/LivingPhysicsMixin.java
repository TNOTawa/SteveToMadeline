package com.stevetomadeline.mixin;

import com.stevetomadeline.core.physics.PhysicsController;
import com.stevetomadeline.core.physics.PhysicsPlayerAccess;
import com.stevetomadeline.core.physics.CelesteDimensions;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingPhysicsMixin {
    @Inject(method = "getHitbox", at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$hurtbox(CallbackInfoReturnable<AABB> cir) {
        if ((Object) this instanceof Player player && CelesteDimensions.usesHurtbox(player)) {
            cir.setReturnValue(CelesteDimensions.hurtbox(player));
        }
    }

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$dimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof PhysicsPlayerAccess access && access.stevetomadeline$hasCelesteDimensions()) {
            cir.setReturnValue(CelesteDimensions.forPose(access.stevetomadeline$isDucking()));
        }
    }

    @Inject(method = "knockback", at = @At("TAIL"), require = 1)
    private void stevetomadeline$acceptKnockback(CallbackInfo ci) {
        if ((Object) this instanceof Player player) PhysicsController.knockedBack(player);
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$preventDuplicateJump(CallbackInfo ci) {
        if ((Object) this instanceof Player player && PhysicsController.plane(player) != null) ci.cancel();
    }
}
