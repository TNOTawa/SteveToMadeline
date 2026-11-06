package com.stevetomadeline.core.plane;

/** Camera normals in Minecraft's clockwise yaw order. */
public enum Heading {
    SOUTH(0, 0, 1), WEST(90, -1, 0), NORTH(180, 0, -1), EAST(270, 1, 0);

    private final float yaw;
    private final int normalX;
    private final int normalZ;

    Heading(float yaw, int normalX, int normalZ) {
        this.yaw = yaw;
        this.normalX = normalX;
        this.normalZ = normalZ;
    }

    public float yaw() { return yaw; }
    public int normalX() { return normalX; }
    public int normalZ() { return normalZ; }
    public int rightX() { return -normalZ; }
    public int rightZ() { return normalX; }
    public boolean locksZ() { return normalZ != 0; }
    public Heading clockwise() { return values()[(ordinal() + 1) % 4]; }
    public Heading counterClockwise() { return values()[(ordinal() + 3) % 4]; }

    public static Heading fromYaw(float yaw) {
        return values()[Math.floorMod(Math.round(yaw / 90.0f), 4)];
    }
}
