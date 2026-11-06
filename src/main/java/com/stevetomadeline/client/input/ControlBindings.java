package com.stevetomadeline.client.input;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.stevetomadeline.SteveToMadeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

/** Three keyboard/mouse slots per action. Original single-key settings migrate once. */
public final class ControlBindings {
    public enum Action {
        LEFT("left", "a", "left"), RIGHT("right", "d", "right"), UP("up", "w", "up"), DOWN("down", "s", "down"),
        JUMP("jump", "space", "c"), DASH("dash", "x"), GRAB("grab", "left.shift", "z", "v"),
        CROUCH_DASH("crouch_dash"), TOGGLE("toggle_celeste_mode", "r"), TURN("turn_plane", "tab");
        public final String id;
        private final String[] defaults;
        Action(String id, String... keys) {
            this.id = id; defaults = new String[]{"", "", ""};
            for (int i = 0; i < keys.length; i++) defaults[i] = "key.keyboard." + keys[i];
        }
        public Component label() { return Component.translatable("key.stevetomadeline." + id); }
    }
    private static final EnumMap<Action, String[]> KEYS = new EnumMap<>(Action.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded, malformed;
    private static String assetPath = "", error = "";
    private static boolean showCharacter = true;
    private static PresentationOptions presentation = PresentationOptions.DEFAULT;
    private ControlBindings() { }
    private static Path path() { return FMLPaths.CONFIGDIR.get().resolve("stevetomadeline-controls.json"); }
    public static void initialize() { load(); }
    public static String error() { load(); return error; }
    public static String assetPath() { load(); return assetPath; }
    public static boolean showCharacter() { load(); return showCharacter; }
    public static PresentationOptions presentation() { load(); return presentation; }
    public static void presentation(PresentationOptions value) {
        load();
        if (value.grabMode() != presentation.grabMode()) PhysicsKeys.resetGrab();
        presentation = value; save();
    }
    public static void appearance(String path, boolean enabled) { load(); assetPath = path.trim(); showCharacter = enabled; save(); }
    public static String get(Action action, int slot) { load(); return KEYS.get(action)[slot]; }
    public static Component name(String binding) {
        if (binding.isEmpty()) return Component.translatable("stevetomadeline.controls.unbound");
        String[] parts = binding.split(":", 2);
        Component key = InputConstants.getKey(parts[0]).getDisplayName();
        return parts.length == 1 ? key : Component.literal(parts[1] + " + ").append(key);
    }
    public static boolean set(Action action, int slot, String key) {
        load();
        String[] keys = KEYS.get(action);
        for (int i = 0; i < keys.length; i++) if (!key.isEmpty() && keys[i].equals(key)) keys[i] = "";
        keys[slot] = key;
        PhysicsKeys.clearClicks();
        return save();
    }
    public static void defaults() {
        load();
        for (Action action : Action.values()) KEYS.put(action, action.defaults.clone());
        PhysicsKeys.clearClicks(); save();
    }
    public static boolean conflict(Action action, int slot) {
        String key = get(action, slot);
        if (key.isEmpty()) return false;
        if (key.split(":", 2)[0].equals(com.stevetomadeline.client.SteveToMadelineKeys.OPEN_SETTINGS.getKey().getName())) return true;
        for (Action other : Action.values()) if (other != action && Arrays.asList(KEYS.get(other)).contains(key)) return true;
        return false;
    }
    private static boolean modifiers(String binding, Set<String> held) {
        if (!binding.contains(":")) return true;
        String modifier = binding.substring(binding.indexOf(':') + 1);
        return switch (modifier) {
            case "SHIFT" -> held.contains("key.keyboard.left.shift") || held.contains("key.keyboard.right.shift");
            case "CONTROL" -> held.contains("key.keyboard.left.control") || held.contains("key.keyboard.right.control");
            case "ALT" -> held.contains("key.keyboard.left.alt") || held.contains("key.keyboard.right.alt");
            default -> true;
        };
    }
    private static boolean matches(String binding, InputTimeline.Press press) {
        String[] parts = binding.split(":", 2);
        if (!parts[0].equals(press.key())) return false;
        if (parts.length == 1) return true;
        int mask = switch (parts[1]) { case "SHIFT" -> GLFW.GLFW_MOD_SHIFT; case "CONTROL" -> GLFW.GLFW_MOD_CONTROL; case "ALT" -> GLFW.GLFW_MOD_ALT; default -> 0; };
        return (press.modifiers() & mask) == mask;
    }
    private static boolean reserved(String binding) {
        String key = binding.split(":", 2)[0];
        if (key.equals(com.stevetomadeline.client.SteveToMadelineKeys.OPEN_SETTINGS.getKey().getName())) return true;
        for (Action action : new Action[]{Action.TOGGLE, Action.TURN})
            for (String other : KEYS.get(action)) if (other.split(":", 2)[0].equals(key)) return true;
        return false;
    }
    public static boolean held(Action action, InputTimeline.Sample sample) {
        load();
        for (String key : KEYS.get(action)) {
            if (!key.isEmpty() && !reserved(key) && sample.held().contains(key.split(":", 2)[0]) && modifiers(key, sample.held())) return true;
        }
        return false;
    }
    public static boolean pressed(Action action, InputTimeline.Sample sample) {
        load();
        for (String key : KEYS.get(action)) {
            if (action != Action.TOGGLE && action != Action.TURN && reserved(key)) continue;
            for (InputTimeline.Press press : sample.pressed()) if (matches(key, press)) return true;
        }
        return false;
    }
    public static boolean boundTo(Action action, InputConstants.Key key) {
        load();
        return Arrays.stream(KEYS.get(action)).anyMatch(binding -> binding.split(":", 2)[0].equals(key.getName()));
    }
    private static void load() {
        if (loaded) return;
        loaded = true;
        for (Action action : Action.values()) KEYS.put(action, action.defaults.clone());
        if (!Files.exists(path())) { migrate(); return; }
        try (var reader = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IOException("Unsupported controls version");
            JsonObject bindings = root.getAsJsonObject("bindings");
            for (Action action : Action.values()) if (bindings.has(action.id)) {
                JsonArray values = bindings.getAsJsonArray(action.id);
                if (values.size() > 3) throw new IOException("Too many key slots");
                String[] keys = new String[]{"", "", ""};
                for (int i = 0; i < values.size(); i++) {
                    String name = values.get(i).getAsString();
                    if (!name.isEmpty()) InputConstants.getKey(name.split(":", 2)[0]);
                    keys[i] = name;
                }
                KEYS.put(action, keys);
            }
            if (root.has("celestePath")) assetPath = root.get("celestePath").getAsString();
            if (root.has("showCharacter")) showCharacter = root.get("showCharacter").getAsBoolean();
            presentation = PresentationOptions.read(root);
        } catch (Exception ex) {
            malformed = true; error = ex.getMessage(); SteveToMadeline.LOGGER.warn("Could not load controls", ex);
        }
    }
    private static void migrate() {
        Path options = Minecraft.getInstance().gameDirectory.toPath().resolve("options.txt");
        if (!Files.isRegularFile(options)) return;
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                for (Action action : Action.values()) {
                    String prefix = "key_key.stevetomadeline." + action.id + ":";
                    if (line.startsWith(prefix)) {
                        String key = line.substring(prefix.length());
                        InputConstants.getKey(key.split(":", 2)[0]);
                        KEYS.put(action, new String[]{key.equals("key.keyboard.unknown") ? "" : key, "", ""});
                    }
                }
            }
            save();
        } catch (Exception ex) { error = ex.getMessage(); SteveToMadeline.LOGGER.warn("Could not migrate controls", ex); }
    }
    private static boolean save() {
        Path temporary = path().resolveSibling(path().getFileName() + ".tmp");
        try {
            Files.createDirectories(path().getParent());
            JsonObject root = new JsonObject(); root.addProperty("version", 1);
            root.addProperty("celestePath", assetPath); root.addProperty("showCharacter", showCharacter);
            presentation.write(root);
            JsonObject bindings = new JsonObject();
            for (Action action : Action.values()) bindings.add(action.id, GSON.toJsonTree(KEYS.get(action)));
            root.add("bindings", bindings);
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            if (malformed && Files.exists(path())) Files.copy(path(), path().resolveSibling(path().getFileName() + ".invalid-" + System.currentTimeMillis()));
            try { Files.move(temporary, path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path(), StandardCopyOption.REPLACE_EXISTING); }
            malformed = false; error = ""; return true;
        } catch (Exception ex) { error = ex.getMessage(); SteveToMadeline.LOGGER.warn("Could not save controls", ex); return false; }
        finally { try { Files.deleteIfExists(temporary); } catch (IOException ex) { SteveToMadeline.LOGGER.warn("Could not remove controls temporary file", ex); } }
    }
}
