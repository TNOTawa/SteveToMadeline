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
 * Four keyboard/mouse slots and four controller slots per action, stored and edited separately.
 * Both groups share the same conflict and reserve rules inside their own device family, so a
 * keyboard key and a controller button never shadow each other.
 */
public final class ControlBindings {
    public enum Action {
        LEFT("left",
                new String[]{"key.keyboard.a", "", "", ""},
                new String[]{"gamepad:axis:leftx:negative", "gamepad:button:dpleft", "", ""}),
        RIGHT("right",
                new String[]{"key.keyboard.d", "", "", ""},
                new String[]{"gamepad:axis:leftx:positive", "gamepad:button:dpright", "", ""}),
        UP("up",
                new String[]{"key.keyboard.w", "", "", ""},
                new String[]{"gamepad:axis:lefty:negative", "gamepad:button:dpup", "", ""}),
        DOWN("down",
                new String[]{"key.keyboard.s", "", "", ""},
                new String[]{"gamepad:axis:lefty:positive", "gamepad:button:dpdown", "", ""}),
        JUMP("jump",
                new String[]{"key.keyboard.space", "key.keyboard.c", "", ""},
                new String[]{"gamepad:button:a", "", "", ""}),
        DASH("dash",
                new String[]{"key.keyboard.x", "", "", ""},
                new String[]{"gamepad:button:x", "", "", ""}),
        GRAB("grab",
                new String[]{"key.keyboard.left.shift", "key.keyboard.z", "key.keyboard.v", ""},
                new String[]{"gamepad:axis:triggerright:positive", "", "", ""}),
        CROUCH_DASH("crouch_dash",
                new String[]{"", "", "", ""},
                new String[]{"", "", "", ""}),
        TOGGLE("toggle_celeste_mode",
                new String[]{"key.keyboard.r", "", "", ""},
                new String[]{"gamepad:button:start", "", "", ""}),
        TURN("turn_plane",
                new String[]{"key.keyboard.tab", "", "", ""},
                new String[]{"gamepad:button:leftbumper", "", "", ""});

        public final String id;
        public final String[] keyboard, controller;
        Action(String id, String[] keyboard, String[] controller) {
            this.id = id;
            this.keyboard = keyboard.clone();
            this.controller = controller.clone();
        }
        public Component label() { return Component.translatable("key.stevetomadeline." + id); }
    }

