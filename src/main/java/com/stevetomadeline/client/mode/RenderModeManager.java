package com.stevetomadeline.client.mode;

import com.stevetomadeline.Config;
import com.stevetomadeline.client.ClientPlaneController;

/** Rendering follows the authoritative plane state; R always enters or exits the mode. */
public final class RenderModeManager {
    public enum Mode { VANILLA, FLAT_2D, ISOMETRIC }

    private RenderModeManager() {
    }

    public static Mode mode() {
        if (ClientPlaneController.plane() == null) return Mode.VANILLA;
        return Config.isometricEnabled() ? Mode.ISOMETRIC : Mode.FLAT_2D;
    }

    public static boolean isCameraMode() { return mode() != Mode.VANILLA; }
    public static boolean isFlat2D() { return mode() == Mode.FLAT_2D; }
    public static boolean isIsometric() { return mode() == Mode.ISOMETRIC; }
}
