package com.stevetomadeline.core.physics;

import com.stevetomadeline.BlockTestWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidGeometryTest {
    @Test void waterLavaAndBubbleColumnsAllUseTheSameGeometricContact() {
        var world = new BlockTestWorld();
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.WATER, Blocks.LAVA, Blocks.BUBBLE_COLUMN}) {
            world.blocks.put(BlockPos.ZERO, block.defaultBlockState());
            assertTrue(MinecraftPhysicsWorld.containsFluid(world, new AABB(.1, .2, .1, .8, .8, .8), pos -> true));
            assertFalse(MinecraftPhysicsWorld.containsFluid(world, new AABB(.1, 1.01, .1, .8, 1.7, .8), pos -> true));
        }
        world.blocks.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        assertFalse(MinecraftPhysicsWorld.containsFluid(world, new AABB(.1, .2, .1, .8, .8, .8), pos -> true));
    }

    @Test void flowingSurfaceHeightAndExactBoundaryAreRespected() {
        var world = new BlockTestWorld();
        world.blocks.put(BlockPos.ZERO, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));
        double height = world.getFluidState(BlockPos.ZERO).getHeight(world, BlockPos.ZERO);
        assertTrue(height > 0 && height < .5);
        assertTrue(MinecraftPhysicsWorld.containsFluid(world, new AABB(.1, height - .01, .1, .8, 1, .8), pos -> true));
        assertFalse(MinecraftPhysicsWorld.containsFluid(world, new AABB(.1, height, .1, .8, 1, .8), pos -> true));
        assertFalse(MinecraftPhysicsWorld.containsFluid(world, new AABB(1, 0, .1, 1.5, .7, .8), pos -> true));
        assertFalse(MinecraftPhysicsWorld.containsFluid(world, new AABB(.1, 0, .1, .8, .7, .8), pos -> false));
    }
}
