package com.stevetomadeline.client;

import org.junit.jupiter.api.Test;
import java.util.Arrays;

import static com.stevetomadeline.client.TerrainClassifier.*;
import static org.junit.jupiter.api.Assertions.*;

class TerrainClassifierTest {
    private static final int SIDE = 16;

    private static byte[] ground() {
        byte[] cells = new byte[SIDE * SIDE * SIDE];
        Arrays.fill(cells, 0, SIDE * SIDE * 8, TERRAIN);
        return cells;
    }

    private static int at(int x, int y, int z) {
        return (y * SIDE + z) * SIDE + x;
    }

    @Test
    void thickContinuousGroundIsEligibleForCutting() {
        assertEquals(Result.SOLID_TERRAIN, classify(ground(), SIDE, SIDE, SIDE));
    }

    @Test
    void aProtectedBlockVetoesCuttingTheWholeMixedRegion() {
        byte[] cells = ground();
        cells[at(8, 8, 8)] = PROTECTED;
        assertEquals(Result.PROTECTED_BLOCK, classify(cells, SIDE, SIDE, SIDE));
    }

    @Test
    void cavesAndRoofsArePreservedEvenWhenMadeOfNaturalMaterials() {
        byte[] cave = ground();
        cave[at(8, 3, 8)] = EMPTY;
        assertEquals(Result.CAVITY_OR_OVERHANG, classify(cave, SIDE, SIDE, SIDE));
        byte[] roof = ground();
        roof[at(8, 12, 8)] = TERRAIN;
        assertEquals(Result.CAVITY_OR_OVERHANG, classify(roof, SIDE, SIDE, SIDE));
    }

    @Test
    void aThinStoneWallIsPreservedDespiteItsThickNaturalFoundation() {
        byte[] cells = ground();
        for (int x = 2; x < 14; x++) {
            for (int y = 8; y < 13; y++) cells[at(x, y, 8)] = TERRAIN;
        }
        assertEquals(Result.THIN_STRUCTURE, classify(cells, SIDE, SIDE, SIDE));
    }

    @Test
    void aSlopeIsNotMistakenForAThinWall() {
        byte[] cells = new byte[SIDE * SIDE * SIDE];
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                for (int y = 0; y < 6 + x / 4; y++) cells[at(x, y, z)] = TERRAIN;
            }
        }
        assertEquals(Result.SOLID_TERRAIN, classify(cells, SIDE, SIDE, SIDE));
    }

    @Test
    void floatingAndThinNaturalStructuresRemainUncut() {
        byte[] floating = new byte[SIDE * SIDE * SIDE];
        Arrays.fill(floating, SIDE * SIDE * 4, SIDE * SIDE * 8, TERRAIN);
        assertEquals(Result.CAVITY_OR_OVERHANG, classify(floating, SIDE, SIDE, SIDE));
        byte[] floor = new byte[SIDE * SIDE * SIDE];
        Arrays.fill(floor, 0, SIDE * SIDE, TERRAIN);
        assertEquals(Result.LOW_CONFIDENCE, classify(floor, SIDE, SIDE, SIDE));
    }

    @Test
    void unknownCellsAndInvalidDimensionsCannotBecomeTerrain() {
        byte[] unknown = ground();
        unknown[at(0, 0, 0)] = 3;
        assertEquals(Result.PROTECTED_BLOCK, classify(unknown, SIDE, SIDE, SIDE));
        assertThrows(IllegalArgumentException.class, () -> classify(unknown, SIDE - 1, SIDE, SIDE));
    }

    @Test
    void denseNaturalMaterialConstructionIsAnExplicitUnresolvableCase() {
        // Identical materials and occupancy cannot reveal whether this solid mass was built.
        byte[] solidStone = new byte[SIDE * SIDE * SIDE];
        Arrays.fill(solidStone, TERRAIN);
        assertEquals(Result.SOLID_TERRAIN, classify(solidStone, SIDE, SIDE, SIDE));
    }
}
