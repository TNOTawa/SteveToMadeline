package com.stevetomadeline.core.plane;

public enum EntryOrientation {
    FACING_RIGHT, FACING_LEFT, CAMERA_FORWARD, SOUTH, WEST, NORTH, EAST;

    public Heading select(float playerYaw) {
        Heading facing = Heading.fromYaw(playerYaw);
        return switch (this) {
            case FACING_RIGHT -> facing.counterClockwise();
            case FACING_LEFT -> facing.clockwise();
            case CAMERA_FORWARD -> facing;
            case SOUTH -> Heading.SOUTH;
            case WEST -> Heading.WEST;
            case NORTH -> Heading.NORTH;
            case EAST -> Heading.EAST;
        };
    }
}
