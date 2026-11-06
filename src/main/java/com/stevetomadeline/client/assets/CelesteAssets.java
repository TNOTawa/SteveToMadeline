package com.stevetomadeline.client.assets;

import com.mojang.blaze3d.platform.NativeImage;
import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.client.input.ControlBindings;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Reads only the local game. CPU decoding is asynchronous; GPU ownership stays on the client thread. */
public final class CelesteAssets {
    public record Frame(int x, int y, int width, int height) { }
    public record Loaded(Map<String, Frame> frames, PlayerSpriteBank bank, int width, int height, String source) { }
    private record Packed(Loaded loaded, int[] pixels) { }
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SteveToMadeline.MODID, "celeste_player");
    private static Loaded current;
    private static boolean started;
    private static int generation;
    private static Component status = Component.translatable("stevetomadeline.assets.waiting");
    private CelesteAssets() { }
    public static Component status() { return status; }
    public static Loaded current() { ensure(); return current; }
    public static void ensure() {
        if (started) return;
        started = true;
        install(placeholder());
        reload();
    }
    public static void reload() {
        if (!started) { ensure(); return; }
        int request = ++generation;
        String configured = ControlBindings.assetPath();
        status = Component.translatable("stevetomadeline.assets.loading");
        CompletableFuture.supplyAsync(() -> {
            try {
                var root = CelesteInstall.find(configured);
                var graphics = root.resolve("Content/Graphics");
                var images = CelesteAtlas.loadPlayer(graphics);
                var bank = PlayerSpriteBank.load(graphics.resolve("Sprites.xml"), images.keySet());
                return pack(images, bank, root.toString());
            } catch (Exception ex) { throw new java.util.concurrent.CompletionException(ex); }
        }).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            if (request != generation) return;
            if (error != null) {
                status = Component.translatable("stevetomadeline.assets.failed", error.getCause() == null ? error.getMessage() : error.getCause().getMessage());
                SteveToMadeline.LOGGER.warn("Celeste assets could not be loaded", error);
                return;
            }
            install(result);
            status = Component.translatable("stevetomadeline.assets.ready", result.loaded.frames.keySet().stream().filter(key -> !key.contains("#")).count());
        }));
    }
    private static Packed pack(Map<String, CelesteAtlas.Image> images, PlayerSpriteBank bank, String source) {
        int width = 1024, x = 0, y = 0, row = 0;
        Map<String, Frame> frames = new LinkedHashMap<>();
        for (var entry : images.entrySet()) {
            var image = entry.getValue();
            if (image.width() > width) throw new IllegalArgumentException("Player frame too wide");
            if (x + image.width() > width) { x = 0; y += row + 2; row = 0; }
            frames.put(entry.getKey(), new Frame(x, y, image.width(), image.height()));
            x += image.width() + 2; row = Math.max(row, image.height());
        }
        int height = 1;
        while (height < y + row) height *= 2;
        if (height > 8192) throw new IllegalArgumentException("Player atlas too large");
        int[] pixels = new int[width * height];
        for (var entry : frames.entrySet()) {
            var image = images.get(entry.getKey()); Frame frame = entry.getValue();
            for (int dy = 0; dy < frame.height; dy++) {
                System.arraycopy(image.argb(), dy * frame.width, pixels, (dy + frame.y) * width + frame.x, frame.width);
            }
        }
        return new Packed(new Loaded(Map.copyOf(frames), bank, width, height, source), pixels);
    }
    private static void install(Packed packed) {
        NativeImage image = new NativeImage(packed.loaded.width, packed.loaded.height, true);
        for (int y = 0; y < packed.loaded.height; y++) for (int x = 0; x < packed.loaded.width; x++) {
            int color = packed.pixels[y * packed.loaded.width + x];
            image.setPixelRGBA(x, y, color & 0xFF00FF00 | (color & 255) << 16 | color >>> 16 & 255);
        }
        DynamicTexture texture = new DynamicTexture(image); texture.setFilter(false, false);
        Minecraft.getInstance().getTextureManager().release(TEXTURE);
        Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
        current = packed.loaded;
    }
    private static Packed placeholder() {
        // Original neutral placeholder, never a bundled Celeste sprite.
        String[] rows = {"    HHHH    ", "   HHHHHH   ", "   HWWBWH   ", "   HWWWW    ", "    TTTT    ",
                "   TTTTTT   ", "  WTTTTTTW  ", "   TTTTTT   ", "    BBBB    ", "    B  B    ", "   BB  BB   "};
        int[] pixels = new int[12 * 16];
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) {
            pixels[(y + 5) * 12 + x] = switch (rows[y].charAt(x)) {
                case 'H' -> 0xFF303038; case 'T' -> 0xFF4CBBAA; case 'W' -> 0xFFE5E8EF; case 'B' -> 0xFF41414C; default -> 0;
            };
        }
        PlayerSpriteBank bank = new PlayerSpriteBank(); bank.originX = 6; bank.originY = 16;
        bank.animations.put("idle", new PlayerSpriteBank.Animation(List.of("placeholder"), 0, "idle"));
        return pack(Map.of("placeholder", new CelesteAtlas.Image(12, 16, pixels)), bank, "");
    }
}
