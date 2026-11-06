package com.stevetomadeline.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.stevetomadeline.client.assets.CelesteAssets;
import com.stevetomadeline.client.windowx.WindowXCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class MadelineSettingsScreen extends Screen {
    private final Screen parent;
    private int page, tab, rows, start, contentWidth;
    private ControlBindings.Action listening;
    private int listeningSlot;
    private EditBox path;
    public MadelineSettingsScreen(Screen parent) { super(Component.translatable("stevetomadeline.settings.title")); this.parent = parent; }
    private static Component text(String key) { return Component.translatable("stevetomadeline." + key); }
    private Button button(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, w, 20).build());
    }
    @Override protected void init() {
        CelesteAssets.ensure();
        contentWidth = Math.min(440, width - 24); start = (width - contentWidth) / 2;
        button(text("settings.controls"), start, 30, contentWidth / 4 - 2, () -> { tab = 0; listening = null; rebuildWidgets(); }).active = tab != 0;
        button(text("settings.character"), start + contentWidth / 4, 30, contentWidth / 4 - 2, () -> { tab = 1; listening = null; rebuildWidgets(); }).active = tab != 1;
        button(text("settings.effects"), start + contentWidth / 2, 30, contentWidth / 4 - 2, () -> { tab = 2; listening = null; rebuildWidgets(); }).active = tab != 2;
        button(text("settings.camera"), start + contentWidth * 3 / 4, 30, contentWidth / 4, () ->
                ModList.get().getModContainerById("stevetomadeline").ifPresent(container -> minecraft.setScreen(
                        new ConfigurationScreen(container, this, (context, key, element) ->
                                showCameraOption(key, WindowXCompat.available()) ? element : null))));
        if (tab == 0) controls(); else if (tab == 1) character(); else effects();
        button(Component.translatable("gui.done"), width / 2 - 70, height - 26, 140, this::onClose);
    }
    static boolean showCameraOption(String key, boolean windowXAvailable) {
        return windowXAvailable || (!key.equals("parallaxMode") && !key.equals("parallaxRatio"));
    }

    private void controls() {
        int modeLabelWidth = Math.min(150, contentWidth * 2 / 5);
        int modeWidth = (contentWidth - modeLabelWidth) / 3;
        for (GrabInput.Mode mode : GrabInput.Mode.values()) {
            Button selected = button(text("controls.grab." + mode.name().toLowerCase(java.util.Locale.ROOT)),
                    start + modeLabelWidth + mode.ordinal() * modeWidth, 59, modeWidth - 2, () -> {
                    var old = ControlBindings.presentation();
                    ControlBindings.presentation(new PresentationOptions(mode, old.soundEnabled(), old.soundVolume(), old.effectsEnabled()));
                    rebuildWidgets();
                });
            selected.active = mode != ControlBindings.presentation().grabMode();
        }
        rows = Math.max(1, (height - 154) / 23);
        int count = ControlBindings.Action.values().length, pages = (count + rows - 1) / rows;
        page = Math.min(page, pages - 1);
        int labelWidth = Math.min(150, contentWidth * 2 / 5), keyWidth = (contentWidth - labelWidth) / 3;
        for (int row = 0; row < rows && page * rows + row < count; row++) {
            var action = ControlBindings.Action.values()[page * rows + row];
            for (int slot = 0; slot < 3; slot++) {
                final int index = slot;
                Component name = listening == action && listeningSlot == slot ? text("controls.listening") : ControlBindings.name(ControlBindings.get(action, slot));
                String shown = font.plainSubstrByWidth(name.getString(), keyWidth - 25);
                Button button = button(Component.literal(shown).withColor(ControlBindings.conflict(action, slot) ? 0xFFC46A : 0xFFFFFF),
                        start + labelWidth + slot * keyWidth, 90 + row * 23, keyWidth - 19,
                        () -> { listening = action; listeningSlot = index; rebuildWidgets(); });
                Component tooltip = action.label().copy().append("\n").append(name);
                button.setTooltip(Tooltip.create(ControlBindings.conflict(action, slot)
                        ? tooltip.copy().append("\n").append(text("controls.conflict")) : tooltip));
                button(Component.literal("x"), start + labelWidth + (slot + 1) * keyWidth - 18, 90 + row * 23, 16,
                        () -> { ControlBindings.set(action, index, ""); listening = null; rebuildWidgets(); })
                        .setTooltip(Tooltip.create(text("controls.clear")));
            }
        }
        button(Component.literal("<"), start, height - 54, 24, () -> { page--; listening = null; rebuildWidgets(); }).active = page > 0;
        button(Component.literal(">"), start + 28, height - 54, 24, () -> { page++; listening = null; rebuildWidgets(); }).active = page + 1 < pages;
        button(text("controls.defaults"), start + contentWidth - 120, height - 54, 120, () -> { ControlBindings.defaults(); listening = null; rebuildWidgets(); });
    }
    private void character() {
        path = addRenderableWidget(new EditBox(font, start, 78, contentWidth - 64, 20, text("assets.path")));
        path.setMaxLength(4096); path.setValue(ControlBindings.assetPath());
        button(text("assets.browse"), start + contentWidth - 60, 78, 60, () -> {
            String selected = TinyFileDialogs.tinyfd_selectFolderDialog("Celeste", path.getValue());
            if (selected != null) { path.setValue(selected); reloadAssets(); }
        });
        button(text("assets.reload"), start, 105, contentWidth / 2 - 2, this::reloadAssets);
        button(text("assets.detect"), start + contentWidth / 2, 105, contentWidth / 2, () -> { path.setValue(""); reloadAssets(); });
        addRenderableWidget(Checkbox.builder(text("assets.show"), font).pos(start, 132).maxWidth(contentWidth)
                .selected(ControlBindings.showCharacter())
                .onValueChange((checkbox, enabled) -> ControlBindings.appearance(path.getValue(), enabled)).build());
    }
    private void reloadAssets() {
        ControlBindings.appearance(path.getValue(), ControlBindings.showCharacter());
        CelesteAssets.reload();
        com.stevetomadeline.client.audio.CelesteAudio.reload();
    }
    private void effects() {
        var options = ControlBindings.presentation();
        addRenderableWidget(Checkbox.builder(text("effects.sound"), font).pos(start, 62).maxWidth(contentWidth)
                .selected(options.soundEnabled()).onValueChange((box, value) -> {
                    var old = ControlBindings.presentation();
                    ControlBindings.presentation(new PresentationOptions(old.grabMode(), value, old.soundVolume(), old.effectsEnabled()));
                }).build());
        addRenderableWidget(new AbstractSliderButton(start, 90, contentWidth, 20, Component.empty(), options.soundVolume() / 100.0) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.translatable("stevetomadeline.effects.volume", Math.round(value * 100))); }
            @Override protected void applyValue() {
                var old = ControlBindings.presentation();
                ControlBindings.presentation(new PresentationOptions(old.grabMode(), old.soundEnabled(), (int) Math.round(value * 100), old.effectsEnabled()));
            }
        });
        addRenderableWidget(Checkbox.builder(text("effects.visual"), font).pos(start, 119).maxWidth(contentWidth)
                .selected(options.effectsEnabled()).onValueChange((box, value) -> {
                    var old = ControlBindings.presentation();
                    ControlBindings.presentation(new PresentationOptions(old.grabMode(), old.soundEnabled(), old.soundVolume(), value));
                }).build());
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (listening != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                String name = key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE ? "" : InputConstants.getKey(key, scan).getName();
                ControlBindings.set(listening, listeningSlot, name);
            }
            listening = null; rebuildWidgets(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (listening != null) {
            ControlBindings.set(listening, listeningSlot, InputConstants.Type.MOUSE.getOrCreate(button).getName());
            listening = null; rebuildWidgets(); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        gui.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (tab == 0) {
            gui.drawString(font, text("controls.grab"), start, 65, 0xFFFFFF);
            for (int row = 0; row < rows && page * rows + row < ControlBindings.Action.values().length; row++) {
                var action = ControlBindings.Action.values()[page * rows + row];
                gui.drawString(font, font.plainSubstrByWidth(action.label().getString(), Math.min(150, contentWidth * 2 / 5) - 4), start, 96 + row * 23, 0xFFFFFF);
            }
        } else if (tab == 1) {
            gui.drawString(font, text("assets.path"), start, 64, 0xFFFFFF);
            int available = Math.max(0, (height - 194) / 10);
            var lines = font.split(CelesteAssets.status(), contentWidth);
            for (int i = 0; i < Math.min(available, lines.size()); i++) gui.drawString(font, lines.get(i), start, 160 + i * 10, 0xD6D9DE);
        } else {
            var lines = font.split(com.stevetomadeline.client.audio.CelesteAudio.status(), contentWidth);
            for (int i = 0; i < Math.min(Math.max(0, (height - 186) / 10), lines.size()); i++)
                gui.drawString(font, lines.get(i), start, 152 + i * 10, 0xD6D9DE);
        }
        if (!ControlBindings.error().isEmpty()) gui.drawString(font,
                font.plainSubstrByWidth(text("controls.save_error").getString(), contentWidth), start, height - 66, 0xFF7777);
    }
    @Override public void onClose() { PhysicsKeys.clearClicks(); minecraft.setScreen(parent); }
}
