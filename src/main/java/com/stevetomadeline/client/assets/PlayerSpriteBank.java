package com.stevetomadeline.client.assets;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Player animations and per-frame hair metadata from the installed Sprites.xml. */
public final class PlayerSpriteBank {
    public record Animation(List<String> frames, float delay, String next) { }
    public record Hair(float x, float y, int bangs, boolean visible) { }
    public final Map<String, Animation> animations = new HashMap<>();
    public final Map<String, Animation> sweat = new HashMap<>();
    public final Map<String, Hair> hair = new HashMap<>();
    public float originX = 16, originY = 32;
    private static String normalized(String value) { return value.toLowerCase(Locale.ROOT); }
    public static PlayerSpriteBank load(Path file, Set<String> frames) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        Document xml;
        try (InputStream stream = Files.newInputStream(file)) { xml = factory.newDocumentBuilder().parse(stream); }
        Element player = (Element) xml.getDocumentElement().getElementsByTagName("player").item(0);
        if (player == null) throw new IOException("Player sprite definition missing");
        PlayerSpriteBank bank = new PlayerSpriteBank();
        readAnimations(player, frames, bank.animations, bank);
        Element sweat = (Element) xml.getDocumentElement().getElementsByTagName("player_sweat").item(0);
        if (sweat != null) readAnimations(sweat, frames, bank.sweat, null);
        return bank;
    }
    private static void readAnimations(Element player, Set<String> frames, Map<String, Animation> animations, PlayerSpriteBank bank) throws IOException {
        String root = normalized(player.getAttribute("path"));
        for (Node node = player.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element e)) continue;
            if (e.getTagName().equals("Origin") && bank != null) { bank.originX = Float.parseFloat(e.getAttribute("x")); bank.originY = Float.parseFloat(e.getAttribute("y")); }
            if (!e.getTagName().equals("Anim") && !e.getTagName().equals("Loop")) continue;
            String id = normalized(e.getAttribute("id")), path = root + normalized(e.getAttribute("path"));
            List<String> sequence = new ArrayList<>();
            if (!e.getAttribute("frames").isEmpty()) {
                for (int index : frameNumbers(e.getAttribute("frames"))) {
                    String frame = frameAt(frames, path, index);
                    if (frame == null) throw new IOException("Animation frame missing: " + path + index);
                    sequence.add(frame);
                }
            } else {
                for (int i = 0; i < 4096; i++) { String frame = frameAt(frames, path, i); if (frame == null) break; sequence.add(frame); }
            }
            if (sequence.isEmpty()) throw new IOException("Empty player animation: " + id);
            float delay = e.hasAttribute("delay") ? Float.parseFloat(e.getAttribute("delay")) : 0;
            if (!Float.isFinite(delay) || delay < 0) throw new IOException("Invalid animation delay");
            animations.put(id, new Animation(List.copyOf(sequence), delay,
                    e.getTagName().equals("Loop") ? id : normalized(e.getAttribute("goto"))));
        }
        if (bank == null) return;
        NodeList metadata = player.getElementsByTagName("Frames");
        for (int i = 0; i < metadata.getLength(); i++) {
            Element e = (Element) metadata.item(i);
            String[] entries = e.getAttribute("hair").split("\\|", -1);
            for (int j = 0; j < entries.length; j++) {
                String frame = frameAt(frames, root + normalized(e.getAttribute("path")), j);
                if (frame == null || entries[j].isBlank() || entries[j].equalsIgnoreCase("x")) continue;
                String[] fields = entries[j].split(":"); String[] xy = fields[0].split(",");
                bank.hair.put(frame, new Hair(Float.parseFloat(xy[0].trim()), Float.parseFloat(xy[1].trim()),
                        fields.length > 1 ? Integer.parseInt(fields[1].trim()) : 0, true));
            }
        }
    }
    public static List<Integer> frameNumbers(String csv) throws IOException {
        List<Integer> result = new ArrayList<>();
        for (String token : csv.split(",")) {
            String[] range = token.trim().split("-");
            if (range.length == 2) {
                int from = Integer.parseInt(range[0]), to = Integer.parseInt(range[1]);
                if (from < 0 || to < 0 || Math.abs(to - from) > 4096) throw new IOException("Invalid frame range");
                int direction = from <= to ? 1 : -1;
                for (int i = from; ; i += direction) { result.add(i); if (i == to) break; }
            } else {
                String[] repeated = token.trim().split("\\*");
                int value = Integer.parseInt(repeated[0]), count = repeated.length == 2 ? Integer.parseInt(repeated[1]) : 1;
                if (value < 0 || count < 0 || count > 4096) throw new IOException("Invalid frame repetition");
                for (int i = 0; i < count; i++) result.add(value);
            }
            if (result.size() > 4096) throw new IOException("Animation too long");
        }
        return result;
    }
    private static String frameAt(Set<String> frames, String path, int index) {
        if (index == 0 && frames.contains(path)) return path;
        String suffix = Integer.toString(index);
        for (int i = 0; i < 6; i++, suffix = "0" + suffix) if (frames.contains(path + suffix)) return path + suffix;
        return null;
    }
}
