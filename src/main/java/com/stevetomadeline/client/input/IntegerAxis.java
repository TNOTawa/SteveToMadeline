package com.stevetomadeline.client.input;

/** desk-madeline IntegerAxis.TakeNewer: opposite keys invert once until either is released. */
public final class IntegerAxis {
    private int value;
    private boolean turned;

    public int update(boolean negative, boolean positive) {
        if (positive && negative) {
            if (!turned) { value = -value; turned = true; }
        } else {
            turned = false;
            value = positive ? 1 : negative ? -1 : 0;
        }
        return value;
    }
}
