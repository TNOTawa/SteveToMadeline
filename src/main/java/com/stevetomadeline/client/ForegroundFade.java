package com.stevetomadeline.client;

/** Reversible 250 ms opacity transition, independent of frame rate. */
public final class ForegroundFade {
    private double amount;
    public void update(boolean sliced, double seconds) {
        double change = Math.clamp(seconds, 0, .1) / .25;
        amount = Math.clamp(amount + (sliced ? change : -change), 0, 1);
    }
    public float opacity() { return (float) (1 - amount * amount * (3 - 2 * amount)); }
    public boolean active() { return amount > 0; }
    public void reset() { amount = 0; }
}
