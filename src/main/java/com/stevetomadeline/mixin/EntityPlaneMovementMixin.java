package com.stevetomadeline.mixin;

import com.stevetomadeline.core.plane.PlanePlayerAccess;
import com.stevetomadeline.core.plane.PlaneState;
import com.stevetomadeline.core.physics.PhysicsController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class EntityPlaneMovementMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, require = 1)
    private Vec3 stevetomadeline$constrainMovement(Vec3 movement) {
        if ((Object) this instanceof Player player) {
            PlaneState state = ((PlanePlayerAccess) player).stevetomadeline$getPlane();
            if (state != null && PhysicsController.controlsMovement(player)) {
                return new Vec3(state.projectX(movement.x), movement.y, state.projectZ(movement.z));
            }
        }
        return movement;
    }
}
