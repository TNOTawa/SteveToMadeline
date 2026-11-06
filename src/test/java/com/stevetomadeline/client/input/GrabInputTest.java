package com.stevetomadeline.client.input;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrabInputTest {
    @Test void holdInvertAndToggleReadBeforeThePressChangesTheLatch() {
        var grab = new GrabInput();
        assertEquals(GrabInput.Mode.HOLD, grab.mode());
        assertTrue(grab.sample(true, true)); assertFalse(grab.sample(false, false));
        grab.setMode(GrabInput.Mode.INVERT);
        assertTrue(grab.sample(false, false)); assertFalse(grab.sample(true, true));
        grab.setMode(GrabInput.Mode.TOGGLE);
        assertFalse(grab.sample(true, true));
        assertTrue(grab.sample(false, false));
        assertTrue(grab.sample(true, true));
        assertFalse(grab.sample(false, false));
        grab.sample(true, true); grab.setMode(GrabInput.Mode.TOGGLE);
        assertFalse(grab.sample(false, false));
        grab.sample(true, true); grab.reset(); assertFalse(grab.sample(false, false));
    }
    @Test void oldBindingFilesGainDefaultsWithoutLosingOtherFields() {
        var old = new JsonObject(); old.addProperty("version", 1);
        assertEquals(PresentationOptions.DEFAULT, PresentationOptions.read(old));
        var options = new PresentationOptions(GrabInput.Mode.TOGGLE, false, 300, false);
        assertEquals(100, options.soundVolume());
        options.write(old);
        assertEquals(options, PresentationOptions.read(old));
        assertEquals(1, old.get("version").getAsInt());
        old.addProperty("grabMode", "unknown");
        assertEquals(GrabInput.Mode.HOLD, PresentationOptions.read(old).grabMode());
    }
}
