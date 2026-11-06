package com.stevetomadeline.mixin;

import com.stevetomadeline.core.plane.PlanePlayerAccess;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import java.util.Set;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fluid geometry remains in the physics world; vanilla never applies its fluid rules. */
@Mixin(Entity.class)
public abstract class EntityFluidMixin {
    @Shadow protected boolean wasTouchingWater;
    @Shadow protected boolean wasEyeInWater;
    @Shadow protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    @Shadow protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;
    @Shadow @Final private Set<TagKey<Fluid>> fluidOnEyes;
    @Shadow private FluidType forgeFluidTypeOnEyes;

    @Unique private boolean stevetomadeline$ownsFluids() {
        return (Object) this instanceof PlanePlayerAccess access && access.stevetomadeline$getPlane() != null;
    }

    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$replaceFluidContact(CallbackInfoReturnable<Boolean> cir) {
        if (!stevetomadeline$ownsFluids()) return;
        fluidHeight.clear();
        forgeFluidTypeHeight.clear();
        wasTouchingWater = false;
        ((Entity) (Object) this).setSwimming(false);
        cir.setReturnValue(false);
    }

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$noFluidCurrent(CallbackInfo ci) {
        if (!stevetomadeline$ownsFluids()) return;
        fluidHeight.clear();
        forgeFluidTypeHeight.clear();
        ci.cancel();
    }

    @Inject(method = "updateFluidOnEyes", at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$noSubmersionEffects(CallbackInfo ci) {
        if (!stevetomadeline$ownsFluids()) return;
        wasEyeInWater = false;
        fluidOnEyes.clear();
        forgeFluidTypeOnEyes = NeoForgeMod.EMPTY_TYPE.value();
        ci.cancel();
    }

    @Inject(method = {"onAboveBubbleCol", "onInsideBubbleColumn"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$noBubbleImpulse(boolean downwards, CallbackInfo ci) {
        if (stevetomadeline$ownsFluids()) ci.cancel();
    }

    @Inject(method = "isInBubbleColumn", at = @At("HEAD"), cancellable = true, require = 1)
    private void stevetomadeline$noBubbleProperties(CallbackInfoReturnable<Boolean> cir) {
        if (stevetomadeline$ownsFluids()) cir.setReturnValue(false);
    }
}
