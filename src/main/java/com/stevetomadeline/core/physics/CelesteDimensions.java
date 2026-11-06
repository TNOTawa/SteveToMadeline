package com.stevetomadeline.core.physics;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Feet stay anchored; the reference's 8 by 11 pixels become fixed block dimensions. */
public final class CelesteDimensions {
    public static final EntityDimensions ACTIVE = EntityDimensions.fixed(0.5F, 0.6875F);
    public static final EntityDimensions DUCKING = EntityDimensions.fixed(0.5F, 0.375F);
    public static EntityDimensions forPose(boolean ducking) { return ducking ? DUCKING : ACTIVE; }

    /** Celeste's hurtbox shares the collider's top, excluding its bottom two pixels. */
    public static AABB hurtbox(Vec3 feet, boolean ducking) {
        AABB body = forPose(ducking).makeBoundingBox(feet);
        return body.setMinY(body.minY + 2.0 / 16);
    }

    public static boolean usesHurtbox(Entity entity) {
        return entity instanceof PhysicsPlayerAccess access && access.stevetomadeline$hasCelesteDimensions();
    }

    public static AABB hurtbox(Entity entity) {
        return usesHurtbox(entity) ? hurtbox(entity.position(), ((PhysicsPlayerAccess) entity).stevetomadeline$isDucking())
                : entity.getBoundingBox();
    }

    private CelesteDimensions() {
    }

    /** Prefer standing, then vanilla's smaller poses, without relocating the player. */
    public static Pose exitPose(Player player) {
        for (Pose pose : new Pose[]{Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING}) {
            var box = player.getDefaultDimensions(pose).scale(player.getScale()).makeBoundingBox(player.position());
            if (player.level().getWorldBorder().isWithinBounds(box)
                    && player.level().noCollision(player, box.deflate(1.0e-7))) return pose;
        }
        return null;
    }
}
