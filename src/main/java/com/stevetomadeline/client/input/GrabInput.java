package com.stevetomadeline.client.input;

/** Celeste Input.GrabCheck reads the latch before Input.UpdateGrab flips it. */
public final class GrabInput {
    public enum Mode { HOLD, INVERT, TOGGLE }
    private Mode mode = Mode.HOLD;
    private boolean toggled;
    public boolean sample(boolean held, boolean pressed) {
        boolean result = switch (mode) {
            case HOLD -> held;
            case INVERT -> !held;
            case TOGGLE -> toggled;
        };
        if (mode == Mode.TOGGLE && pressed) toggled = !toggled;
        return result;
    }
    public void setMode(Mode value) { mode = value; reset(); }
    public Mode mode() { return mode; }
    public boolean toggled() { return toggled; }
    public void reset() { toggled = false; }
}
