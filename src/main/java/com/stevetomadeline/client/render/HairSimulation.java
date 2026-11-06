package com.stevetomadeline.client.render;

/** desk-madeline PlayerHairSim, in sprite pixels with positive-down Y. */
public final class HairSimulation {
    public final float[] x = new float[5], y = new float[5];
    public int count = 4, color = 0xAC3232;
    private boolean started;
    private float wave, time, flash;
    private int lastDashes = 1;
    public void reset() { started = false; }
    public void step(float rootX, float rootY, int facing, int dashes) {
        float dt = 1f / 60;
        time += dt; wave += dt * 4; count = dashes > 1 ? 5 : 4;
        if (!started) { for (int i = 0; i < 5; i++) { x[i] = rootX - facing * 3; y[i] = rootY + 2; } started = true; }
        x[0] = rootX; y[0] = rootY;
        float stepX = dashes > 1 ? (float) Math.sin(time * 2) * 0.7f - facing * 3 : 0;
        float stepY = dashes > 1 ? (float) Math.sin(time) : 2;
        float inFacing = dashes > 1 ? 0 : 0.5f, sine = dashes > 1 ? 1 : 0, speed = dashes > 1 ? 90 : 64;
        float tx = x[0] - facing * inFacing * 2 + stepX, ty = y[0] + (float) Math.sin(wave) * sine + stepY;
        for (int i = 1; i < count; i++) {
            float dx = tx - x[i], dy = ty - y[i], length = (float) Math.sqrt(dx * dx + dy * dy);
            float approach = (1f - (float) i / count * 0.5f) * speed * dt;
            if (length <= approach) { x[i] = tx; y[i] = ty; }
            else { x[i] += dx / length * approach; y[i] += dy / length * approach; }
            dx = x[i] - x[i - 1]; dy = y[i] - y[i - 1]; length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length > 3) { x[i] = x[i - 1] + dx / length * 3; y[i] = y[i - 1] + dy / length * 3; }
            tx = x[i] - facing * inFacing + stepX; ty = y[i] + (float) Math.sin(wave + i * 0.8f) * sine + stepY;
        }
        if (dashes == 0) color = lerp(color, 0x44B7FF, 6f * dt);
        else if (lastDashes != dashes) { color = 0xFFFFFF; flash = 0.12f; }
        else if (flash > 0) { flash -= dt; color = 0xFFFFFF; }
        else color = dashes == 2 ? 0xFF6DEF : 0xAC3232;
        lastDashes = dashes;
    }
    private static int lerp(int from, int to, float amount) {
        int color = 0;
        for (int shift : new int[]{0, 8, 16}) {
            int a = from >> shift & 255, b = to >> shift & 255;
            color |= (int) (a + (b - a) * amount) << shift;
        }
        return color;
    }
}
