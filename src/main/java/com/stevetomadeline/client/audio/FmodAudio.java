package com.stevetomadeline.client.audio;

import com.sun.jna.Library;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.VersionUtil;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.*;

/** Original FMOD 1.x events from the user's installation; no bundled samples or native libraries. */
public final class FmodAudio implements AutoCloseable {
    private NativeLibrary core, studio;
    private Pointer system, bus;
    private final Map<String, Pointer> descriptions = new HashMap<>(), kept = new HashMap<>();
    private final Set<String> missing = new HashSet<>();

    public static FmodAudio open(Path game, boolean silent) throws IOException {
        if (!System.getProperty("os.name").startsWith("Windows")) throw new IOException("Windows x64 FMOD runtime required");
        FmodAudio audio = new FmodAudio();
        try {
            Path[] libraries = locate(game);
            var info = VersionUtil.getFileVersionInfo(libraries[1].toString());
            int major = info.getFileVersionMajor();
            int minor = info.getFileVersionMinor();
            int patch = info.getFileVersionRevision();
            // Some Everest builds encode 1.10.14 as the four fields 0.1.10.14.
            if (major == 0 && minor == 1) {
                major = minor; minor = patch; patch = info.getFileVersionBuild();
            }
            if (major != 1) throw new IOException("FMOD 1.x required; found " + major + "." + minor + "." + patch);
            if (minor > 99 || patch > 99) throw new IOException("Unsupported FMOD version fields");
            int version = bcd(major) << 16 | bcd(minor) << 8 | bcd(patch);
            var options = Map.of(Library.OPTION_STRING_ENCODING, "UTF-8");
            audio.core = NativeLibrary.getInstance(libraries[0].toString(), options);
            audio.studio = NativeLibrary.getInstance(libraries[1].toString(), options);
            PointerByReference out = new PointerByReference();
            audio.call("System_Create", out, version); audio.system = out.getValue();
            // Headless integration checks use FMOD's no-sound output, never a game process.
            if (silent) {
                audio.call("System_GetLowLevelSystem", audio.system, out);
                check(audio.core.getFunction("FMOD_System_SetOutput").invokeInt(new Object[]{out.getValue(), 2}), "no-sound output");
            }
            audio.call("System_Initialize", audio.system, 64, 0, 0, null);
            Path banks = game.resolve("Content/FMOD/Desktop");
            for (String name : new String[]{"Master Bank.bank", "Master Bank.strings.bank", "sfx.bank", "dlc_sfx.bank"}) {
                Path bank = banks.resolve(name);
                if (!Files.isRegularFile(bank)) {
                    if (name.equals("dlc_sfx.bank")) continue;
                    throw new IOException("Missing Celeste bank: " + bank);
                }
                audio.call("System_LoadBankFile", audio.system, bank.toString(), 0, out);
            }
            audio.call("System_GetBus", audio.system, "bus:/gameplay_sfx", out); audio.bus = out.getValue();
            audio.volume(0);
            return audio;
        } catch (Exception | LinkageError failure) {
            try { audio.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw new IOException("Celeste audio unavailable: " + failure.getMessage(), failure);
        }
    }
    private static int bcd(int value) { return value / 10 * 16 + value % 10; }
    private static Path[] locate(Path root) throws IOException {
        for (String[] layout : new String[][]{{"lib64-win-x64", "fmod64.dll", "fmodstudio64.dll"},
                {"lib64-win-x64", "fmod64.dll", "fmodstudio.dll"}, {"", "fmod64.dll", "fmodstudio64.dll"},
                {"", "fmod64.dll", "fmodstudio.dll"}, {"", "fmod.dll", "fmodstudio.dll"}}) {
            Path core = root.resolve(layout[0]).resolve(layout[1]), studio = root.resolve(layout[0]).resolve(layout[2]);
            if (isX64(core) && isX64(studio)) return new Path[]{core, studio};
        }
        throw new IOException("Compatible 64-bit FMOD libraries missing (Celeste/Everest lib64-win-x64)");
    }
    public static boolean isX64(Path file) {
        if (!Files.isRegularFile(file)) return false;
        try (var input = new RandomAccessFile(file.toFile(), "r")) {
            if (input.length() < 64 || input.readUnsignedShort() != 0x4D5A) return false;
            input.seek(0x3c); long header = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));
            if (header + 6 > input.length()) return false;
            input.seek(header);
            return Integer.reverseBytes(input.readInt()) == 0x4550 && Short.reverseBytes(input.readShort()) == (short) 0x8664;
        } catch (IOException ex) { return false; }
    }
    private static void check(int result, String operation) {
        if (result != 0) throw new IllegalStateException(operation + " (FMOD " + result + ")");
    }
    private void call(String name, Object... args) { check(studio.getFunction("FMOD_Studio_" + name).invokeInt(args), name); }
    private Pointer description(String event) {
        return descriptions.computeIfAbsent(event, path -> {
            PointerByReference result = new PointerByReference(); call("System_GetEvent", system, path, result); return result.getValue();
        });
    }
    public boolean hasEvent(String event) { try { description(event); return true; } catch (RuntimeException ex) { return false; } }
    public boolean play(String event, String key, String parameter, float value) {
        if (system == null || missing.contains(event)) return false;
        if ("idle".equals(key) && kept.containsKey(key)) {
            call("EventInstance_Release", kept.remove(key));
        } else if (key != null && kept.containsKey(key)) return true;
        Pointer instance = null;
        try {
            PointerByReference out = new PointerByReference(); call("EventDescription_CreateInstance", description(event), out);
            instance = out.getValue();
            if (parameter != null) parameter(instance, parameter, value);
            call("EventInstance_Start", instance);
            if (key != null) { kept.put(key, instance); instance = null; }
            return true;
        } catch (RuntimeException ex) {
            missing.add(event);
            com.stevetomadeline.SteveToMadeline.LOGGER.warn("Celeste sound {} unavailable: {}", event, ex.getMessage());
            return false;
        } finally { if (instance != null) call("EventInstance_Release", instance); }
    }
    private void parameter(Pointer instance, String name, float value) {
        PointerByReference out = new PointerByReference(); call("EventInstance_GetParameter", instance, name, out);
        call("ParameterInstance_SetValue", out.getValue(), value);
    }
    public void stop(String key) {
        Pointer instance = kept.remove(key);
        if (instance != null) { call("EventInstance_Stop", instance, 0); call("EventInstance_Release", instance); }
    }
    public void stopAll() { for (String key : List.copyOf(kept.keySet())) stop(key); }
    public void volume(float value) { if (system != null) call("Bus_SetVolume", bus, value); }
    public void update() { if (system != null) call("System_Update", system); }
    @Override public void close() {
        try {
            if (system != null) {
                try { stopAll(); } finally { call("System_Release", system); }
            }
        }
        finally {
            system = null; kept.clear(); descriptions.clear(); missing.clear();
            if (studio != null) { studio.close(); studio = null; }
            if (core != null) { core.close(); core = null; }
        }
    }
}
