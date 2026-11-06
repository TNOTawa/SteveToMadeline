package com.stevetomadeline.client.input;

import com.google.gson.JsonObject;
import java.util.Locale;

/** Optional fields keep version-one bindings compatible without rewriting their keys. */
public record PresentationOptions(GrabInput.Mode grabMode, boolean soundEnabled, int soundVolume, boolean effectsEnabled,
                                  boolean showStamina, boolean showDashes) {
    public static final PresentationOptions DEFAULT = new PresentationOptions(GrabInput.Mode.HOLD, true, 100, true);
    public PresentationOptions(GrabInput.Mode grabMode, boolean soundEnabled, int soundVolume, boolean effectsEnabled) {
        this(grabMode, soundEnabled, soundVolume, effectsEnabled, false, false);
    }
    public PresentationOptions { soundVolume = Math.clamp(soundVolume, 0, 100); }
    public static PresentationOptions read(JsonObject root) {
        GrabInput.Mode mode = GrabInput.Mode.HOLD;
        if (root.has("grabMode")) {
            try { mode = GrabInput.Mode.valueOf(root.get("grabMode").getAsString().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { }
        }
        return new PresentationOptions(mode, !root.has("soundEnabled") || root.get("soundEnabled").getAsBoolean(),
                root.has("soundVolume") ? root.get("soundVolume").getAsInt() : 100,
                !root.has("effectsEnabled") || root.get("effectsEnabled").getAsBoolean(),
                root.has("showStamina") && root.get("showStamina").getAsBoolean(),
                root.has("showDashes") && root.get("showDashes").getAsBoolean());
    }
    public void write(JsonObject root) {
        root.addProperty("grabMode", grabMode.name().toLowerCase(Locale.ROOT));
        root.addProperty("soundEnabled", soundEnabled); root.addProperty("soundVolume", soundVolume);
        root.addProperty("effectsEnabled", effectsEnabled);
        root.addProperty("showStamina", showStamina); root.addProperty("showDashes", showDashes);
    }
}
