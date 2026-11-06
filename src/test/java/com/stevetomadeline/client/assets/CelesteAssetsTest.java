package com.stevetomadeline.client.assets;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CelesteAssetsTest {
    @Test void referenceFramesAndSpriteMetadataLoadFromLocalGame() throws Exception {
        Path installation;
        try { installation = CelesteInstall.find(""); }
        catch (IOException ex) { org.junit.jupiter.api.Assumptions.abort("Local Celeste integration: " + ex.getMessage()); return; }
        var graphics = installation.resolve("Content/Graphics");
        var images = CelesteAtlas.loadPlayer(graphics);
        assertTrue(images.containsKey("particles/smoke0"));
        assertTrue(images.containsKey("particles/dashparticle"));
        assertTrue(images.containsKey("util/glove"));
        assertTrue(images.containsKey("effects/slash/00"));
        assertTrue(images.keySet().stream().anyMatch(key -> key.startsWith("characters/player/sweat/")));
        assertTrue(images.size() > 500);
        var bank = PlayerSpriteBank.load(graphics.resolve("Sprites.xml"), images.keySet());
        for (String id : new String[]{"idle", "climb", "climbloop", "still", "jump", "danger"}) {
            assertTrue(bank.sweat.containsKey(id), "sweat " + id);
            for (String frame : bank.sweat.get(id).frames()) assertNotNull(images.get(frame), frame);
        }
        int x = 0, y = 0, row = 0;
        for (var image : images.values()) {
            if (x + image.width() > 1024) { x = 0; y += row + 2; row = 0; }
            x += image.width() + 2; row = Math.max(row, image.height());
        }
        assertTrue(y + row <= 8192, "all effect and player frames fit in the dynamic atlas");
        for (String id : new String[]{"idle", "runfast", "dash", "jumpfast", "fallslow", "wallslide", "climbup", "duck", "swimidle", "swimup", "swimdown"}) {
            assertTrue(bank.animations.containsKey(id), id);
            for (String frame : bank.animations.get(id).frames()) assertNotNull(images.get(frame), frame);
        }
        assertEquals(16, bank.originX); assertEquals(32, bank.originY);
        assertTrue(bank.hair.get(bank.animations.get("idle").frames().getFirst()).visible());
        assertTrue(images.containsKey("characters/player/hair00"));
        assertTrue(images.containsKey("characters/player/bangs00"));
        assertTrue(images.keySet().stream().anyMatch(key -> key.endsWith("#hair")));
    }
    @Test void frameListUsesMonocleRangesAndRepetitions() throws Exception {
        assertEquals(List.of(0, 1, 2, 2, 2, 4, 3), PlayerSpriteBank.frameNumbers("0-2,2*2,4-3"));
    }
    @Test void truncatedRlePageFailsInsteadOfProducingTransparentArt() {
        byte[] incomplete = {1, 0, 0, 0, 1, 0, 0, 0, 1, 1};
        assertThrows(EOFException.class, () -> CelesteAtlas.decode(new ByteArrayInputStream(incomplete)));
    }
    @Test void croppedFrameOffsetsPutPixelsAtTheRightPosition() throws Exception {
        var page = new CelesteAtlas.Image(2, 2, new int[]{1, 2, 3, 4});
        var entry = new CelesteAtlas.Entry(0, 1, 0, 1, 2, 1, 1, 3, 4);
        var result = CelesteAtlas.extract(page, entry);
        assertEquals(2, result.argb()[4]); assertEquals(4, result.argb()[7]);
    }
}
