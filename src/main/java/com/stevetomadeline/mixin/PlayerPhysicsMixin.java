package com.stevetomadeline.mixin;

import com.stevetomadeline.core.physics.PhysicsController;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerPhysicsMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$physics(CallbackInfo ci) {
        if (PhysicsController.travel((Player) (Object) this)) ci.cancel();
    }
}
