package com.stevetomadeline.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Camera-sized rays stop at opaque solids, including the occupied parts of slabs/stairs. */
public final class CameraObstruction {
    private static final double RADIUS = .1, CLEARANCE = .05;
    private CameraObstruction() { }

    public record Result(Vec3 position, double distance, boolean obstructed) { }

    public static VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos) {
        boolean shapedSolid = state.getBlock() instanceof SlabBlock || state.getBlock() instanceof StairBlock;
        if (!shapedSolid && (!state.canOcclude() || !state.isCollisionShapeFullBlock(level, pos))) return Shapes.empty();
        return state.getCollisionShape(level, pos, CollisionContext.empty());
    }

    public static Vec3 resolve(BlockGetter level, Vec3 eye, Vec3 desired) {
        return inspect(level, eye, desired).position();
    }

    public static Result inspect(BlockGetter level, Vec3 eye, Vec3 desired) {
        Vec3 offset = desired.subtract(eye);
        double length = offset.length();
        if (length < 1.0e-7) return new Result(eye, 0, false);
        double allowed = length;
        for (int i = -1; i < 8; i++) {
            Vec3 corner = i < 0 ? Vec3.ZERO : new Vec3(((i & 1) * 2 - 1) * RADIUS,
                    ((i >> 1 & 1) * 2 - 1) * RADIUS, ((i >> 2 & 1) * 2 - 1) * RADIUS);
            Vec3 from = eye.add(corner), to = desired.add(corner);
            var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter world, BlockPos pos) {
                    return shape(state, world, pos);
                }
            });
            if (hit.getType() != HitResult.Type.MISS) {
                allowed = Math.min(allowed, Math.max(0, from.distanceTo(hit.getLocation()) - CLEARANCE));
            }
        }
        return new Result(eye.add(offset.scale(allowed / length)), allowed, allowed < length - 1.0e-6);
    }
}
