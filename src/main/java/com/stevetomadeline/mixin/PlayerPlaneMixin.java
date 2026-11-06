package com.stevetomadeline.mixin;

import com.stevetomadeline.core.plane.PlanePlayerAccess;
import com.stevetomadeline.core.plane.PlaneState;
import com.stevetomadeline.core.physics.PhysicsPlayerAccess;
import com.stevetomadeline.core.physics.PhysicsSession;
import com.stevetomadeline.core.physics.CelesteDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public abstract class PlayerPlaneMixin implements PlanePlayerAccess, PhysicsPlayerAccess {
    @Unique private PhysicsSession stevetomadeline$physics;
    @Override public PhysicsSession stevetomadeline$getPhysics() { return stevetomadeline$physics; }
    @Override public void stevetomadeline$setPhysics(PhysicsSession session) { stevetomadeline$physics = session; }
    @Unique private PlaneState stevetomadeline$plane;
    @Unique private long stevetomadeline$lastTurnTick = Long.MIN_VALUE;
    @Unique private boolean stevetomadeline$turnLocked;
    @Unique private boolean stevetomadeline$celesteDimensions;
    @Unique private boolean stevetomadeline$ducking;

    @Override public boolean stevetomadeline$isDucking() { return stevetomadeline$ducking; }
    @Override public void stevetomadeline$setDucking(boolean ducking) {
        if (stevetomadeline$ducking == ducking) return;
        stevetomadeline$ducking = ducking;
        if (stevetomadeline$celesteDimensions) ((Player) (Object) this).refreshDimensions();
    }

    @Override public boolean stevetomadeline$hasCelesteDimensions() { return stevetomadeline$celesteDimensions; }
    @Override public void stevetomadeline$setCelesteDimensions(boolean enabled) {
        if (stevetomadeline$celesteDimensions == enabled) return;
        stevetomadeline$celesteDimensions = enabled;
        if (!enabled) stevetomadeline$ducking = false;
        Player player = (Player) (Object) this;
        if (!enabled && player.isAlive() && !player.isSleeping() && !player.isPassenger()
                && !player.isFallFlying() && !player.isSpectator() && !player.getAbilities().flying) {
            Pose pose = CelesteDimensions.exitPose(player);
            if (pose != null) player.setPose(pose);
        }
        player.refreshDimensions();
    }

    @Override public PlaneState stevetomadeline$getPlane() { return stevetomadeline$plane; }
    @Override public void stevetomadeline$setPlane(PlaneState state) {
        stevetomadeline$plane = state;
        if (state == null) stevetomadeline$turnLocked = false;
        stevetomadeline$setCelesteDimensions(state != null);
    }
    @Override public long stevetomadeline$getLastTurnTick() { return stevetomadeline$lastTurnTick; }
    @Override public void stevetomadeline$setLastTurnTick(long tick) { stevetomadeline$lastTurnTick = tick; }
    @Override public boolean stevetomadeline$isTurnLocked() { return stevetomadeline$turnLocked; }
    @Override public void stevetomadeline$setTurnLocked(boolean locked) { stevetomadeline$turnLocked = locked; }
}
