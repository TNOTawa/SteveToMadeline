package com.stevetomadeline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stevetomadeline.core.physics.CelesteDimensions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Broad-phase entity search stays unchanged; ray/projectile narrow-phase uses the hurtbox. */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileHurtboxMixin {
    @WrapOperation(method = "getEntityHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"), require = 1)
    private static AABB stevetomadeline$rememberTarget(Entity entity, Operation<AABB> original,
                                                     @Share("stmTarget") LocalRef<Entity> target) {
        target.set(entity);
        return original.call(entity);
    }

    @WrapOperation(method = "getEntityHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;"), require = 1)
    private static AABB stevetomadeline$preciseHit(AABB box, double margin, Operation<AABB> original,
                                                @Share("stmTarget") LocalRef<Entity> target) {
        Entity entity = target.get();
        return entity != null && CelesteDimensions.usesHurtbox(entity)
                ? CelesteDimensions.hurtbox(entity) : original.call(box, margin);
    }
}
