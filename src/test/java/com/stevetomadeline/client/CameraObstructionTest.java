package com.stevetomadeline.client;

import com.stevetomadeline.BlockTestWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraObstructionTest {
    @Test void decorationsAndTransparentBlocksDoNotMoveTheCamera() {
        var world = new BlockTestWorld();
        var eye = new Vec3(.5, .5, .5); var desired = new Vec3(.5, .5, -8);
        for (Block block : new Block[]{Blocks.SHORT_GRASS, Blocks.DANDELION, Blocks.COBWEB, Blocks.TORCH,
                Blocks.REDSTONE_WIRE, Blocks.REDSTONE_TORCH, Blocks.GLASS, Blocks.RED_STAINED_GLASS,
                Blocks.TINTED_GLASS, Blocks.GLASS_PANE, Blocks.OAK_LEAVES, Blocks.WATER, Blocks.LAVA}) {
            world.blocks.put(new BlockPos(0, 0, -3), block.defaultBlockState());
            assertEquals(desired, CameraObstruction.resolve(world, eye, desired), block.toString());
        }
    }

    @Test void opaqueWallBetweenPlayerAndCameraClampsEvenWhenTheDesiredPositionIsAir() {
        var world = new BlockTestWorld();
        world.blocks.put(new BlockPos(0, 0, -3), Blocks.STONE.defaultBlockState());
        var eye = new Vec3(.5, .5, .5);
        Vec3 result = CameraObstruction.resolve(world, eye, new Vec3(.5, .5, -8));
        assertEquals(-1.85, result.z, 1e-6);
        assertEquals(eye.x, result.x); assertEquals(eye.y, result.y);
        world.blocks.clear();
        assertEquals(new Vec3(.5, .5, -8), CameraObstruction.resolve(world, eye, new Vec3(.5, .5, -8)));
    }

    @Test void cameraInsideABlockMovesToThePlayerSideOfItsFace() {
        var world = new BlockTestWorld();
        world.blocks.put(new BlockPos(0, 0, -3), Blocks.STONE.defaultBlockState());
        Vec3 result = CameraObstruction.resolve(world, new Vec3(.5, .5, .5), new Vec3(.5, .5, -2.5));
        assertTrue(result.z > -2); assertTrue(result.z < .5);
    }

    @Test void slabUsesItsOccupiedHalfRatherThanAFullCube() {
        var world = new BlockTestWorld(); var pos = new BlockPos(0, 0, -3);
        world.blocks.put(pos, Blocks.STONE_SLAB.defaultBlockState());
        assertTrue(CameraObstruction.resolve(world, new Vec3(.5, .25, .5), new Vec3(.5, .25, -8)).z > -2);
        assertEquals(new Vec3(.5, .8, -8), CameraObstruction.resolve(world, new Vec3(.5, .8, .5), new Vec3(.5, .8, -8)));
        world.blocks.put(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        assertTrue(CameraObstruction.resolve(world, new Vec3(.5, .8, .5), new Vec3(.5, .8, -8)).z > -2);
    }

    @Test void stairsBlockOnlyTheirOccupiedStepsIncludingDuringDiagonalCameraMotion() {
        var world = new BlockTestWorld(); var pos = new BlockPos(0, 0, -3);
        world.blocks.put(pos, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        assertFalse(CameraObstruction.shape(world.getBlockState(pos), world, pos).isEmpty());
        assertTrue(CameraObstruction.resolve(world, new Vec3(.75, .8, .5), new Vec3(.75, .8, -8)).z > -2);
        assertEquals(new Vec3(.2, .8, -8), CameraObstruction.resolve(world, new Vec3(.2, .8, .5), new Vec3(.2, .8, -8)));
        assertTrue(CameraObstruction.resolve(world, new Vec3(.75, .25, .5), new Vec3(.75, 1, -8)).z > -2);
    }

    @Test void nearCameraCornersAndVeryCloseWallsCannotClipThrough() {
        var world = new BlockTestWorld();
        world.blocks.put(new BlockPos(0, 0, -3), Blocks.STONE.defaultBlockState());
        assertTrue(CameraObstruction.resolve(world, new Vec3(1.05, .5, .5), new Vec3(1.05, .5, -8)).z > -2);
        var eye = new Vec3(.5, .5, -1.98);
        Vec3 close = CameraObstruction.resolve(world, eye, new Vec3(.5, .5, -8));
        assertTrue(close.z >= -2); assertTrue(close.distanceTo(eye) < .02);
        assertEquals(eye, CameraObstruction.resolve(world, eye, eye));
    }
}
