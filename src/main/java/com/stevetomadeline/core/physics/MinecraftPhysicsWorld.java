package com.stevetomadeline.core.physics;

import com.stevetomadeline.core.plane.PlaneState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import java.util.function.Predicate;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Each Celeste pixel step is tested against actual three-dimensional voxel/entity shapes. */
public final class MinecraftPhysicsWorld implements PhysicsWorld {
    private final Player player;
    private final PlaneState plane;

    public MinecraftPhysicsWorld(Player player, PlaneState plane) {
        this.player = player;
        this.plane = plane;
    }

    public Vec3 position(double x, double y) {
        return new Vec3(plane.heading().locksZ() ? x * plane.heading().rightX() : plane.anchorX(), y,
                plane.heading().locksZ() ? plane.anchorZ() : x * plane.heading().rightZ());
    }

    private boolean solid(AABB box) {
        if (!player.level().getWorldBorder().isWithinBounds(box)) return true;
        for (int x = ((int) Math.floor(box.minX)) >> 4; x <= ((int) Math.floor(box.maxX)) >> 4; x++) {
            for (int z = ((int) Math.floor(box.minZ)) >> 4; z <= ((int) Math.floor(box.maxZ)) >> 4; z++) {
                if (player.level().getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) == null) return true;
            }
        }
        return !player.level().noCollision(player, box);
    }

    @Override public boolean solid(double x, double y, boolean ducking) {
        return solid(CelesteDimensions.forPose(ducking).makeBoundingBox(position(x, y)));
    }

    @Override public boolean water(double x, double y, boolean ducking) {
        return containsFluid(player.level(), CelesteDimensions.forPose(ducking).makeBoundingBox(position(x, y)),
                player.level()::hasChunkAt);
    }

    static boolean containsFluid(BlockGetter level, AABB bounds, Predicate<BlockPos> loaded) {
        AABB box = bounds.deflate(1.0e-7);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!loaded.test(pos)) continue;
            var fluid = level.getFluidState(pos);
            if (!fluid.isEmpty() && pos.getY() + fluid.getHeight(level, pos) > box.minY) return true;
        }
        return false;
    }

    @Override public boolean solidPoint(double x, double y) {
        Vec3 a = position(x, y), b = position(x + 1.0e-5, y);
        double depthX = plane.heading().locksZ() ? 0 : 0.25;
        double depthZ = plane.heading().locksZ() ? 0.25 : 0;
        return solid(new AABB(Math.min(a.x, b.x) - depthX, y - 1.0e-5, Math.min(a.z, b.z) - depthZ,
                Math.max(a.x, b.x) + depthX, y, Math.max(a.z, b.z) + depthZ));
    }
}