    public static final int SLOTS = 4;
    private static final int VERSION = 3;
    private static final EnumMap<Action, String[]> KEYS = new EnumMap<>(Action.class);
    private static final EnumMap<Action, String[]> PAD = new EnumMap<>(Action.class);
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
    private static EnumMap<Action, String[]> group(boolean controller) { return controller ? PAD : KEYS; }
    public static String get(Action action, boolean controller, int slot) { load(); return group(controller).get(action)[slot]; }
    public static Component name(String binding) {
        if (binding.isEmpty()) return Component.translatable("stevetomadeline.controls.unbound");
        if (GamepadBinding.isGamepad(binding)) return GamepadBinding.name(binding);
        String[] parts = binding.split(":", 2);
        Component key = InputConstants.getKey(parts[0]).getDisplayName();
        return parts.length == 1 ? key : Component.literal(parts[1] + " + ").append(key);
    }
    public static boolean set(Action action, boolean controller, int slot, String binding) {
        load();
        String[] values = group(controller).get(action);
        for (int i = 0; i < values.length; i++) if (!binding.isEmpty() && values[i].equals(binding)) values[i] = "";
        values[slot] = binding;
        PhysicsKeys.clearClicks();
        return save();
    }
    public static void defaults(boolean controller) {
        load();
        for (Action action : Action.values())
            group(controller).put(action, (controller ? action.controller : action.keyboard).clone());
        PhysicsKeys.clearClicks(); save();
    }
    public static boolean conflict(Action action, boolean controller, int slot) {
        String binding = get(action, controller, slot);
        if (binding.isEmpty()) return false;
        if (!controller && sameBinding(false, binding, com.stevetomadeline.client.SteveToMadelineKeys.OPEN_SETTINGS.getKey().getName())) return true;
        for (Action other : Action.values())
            if (other != action)
                for (String entry : group(controller).get(other))
                    if (sameBinding(controller, binding, entry)) return true;
        return false;
    }
    private static String base(String binding) { return binding.split(":", 2)[0]; }
    /** Keyboard modifiers share one physical key; controller bindings are compared verbatim. */
    private static boolean sameBinding(boolean controller, String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return false;
        return controller ? left.equals(right) : base(left).equals(base(right));
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
        boolean controller = GamepadBinding.isGamepad(binding);
        if (!controller && sameBinding(false, binding, com.stevetomadeline.client.SteveToMadelineKeys.OPEN_SETTINGS.getKey().getName())) return true;
        for (Action action : new Action[]{Action.TOGGLE, Action.TURN})
            for (String other : group(controller).get(action)) if (sameBinding(controller, binding, other)) return true;
        return false;
    }
    public static boolean held(Action action, InputTimeline.Sample sample) {
        load();
        return held(KEYS.get(action), sample) || held(PAD.get(action), sample);
    }
    private static boolean held(String[] bindings, InputTimeline.Sample sample) {
        for (String binding : bindings) {
            if (binding.isEmpty() || reserved(binding)) continue;
            if (GamepadBinding.isGamepad(binding)) {
                if (sample.held().contains(binding)) return true;
            } else if (sample.held().contains(base(binding)) && modifiers(binding, sample.held())) return true;
        }
        return false;
    }
    public static boolean pressed(Action action, InputTimeline.Sample sample) {
        load();
        return pressed(action, KEYS.get(action), sample) || pressed(action, PAD.get(action), sample);
    }
    private static boolean pressed(Action action, String[] bindings, InputTimeline.Sample sample) {
        for (String binding : bindings) {
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

    private static boolean validKeyboard(String binding) {
        if (binding.isEmpty() || GamepadBinding.isGamepad(binding)) return false;
        InputConstants.getKey(base(binding));
        return true;
    }
    private static boolean validController(String binding) {
        return GamepadBinding.isGamepad(binding) && GamepadBinding.valid(binding);
    }
    private static void requireKeyboard(String binding) throws IOException {
        try {
            if (!validKeyboard(binding)) throw new IOException("Invalid keyboard binding " + binding);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid keyboard binding " + binding, ex);
        }
    }
    private static void requireController(String binding) throws IOException {
        if (!validController(binding)) throw new IOException("Invalid controller binding " + binding);
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        for (Action action : Action.values()) {
            KEYS.put(action, action.keyboard.clone());
            PAD.put(action, action.controller.clone());
        }
        if (!Files.exists(path())) { migrate(); return; }
        boolean upgrade = false;
        try (var reader = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 0;
            if (version < 1 || version > VERSION) throw new IOException("Unsupported controls version " + version);
            JsonObject bindings = root.has("bindings") ? root.getAsJsonObject("bindings") : null;
            if (version == 2) {
                splitVersionTwo(bindings);
            } else {
                readGroup(bindings, KEYS, false);
                if (root.has("controllerBindings")) readGroup(root.getAsJsonObject("controllerBindings"), PAD, true);
            }
            if (root.has("celestePath")) assetPath = root.get("celestePath").getAsString();
            if (root.has("showCharacter")) showCharacter = root.get("showCharacter").getAsBoolean();
            presentation = PresentationOptions.read(root);
            gamepad = GamepadOptions.read(root);
            if (version < VERSION) upgrade = true;
        } catch (Exception ex) {
            malformed = true; error = ex.getMessage(); SteveToMadeline.LOGGER.warn("Could not load controls", ex);
        }
        if (upgrade) save();
    }

    private static void readGroup(JsonObject group, EnumMap<Action, String[]> target, boolean controller) throws IOException {
        if (group == null) return;
        for (Action action : Action.values()) {
            if (!group.has(action.id)) continue;
            JsonArray values = group.getAsJsonArray(action.id);
            if (values.size() > SLOTS) throw new IOException("Too many binding slots for " + action.id);
            String[] bindings = new String[SLOTS];
            Arrays.fill(bindings, "");
            for (int i = 0; i < values.size(); i++) {
                String name = values.get(i).getAsString();
                if (name.isEmpty()) continue;
                if (controller) requireController(name); else requireKeyboard(name);
                bindings[i] = name;
            }
            target.put(action, bindings);
        }
    }

    /** Version two stored keyboard and controller entries in one four-slot list. */
    private static void splitVersionTwo(JsonObject group) throws IOException {
        if (group == null) return;
        for (Action action : Action.values()) {
            if (!group.has(action.id)) continue;
            JsonArray values = group.getAsJsonArray(action.id);
            if (values.size() > SLOTS) throw new IOException("Too many binding slots for " + action.id);
            String[] mixed = new String[values.size()];
            for (int i = 0; i < values.size(); i++) mixed[i] = values.get(i).getAsString();
            String[] keyboard = new String[SLOTS], controller = new String[SLOTS];
            Arrays.fill(keyboard, "");
            Arrays.fill(controller, "");
            splitMixed(mixed, keyboard, controller);
            KEYS.put(action, keyboard);
            PAD.put(action, controller);
        }
    }

    static void splitMixed(String[] mixed, String[] keyboard, String[] controller) throws IOException {
        int keys = 0, pads = 0;
        for (String binding : mixed) {
            if (binding.isEmpty()) continue;
            if (GamepadBinding.isGamepad(binding)) {
                requireController(binding);
                if (pads >= controller.length) throw new IOException("Too many controller slots");
                controller[pads++] = binding;
            } else {
                requireKeyboard(binding);
                if (keys >= keyboard.length) throw new IOException("Too many keyboard slots");
                keyboard[keys++] = binding;
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
            JsonObject bindings = new JsonObject(), controller = new JsonObject();
            for (Action action : Action.values()) {
                bindings.add(action.id, GSON.toJsonTree(KEYS.get(action)));
                controller.add(action.id, GSON.toJsonTree(PAD.get(action)));
            }
            root.add("bindings", bindings);
            root.add("controllerBindings", controller);
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            if (malformed && Files.exists(path())) Files.copy(path(), path().resolveSibling(path().getFileName() + ".invalid-" + System.currentTimeMillis()));
            try { Files.move(temporary, path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path(), StandardCopyOption.REPLACE_EXISTING); }
            malformed = false; error = ""; return true;
        } catch (Exception ex) { error = ex.getMessage(); SteveToMadeline.LOGGER.warn("Could not save controls", ex); return false; }
        finally { try { Files.deleteIfExists(temporary); } catch (IOException ex) { SteveToMadeline.LOGGER.warn("Could not remove controls temporary file", ex); } }
    }
}
