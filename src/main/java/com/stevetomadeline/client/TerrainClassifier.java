package com.stevetomadeline.client;

/** Conservative height-field recognition, not a claim about who placed a block. */
public final class TerrainClassifier {
    public static final byte EMPTY = 0;
    public static final byte TERRAIN = 1;
    public static final byte PROTECTED = 2;
    private static final int MIN_VOLUME = 64;
    private static final double MIN_BASE_COVERAGE = 0.25;
    private static final double MIN_INTERIOR_FRACTION = 0.35;

    public enum Result {
        SOLID_TERRAIN, PROTECTED_BLOCK, EMPTY_OR_SMALL, CAVITY_OR_OVERHANG,
        THIN_STRUCTURE, LOW_CONFIDENCE, UNLOADED, BUDGET_EXCEEDED
    }

    private TerrainClassifier() {
    }

    /** Layout: (y * depth + z) * width + x; bottom of the sampled volume is y=0. */
    public static Result classify(byte[] cells, int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0 || (long) width * height * depth != cells.length) {
            throw new IllegalArgumentException("Invalid terrain sample dimensions");
        }
        int volume = 0;
        for (byte cell : cells) {
            if (cell != EMPTY && cell != TERRAIN) return Result.PROTECTED_BLOCK;
            if (cell == TERRAIN) volume++;
        }
        if (volume < MIN_VOLUME) return Result.EMPTY_OR_SMALL;

        int layer = width * depth;
        int[] heights = new int[layer];
        int base = 0;
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int column = z * width + x;
                if (cells[column] == TERRAIN) base++;
                boolean sawAir = false;
                for (int y = 0; y < height; y++) {
                    if (cells[y * layer + column] == EMPTY) {
                        sawAir = true;
                    } else {
                        if (sawAir) return Result.CAVITY_OR_OVERHANG;
                        heights[column] = y + 1;
                    }
                }
            }
        }
        if (base < layer * MIN_BASE_COVERAGE) return Result.LOW_CONFIDENCE;

        // Detect narrow walls/towers even when a thick natural foundation dominates the volume.
        for (int z = 2; z < depth - 2; z++) {
            for (int x = 2; x < width - 2; x++) {
                int column = z * width + x;
                int top = heights[column];
                if (top - Math.max(heights[column - 2], heights[column + 2]) >= 3
                        || top - Math.max(heights[column - 2 * width], heights[column + 2 * width]) >= 3) {
                    return Result.THIN_STRUCTURE;
                }
            }
        }
        int interior = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int z = 1; z < depth - 1; z++) {
                for (int x = 1; x < width - 1; x++) {
                    int i = y * layer + z * width + x;
                    if (cells[i] == TERRAIN && cells[i - 1] == TERRAIN && cells[i + 1] == TERRAIN
                            && cells[i - width] == TERRAIN && cells[i + width] == TERRAIN
                            && cells[i - layer] == TERRAIN && cells[i + layer] == TERRAIN) interior++;
                }
            }
        }
        return interior >= volume * MIN_INTERIOR_FRACTION ? Result.SOLID_TERRAIN : Result.LOW_CONFIDENCE;
    }
}
