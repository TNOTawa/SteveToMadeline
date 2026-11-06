package com.stevetomadeline.client.audio;

import com.stevetomadeline.client.assets.CelesteInstall;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.*;

class FmodAudioTest {
    @Test void installedOriginalBanksExposePlayerEventsWithoutPlayingAudio() throws IOException {
        java.nio.file.Path installation;
        try { installation = CelesteInstall.find(""); }
        catch (IOException ex) { Assumptions.abort(ex.getMessage()); return; }
        if (!System.getProperty("os.name").startsWith("Windows")) Assumptions.abort("FMOD Windows runtime");
        try (var audio = FmodAudio.open(installation, true)) {
            for (String name : new String[]{"jump", "jump_super", "jump_superslide", "jump_superwall",
                    "jump_wall_right", "jump_wall_left", "jump_climb_right", "jump_climb_left", "dash_red_right",
                    "dash_red_left", "dash_pink_right", "dash_pink_left", "grab", "grab_letgo", "climb_ledge",
                    "landing", "duck", "stand", "footstep", "handhold", "wallslide", "idle_scratch", "idle_sneeze"})
                assertTrue(audio.hasEvent("event:/char/madeline/" + name), name);
            assertTrue(audio.play("event:/char/madeline/wallslide", "slide", "surface_index", 8));
            audio.stop("slide");
            assertTrue(audio.play("event:/char/madeline/idle_scratch", "idle", null, 0));
            assertTrue(audio.play("event:/char/madeline/idle_sneeze", "idle", null, 0));
            audio.stop("idle");
            assertTrue(audio.play("event:/char/madeline/jump", null, null, 0));
            audio.update();
        }
    }
}
