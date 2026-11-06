package com.stevetomadeline.client.assets;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Monocle Packer metadata and RLE pages, adapted from desk-madeline CelesteAtlas.cs. */
public final class CelesteAtlas {
    public record Entry(int page, int x, int y, int width, int height, int offsetX, int offsetY, int frameWidth, int frameHeight) { }
    public record Index(List<String> pages, Map<String, Entry> entries) { }
    public record Image(int width, int height, int[] argb) { }
    private CelesteAtlas() { }
    private static int intLE(DataInputStream in) throws IOException { return Integer.reverseBytes(in.readInt()); }
    private static int shortLE(DataInputStream in) throws IOException { return Short.reverseBytes(in.readShort()); }
    private static String string(DataInputStream in) throws IOException {
        int length = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte(); length |= (b & 127) << shift;
            if ((b & 128) == 0) {
                if (length < 0 || length > 1_048_576) throw new IOException("Invalid atlas string length");
                byte[] bytes = in.readNBytes(length);
                if (bytes.length != length) throw new EOFException();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Invalid atlas string prefix");
    }
    public static Index readIndex(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(stream));
        intLE(in); string(in); intLE(in);
        int count = shortLE(in);
        if (count < 1 || count > 128) throw new IOException("Invalid atlas page count");
        List<String> pages = new ArrayList<>(); Map<String, Entry> entries = new LinkedHashMap<>();
        for (int page = 0; page < count; page++) {
            pages.add(string(in)); int sprites = shortLE(in);
            if (sprites < 0) throw new IOException("Invalid sprite count");
            for (int i = 0; i < sprites; i++) {
                String key = string(in).replace('\\', '/').toLowerCase(Locale.ROOT);
                Entry entry = new Entry(page, shortLE(in), shortLE(in), shortLE(in), shortLE(in),
                        -shortLE(in), -shortLE(in), shortLE(in), shortLE(in));
                if (entry.width < 0 || entry.height < 0 || entry.frameWidth < 1 || entry.frameHeight < 1
                        || entry.frameWidth > 2048 || entry.frameHeight > 2048) throw new IOException("Invalid sprite dimensions");
                entries.put(key, entry);
            }
        }
        return new Index(List.copyOf(pages), Map.copyOf(entries));
    }
    public static Image decode(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(stream));
        int width = intLE(in), height = intLE(in); boolean alpha = in.readUnsignedByte() == 1;
        if (width < 1 || height < 1 || (long) width * height > 16_777_216) throw new IOException("Invalid atlas page dimensions");
        int[] pixels = new int[width * height];
        for (int at = 0; at < pixels.length;) {
            int length = in.readUnsignedByte(), a = alpha ? in.readUnsignedByte() : 255;
            if (length == 0 || length > pixels.length - at) throw new IOException("Invalid atlas pixel run");
            int color = 0;
            if (a > 0) {
                int b = in.readUnsignedByte(), g = in.readUnsignedByte(), r = in.readUnsignedByte();
                // Packer stores premultiplied colors; Minecraft's blend expects straight alpha.
                r = Math.min(255, r * 255 / a); g = Math.min(255, g * 255 / a); b = Math.min(255, b * 255 / a);
                color = a << 24 | r << 16 | g << 8 | b;
            }
            Arrays.fill(pixels, at, at + length, color); at += length;
        }
        return new Image(width, height, pixels);
    }
    public static Image extract(Image page, Entry entry) throws IOException {
        if (entry.x < 0 || entry.y < 0 || entry.x + entry.width > page.width || entry.y + entry.height > page.height)
            throw new IOException("Sprite outside atlas page");
        int[] pixels = new int[entry.frameWidth * entry.frameHeight];
        for (int y = 0; y < entry.height; y++) for (int x = 0; x < entry.width; x++) {
            int dx = x + entry.offsetX, dy = y + entry.offsetY;
            if (dx >= 0 && dx < entry.frameWidth && dy >= 0 && dy < entry.frameHeight)
                pixels[dy * entry.frameWidth + dx] = page.argb[(y + entry.y) * page.width + x + entry.x];
        }
        return new Image(entry.frameWidth, entry.frameHeight, pixels);
    }
    public static Map<String, Image> decodePlayer(Index index, Path atlases) throws IOException {
        Map<String, Image> frames = new TreeMap<>();
        for (int page = 0; page < index.pages.size(); page++) {
            final int id = page;
            var wanted = index.entries.entrySet().stream().filter(e -> e.getValue().page == id && (
                    e.getKey().startsWith("characters/player/") || e.getKey().startsWith("particles/smoke")
                    || e.getKey().equals("particles/dashparticle") || e.getKey().startsWith("effects/slash/")
                    || e.getKey().equals("util/glove"))).toList();
            if (wanted.isEmpty()) continue;
            String name = index.pages.get(page).replace('\\', '/');
            Path data = atlases.resolve(name.endsWith(".data") ? name : name + ".data").normalize();
            if (!data.startsWith(atlases)) throw new IOException("Atlas page outside graphics directory");
            Image image;
            try (InputStream stream = Files.newInputStream(data)) { image = decode(stream); }
            for (var entry : wanted) frames.put(entry.getKey(), extract(image, entry.getValue()));
        }
        return frames;
    }
    public static Map<String, Image> loadPlayer(Path graphics) throws IOException {
        Path atlases = graphics.resolve("Atlases").toAbsolutePath().normalize();
        Index index;
        try (InputStream stream = Files.newInputStream(atlases.resolve("Gameplay.meta"))) { index = readIndex(stream); }
        Map<String, Image> frames = decodePlayer(index, atlases);
        if (frames.isEmpty()) throw new IOException("Player frames missing from Gameplay atlas");
        // Celeste draws dash particles as a plain rectangle, not an atlas sprite.
        frames.put("particles/dashparticle", new Image(1, 1, new int[]{0xFFFFFFFF}));
        for (var entry : List.copyOf(frames.entrySet())) {
            Image image = entry.getValue(); int[] mask = null;
            if (entry.getKey().startsWith("characters/player/")) {
                int[] silhouette = new int[image.argb.length];
                for (int i = 0; i < silhouette.length; i++) silhouette[i] = image.argb[i] & 0xFF000000 | 0xFFFFFF;
                frames.put(entry.getKey() + "#silhouette", new Image(image.width, image.height, silhouette));
            }
            for (int i = 0; i < image.argb.length; i++) {
                int color = image.argb[i], rgb = color & 0xFFFFFF;
                if ((color >>> 24) == 0 || (rgb != 0xAC3232 && rgb != 0x5A1A1A)) continue;
                if (mask == null) mask = new int[image.argb.length];
                int shade = rgb == 0xAC3232 ? 255 : 134;
                mask[i] = color & 0xFF000000 | shade << 16 | shade << 8 | shade;
            }
            if (mask != null) frames.put(entry.getKey() + "#hair", new Image(image.width, image.height, mask));
        }
        return frames;
    }
}
