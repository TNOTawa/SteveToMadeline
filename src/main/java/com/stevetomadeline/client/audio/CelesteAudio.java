package com.stevetomadeline.client.audio;

import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.client.ClientPlaneController;
import com.stevetomadeline.client.assets.CelesteInstall;
import com.stevetomadeline.client.input.ControlBindings;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

/** Client-thread ownership, asynchronous loading, and Minecraft's pause/volume policy. */
public final class CelesteAudio {
    private static FmodAudio audio;
    private static boolean started, audible, loading;
    private static int generation;
    private static Component status = Component.translatable("stevetomadeline.audio.loading");
    private CelesteAudio() { }
    public static Component status() { return status; }
    public static void reload() { close(); }
    public static void close() {
        generation++; started = false; audible = false;
        if (audio != null) {
            var old = audio; audio = null;
            try { old.close(); } catch (RuntimeException ex) { SteveToMadeline.LOGGER.warn("Could not release Celeste audio", ex); }
        }
    }
    public static void reset() { safe(() -> audio.stopAll()); }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { audible = false; safe(() -> { audio.volume(0); audio.stopAll(); audio.update(); }); return; }
        if (!started && !loading) {
            started = loading = true; int request = ++generation;
            String configured = ControlBindings.assetPath();
            status = Component.translatable("stevetomadeline.audio.loading");
            CompletableFuture.supplyAsync(() -> {
                try { return FmodAudio.open(CelesteInstall.find(configured), false); }
                catch (Exception ex) { throw new java.util.concurrent.CompletionException(ex); }
            }).whenComplete((loaded, failure) -> mc.execute(() -> {
                loading = false;
                if (request != generation) {
                    if (loaded != null) try { loaded.close(); } catch (RuntimeException ex) { SteveToMadeline.LOGGER.warn("Could not release stale Celeste audio", ex); }
                    return;
                }
                if (failure != null) {
                    status = Component.translatable("stevetomadeline.audio.failed", failure.getCause().getMessage());
                    SteveToMadeline.LOGGER.warn("Celeste audio could not be loaded", failure);
                } else { audio = loaded; status = Component.translatable("stevetomadeline.audio.ready"); }
            }));
        }
        var options = ControlBindings.presentation();
        audible = options.soundEnabled() && mc.isWindowActive() && !mc.isPaused()
                && mc.screen == null && ClientPlaneController.plane() != null;
        safe(() -> {
            audio.volume(audible ? options.soundVolume() / 100f * mc.options.getSoundSourceVolume(SoundSource.MASTER)
                    * mc.options.getSoundSourceVolume(SoundSource.PLAYERS) : 0);
            if (!audible) audio.stopAll();
            audio.update();
        });
    }
    public static void play(String name) { play(name, null, null, 0); }
    public static void surface(String name) { play(name, null, "surface_index", 8); }
    public static void play(String name, String key, String parameter, float value) {
        if (audible) safe(() -> audio.play("event:/char/madeline/" + name, key, parameter, value));
    }
    public static void stop(String key) { safe(() -> audio.stop(key)); }
    private static void safe(Runnable operation) {
        if (audio == null) return;
        try { operation.run(); }
        catch (RuntimeException | LinkageError ex) {
            SteveToMadeline.LOGGER.warn("Celeste audio disabled after a runtime failure", ex);
            close(); started = true;
            status = Component.translatable("stevetomadeline.audio.failed", ex.getMessage());
        }
    }
}
