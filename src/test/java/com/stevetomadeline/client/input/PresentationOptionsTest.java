package com.stevetomadeline.client.input;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresentationOptionsTest {
    @Test void newAndExistingConfigurationsHideBothIndicatorsByDefault() {
        assertFalse(PresentationOptions.DEFAULT.showStamina());
        assertFalse(PresentationOptions.DEFAULT.showDashes());
        var root = new JsonObject(); root.addProperty("soundEnabled", false); root.addProperty("grabMode", "toggle");
        var options = PresentationOptions.read(root);
        assertFalse(options.showStamina()); assertFalse(options.showDashes());
        assertFalse(options.soundEnabled()); assertEquals(GrabInput.Mode.TOGGLE, options.grabMode());
    }
    @Test void indicatorsPersistIndependentlyWithoutChangingExistingPreferences() {
        for (boolean stamina : new boolean[]{false, true}) for (boolean dashes : new boolean[]{false, true}) {
            var options = new PresentationOptions(GrabInput.Mode.INVERT, false, 43, false, stamina, dashes);
            var root = new JsonObject(); root.addProperty("version", 3);
            options.write(root);
            assertEquals(options, PresentationOptions.read(root));
            assertEquals(3, root.get("version").getAsInt());
        }
    }
}
