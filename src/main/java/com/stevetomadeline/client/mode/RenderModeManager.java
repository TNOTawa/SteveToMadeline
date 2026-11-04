package com.stevetomadeline.client.mode;

import com.stevetomadeline.client.windowx.WindowXCameraBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * R 键的三态循环：原版 → 纯 2D 平面 → 2.5D（侧视下俯 12°）→ 原版。
 *
 * <p>“原版”表示关闭 WindowX 摄像机窗口，回到 Minecraft 主视角；
 * 另外两个模式共用同一个摄像机窗口，只是姿态与渲染附加层不同。
 */
public final class RenderModeManager {
    public enum Mode {
        VANILLA("原版"),
        FLAT_2D("2D 平面"),
        ISOMETRIC("2.5D");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static Mode mode = Mode.VANILLA;

    private RenderModeManager() {
    }

    public static Mode mode() {
        return mode;
    }

    public static boolean isFlat2D() {
        return mode == Mode.FLAT_2D;
    }

    public static boolean isIsometric() {
        return mode == Mode.ISOMETRIC;
    }

    /** 退出世界时复位，避免重新进入后摄像机状态与模式脱节。 */
    public static void reset() {
        mode = Mode.VANILLA;
    }

    public static void cycle() {
        switch (mode) {
            case VANILLA -> switchTo(Mode.FLAT_2D);
            case FLAT_2D -> switchTo(Mode.ISOMETRIC);
            case ISOMETRIC -> switchTo(Mode.VANILLA);
        }
    }

    private static void switchTo(Mode next) {
        mode = next;
        if (next == Mode.VANILLA) {
            WindowXCameraBridge.stop();
        } else if (!WindowXCameraBridge.isActive()) {
            WindowXCameraBridge.start();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("SteveToMadeline: " + next.label()), true);
        }
    }
}
