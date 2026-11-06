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

/**
 * Four binding slots per action. Slots accept keyboard, mouse or controller entries, so the same
 * conflict and reserve checks apply to every device. Controller defaults only fill empty slots
 * when a version-one file is upgraded, which never overwrites an explicit choice.
 */
public final class ControlBindings {
    public enum Action {
        LEFT("left", "key.keyboard.a", "gamepad:axis:leftx:negative", "gamepad:button:dpleft"),
        RIGHT("right", "key.keyboard.d", "gamepad:axis:leftx:positive", "gamepad:button:dpright"),
        UP("up", "key.keyboard.w", "gamepad:axis:lefty:negative", "gamepad:button:dpup"),
        DOWN("down", "key.keyboard.s", "gamepad:axis:lefty:positive", "gamepad:button:dpdown"),
        JUMP("jump", "key.keyboard.space", "key.keyboard.c", "gamepad:button:a"),
        DASH("dash", "key.keyboard.x", "gamepad:button:x"),
        GRAB("grab", "key.keyboard.left.shift", "key.keyboard.z", "key.keyboard.v", "gamepad:axis:triggerright:positive"),
        CROUCH_DASH("crouch_dash"),
        TOGGLE("toggle_celeste_mode", "key.keyboard.r", "gamepad:button:start"),
        TURN("turn_plane", "key.keyboard.tab", "gamepad:button:leftbumper");

        public final String id;
        public final String[] defaults;
        Action(String id, String... bindings) {
            this.id = id;
            defaults = new String[SLOTS];
            Arrays.fill(defaults, "");
            for (int i = 0; i < Math.min(bindings.length, SLOTS); i++) defaults[i] = bindings[i];
        }
        public Component label() { return Component.translatable("key.stevetomadeline." + id); }
    }

    public static final int SLOTS = 4;
    private static final int VERSION = 2;
    private static final EnumMap<Action, String[]> KEYS = new EnumMap<>(Action.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded, malformed;
    private static String assetPath = "", error = "";
    private static boolean showCharacter = true;
    private static PresentationOptions presentation = PresentationOptions.DEFAULT;
    private static GamepadOptions gamepad = GamepadOptions.DEFAULT;

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
    public static GamepadOptions gamepad() { load(); return gamepad; }
    public static void gamepad(GamepadOptions value) {
        load();
        if (!value.enabled()) PhysicsKeys.clearClicks();
        gamepad = value; save();
    }
    public static void appearance(String path, boolean enabled) { load(); assetPath = path.trim(); showCharacter = enabled; save(); }
    public static String get(Action action, int slot) { load(); return KEYS.get(action)[slot]; }
    public static Component name(String binding) {
        if (binding.isEmpty()) return Component.translatable("stevetomadeline.controls.unbound");
        if (GamepadBinding.isGamepad(binding)) return GamepadBinding.name(binding);
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
        if (samePhysical(key, com.stevetomadeline.client.SteveToMadelineKeys.OPEN_SETTINGS.getKey().getName())) return true;
        for (Action other : Action.values()) if (other != action && Arrays.stream(KEYS.get(other)).anyMatch(binding -> samePhysical(key, binding))) return true;
        return false;
    }
    private static String base(String binding) { return binding.split(":", 2)[0]; }
    /** Keyboard modifiers share one physical key; controller bindings are compared verbatim. */
    private static boolean samePhysical(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return false;
        if (GamepadBinding.isGamepad(left) || GamepadBinding.isGamepad(right)) return left.equals(right);
        return base(left).equals(base(right));
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
        if (binding.isEmpty()) return false;
        if (samePhysical(binding, com.stevetomadeline.client.SteveToMadelineKeys.OPEN_SETTINGS.getKey().getName())) return true;
        for (Action action : new Action[]{Action.TOGGLE, Action.TURN})
            for (String other : KEYS.get(action)) if (samePhysical(binding, other)) return true;
        return false;
    }
    public static boolean held(Action action, InputTimeline.Sample sample) {
        load();
        for (String binding : KEYS.get(action)) {
            if (binding.isEmpty() || reserved(binding)) continue;
            if (GamepadBinding.isGamepad(binding)) {
                if (sample.held().contains(binding)) return true;
            } else if (sample.held().contains(base(binding)) && modifiers(binding, sample.held())) return true;
        }
        return false;
    }
    public static boolean pressed(Action action, InputTimeline.Sample sample) {
        load();
        for (String binding : KEYS.get(action)) {
            if (action != Action.TOGGLE && action != Action.TURN && reserved(binding)) continue;
            if (binding.isEmpty()) continue;
            for (InputTimeline.Press press : sample.pressed())
                if (GamepadBinding.isGamepad(binding) ? press.key().equals(binding) : matches(binding, press)) return true;
        }
        return false;
    }
    public static boolean boundTo(Action action, InputConstants.Key key) {
        load();
        return Arrays.stream(KEYS.get(action))
                .anyMatch(binding -> !binding.isEmpty() && !GamepadBinding.isGamepad(binding) && base(binding).equals(key.getName()));
    }

    private static boolean valid(String binding) {
        if (binding.isEmpty()) return false;
        if (GamepadBinding.isGamepad(binding)) return GamepadBinding.valid(binding);
        InputConstants.getKey(base(binding));
        return true;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        for (Action action : Action.values()) KEYS.put(action, action.defaults.clone());
        if (!Files.exists(path())) { migrate(); return; }
        boolean upgrade = false;
        try (var reader = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 0;
            if (version < 1 || version > VERSION) throw new IOException("Unsupported controls version " + version);
            JsonObject bindings = root.getAsJsonObject("bindings");
            for (Action action : Action.values()) if (bindings.has(action.id)) {
                JsonArray values = bindings.getAsJsonArray(action.id);
                if (values.size() > SLOTS) throw new IOException("Too many binding slots");
                String[] keys = new String[SLOTS];
                Arrays.fill(keys, "");
                for (int i = 0; i < values.size(); i++) {
                    String name = values.get(i).getAsString();
                    if (!name.isEmpty() && !valid(name)) throw new IOException("Invalid binding " + name + " for " + action.id);
                    keys[i] = name;
                }
                KEYS.put(action, keys);
            }
            if (root.has("celestePath")) assetPath = root.get("celestePath").getAsString();
            if (root.has("showCharacter")) showCharacter = root.get("showCharacter").getAsBoolean();
            presentation = PresentationOptions.read(root);
            gamepad = GamepadOptions.read(root);
            if (version < VERSION) { upgrade = true; fillGamepadDefaults(KEYS); }
        } catch (Exception ex) {
            malformed = true; error = ex.getMessage(); SteveToMadeline.LOGGER.warn("Could not load controls", ex);
        }
        if (upgrade) save();
    }

    /** Version-one files had three slots and no controller entries; only empty slots gain them. */
    static void fillGamepadDefaults(Map<Action, String[]> keys) {
        for (Action action : Action.values()) {
            String[] bindings = keys.get(action);
            for (int slot = 0; slot < SLOTS; slot++) {
                String fallback = action.defaults[slot];
                if (bindings[slot].isEmpty() && GamepadBinding.isGamepad(fallback)) bindings[slot] = fallback;
            }
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
                        String[] values = new String[SLOTS];
                        Arrays.fill(values, "");
                        values[0] = key.equals("key.keyboard.unknown") ? "" : key;
                        KEYS.put(action, values);
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
            JsonObject root = new JsonObject(); root.addProperty("version", VERSION);
            root.addProperty("celestePath", assetPath); root.addProperty("showCharacter", showCharacter);
            presentation.write(root);
            gamepad.write(root);
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
