package com.stevetomadeline.client;

import com.mojang.logging.LogUtils;
import com.stevetomadeline.Config;
import com.stevetomadeline.SteveToMadeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/** One regional clipping decision. Ambiguity protects the whole foreground, including mixed regions. */
public final class TerrainClipping {
    private static final int MAX_CELLS = 98_304;
    private static final int REFRESH_TICKS = 5;
    private static final int PROTECTION_TICKS = 20;
    private static final TagKey<Block> TERRAIN = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SteveToMadeline.MODID, "terrain_candidates"));
    private static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SteveToMadeline.MODID, "protected_foreground"));

    private record Region(int minHorizontal, int maxHorizontal, int minY, int maxY,
                          int minAxis, int maxAxis, boolean zPlane) {
    }

    private static ClientLevel sampledLevel;
    private static Region sampledRegion;
    private static long sampledTick = Long.MIN_VALUE;
    private static long protectUntil = Long.MIN_VALUE;
    private static boolean cut;
    private static TerrainClassifier.Result lastResult;

    private TerrainClipping() {
    }

    public static void beginFrame(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!ParallaxRenderer.usesDepthPerspective() || ClientPlaneController.turning(partialTick) || mc.player == null || mc.level == null
                || Config.terrainClippingMode() != Config.TerrainClippingMode.ADAPTIVE) {
            reset();
            return;
        }
        if (sampledLevel != mc.level) {
            reset();
            sampledLevel = mc.level;
        }
        Vec3 eye = mc.player.getEyePosition(partialTick);
        int direction = Math.floorMod(Math.round(CameraController.yaw(mc.player) / 90.0f), 4);
        boolean zPlane = direction % 2 == 0;
        int sign = direction == 0 || direction == 3 ? 1 : -1;
        double horizontal = zPlane ? eye.x : eye.z;
        double axis = zPlane ? eye.z : eye.x;
        double halfHeight = Config.ORTHO_HALF_HEIGHT.get();
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double start = axis - sign * (CameraController.distance() - 0.05);
        double end = axis - sign * Config.SLICE_HALF_THICKNESS.get();
        Region region = new Region(Mth.floor(horizontal - halfHeight * aspect) - 1,
                Mth.floor(horizontal + halfHeight * aspect) + 1,
                Math.max(mc.level.getMinBuildHeight(), Mth.floor(eye.y - halfHeight) - 1),
                Math.min(mc.level.getMaxBuildHeight() - 1, Mth.floor(eye.y + halfHeight) + 1),
                Mth.floor(Math.min(start, end)), Mth.floor(Math.max(start, end)), zPlane);
        long tick = mc.level.getGameTime();
        if (region.equals(sampledRegion) && tick >= sampledTick && tick - sampledTick < REFRESH_TICKS) return;

        TerrainClassifier.Result result = sample(mc.level, region);
        sampledRegion = region;
        sampledTick = tick;
        if (result != TerrainClassifier.Result.SOLID_TERRAIN) protectUntil = tick + PROTECTION_TICKS;
        cut = result == TerrainClassifier.Result.SOLID_TERRAIN && tick >= protectUntil;
        if (result != lastResult) {
            LogUtils.getLogger().debug("[STM] foreground classification: {}, cut={}", result, cut);
            lastResult = result;
        }
    }

    private static TerrainClassifier.Result sample(ClientLevel level, Region region) {
        int width = region.maxHorizontal - region.minHorizontal + 1;
        int height = region.maxY - region.minY + 1;
        int depth = region.maxAxis - region.minAxis + 1;
        long volume = (long) width * height * depth;
        if (width <= 0 || height <= 0 || depth <= 0 || volume > MAX_CELLS) {
            return TerrainClassifier.Result.BUDGET_EXCEEDED;
        }
        int minX = region.zPlane ? region.minHorizontal : region.minAxis;
        int maxX = region.zPlane ? region.maxHorizontal : region.maxAxis;
        int minZ = region.zPlane ? region.minAxis : region.minHorizontal;
        int maxZ = region.zPlane ? region.maxAxis : region.maxHorizontal;
        int minChunkX = minX >> 4;
        int minChunkZ = minZ >> 4;
        LevelChunk[][] chunks = new LevelChunk[(maxX >> 4) - minChunkX + 1][(maxZ >> 4) - minChunkZ + 1];
        for (int x = 0; x < chunks.length; x++) {
            for (int z = 0; z < chunks[x].length; z++) {
                chunks[x][z] = level.getChunkSource().getChunk(minChunkX + x, minChunkZ + z, ChunkStatus.FULL, false);
                if (chunks[x][z] == null) return TerrainClassifier.Result.UNLOADED;
            }
        }
        byte[] cells = new byte[(int) volume];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    int worldX = region.zPlane ? region.minHorizontal + x : region.minAxis + z;
                    int worldZ = region.zPlane ? region.minAxis + z : region.minHorizontal + x;
                    pos.set(worldX, region.minY + y, worldZ);
                    BlockState state = chunks[(worldX >> 4) - minChunkX][(worldZ >> 4) - minChunkZ].getBlockState(pos);
                    if (state.is(PROTECTED) || state.hasBlockEntity()) return TerrainClassifier.Result.PROTECTED_BLOCK;
                    if (state.isAir()) continue;
                    if (state.is(TERRAIN) && state.isSolidRender(level, pos)) {
                        cells[(y * depth + z) * width + x] = TerrainClassifier.TERRAIN;
                    } else if (!state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                        return TerrainClassifier.Result.PROTECTED_BLOCK;
                    }
                }
            }
        }
        return TerrainClassifier.classify(cells, width, height, depth);
    }

    public static boolean drawSlice() {
        if (ClientPlaneController.turning(0)) return false;
        if (!ParallaxRenderer.usesDepthPerspective()) return true;
        return switch (Config.terrainClippingMode()) {
            case SLICE -> true;
            case NONE -> false;
            case ADAPTIVE -> cut;
        };
    }

    public static boolean drawSliceBlock(BlockState state) {
        return !ParallaxRenderer.usesDepthPerspective() || Config.terrainClippingMode() == Config.TerrainClippingMode.SLICE
                || (state.is(TERRAIN) && !state.is(PROTECTED) && !state.hasBlockEntity());
    }

    public static float nearPlane(double distance, double slice) {
        return drawSlice() ? (float) Math.max(0.05, distance - slice) : 0.05f;
    }

    public static void reset() {
        sampledLevel = null;
        sampledRegion = null;
        sampledTick = Long.MIN_VALUE;
        protectUntil = Long.MIN_VALUE;
        cut = false;
        lastResult = null;
    }
}
