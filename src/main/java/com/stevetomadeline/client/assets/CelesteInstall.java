package com.stevetomadeline.client.assets;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public final class CelesteInstall {
    private CelesteInstall() { }
    public static boolean valid(Path root) {
        return Files.isRegularFile(root.resolve("Content/Graphics/Atlases/Gameplay.meta"))
                && Files.isRegularFile(root.resolve("Content/Graphics/Sprites.xml"));
    }
    public static Path find(String configured) throws IOException {
        String named = configured.isBlank() ? System.getenv("CELESTE_PATH") : configured;
        if (named != null && !named.isBlank()) {
            Path root = Path.of(named).toAbsolutePath().normalize();
            if (!valid(root)) throw new IOException("Celeste graphics missing: " + root);
            return root;
        }
        LinkedHashSet<Path> libraries = new LinkedHashSet<>();
        for (File drive : File.listRoots()) {
            Path root = drive.toPath();
            libraries.add(root.resolve("SteamLibrary")); libraries.add(root.resolve("Steam"));
            libraries.add(root.resolve("steam/steam"));
        }
        for (String env : new String[]{"ProgramFiles(x86)", "ProgramFiles"}) {
            if (System.getenv(env) != null) libraries.add(Path.of(System.getenv(env), "Steam"));
        }
        // Steam's library file is data, never executed. Read only known library roots.
        for (Path steam : List.copyOf(libraries)) {
            Path vdf = steam.resolve("steamapps/libraryfolders.vdf");
            if (!Files.isRegularFile(vdf)) continue;
            var matches = Pattern.compile("\"path\"\\s+\"([^\"]+)\"").matcher(Files.readString(vdf));
            while (matches.find()) libraries.add(Path.of(matches.group(1).replace("\\\\", "\\")));
        }
        for (Path library : libraries) {
            Path root = library.resolve("steamapps/common/Celeste");
            if (valid(root)) return root;
        }
        throw new IOException("Celeste installation not found");
    }
}
