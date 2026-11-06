# Third-party notices

Original SteveToMadeline code is licensed under the MIT License; see
[`LICENSE`](LICENSE). The notices below cover adapted code and optional
third-party components.

## Celeste public source samples

Swimming parameters, surface checks, water jump and dash behavior refer to
`NoelFB/Celeste`, `Source/Player/Player.cs`, commit
`7fd7ae760bc98bc1659c035d807cb47a3463e9b7`. The Minecraft fluid queries and
prediction integration are specific to this adaptation.

Copyright (c) 2018 Noel Berry. MIT license for the published source samples;
the complete notice is included in `src/main/resources/META-INF/licenses/celeste-source.txt`
and in the built JAR. This license does not cover the commercial game's
graphics, audio, or other assets.

## desk-madeline

Movement constants, the Normal/Climb/Dash structure, jump and wall behavior, and the
TakeNewer directional-input rule were adapted from the GitHub repository
`solstice23/desk-madeline` at commit `8404a89e2a90cbafe35a97ef40f640083afd3c4c`.

Reference files: `Player.cs` (Normal/Climb/Dash, pixel movement, corrections, hair),
`IntegerAxis.cs`, `KeyBindings.cs`, `CelesteAtlas.cs`, `CelesteInstall.cs`,
`SpriteBank.cs`, `HairMeta.cs`, `Sprites.cs`, and the player/hair drawing sections of
`PetWindow.cs`. Presentation behavior also refers to `GrabMode.cs`, `SoundEffects.cs`,
`Particles.cs`, and the idle, dust, dash trail, and low-stamina sections of `Player.cs` and
`PetWindow.cs`. Regression scenarios also refer to
`tests/DeskMadeline.Tests/MoveChecks.cs` and `InputChecks.cs`.

Copyright (c) 2026 solstice23. MIT license. The complete notice is included in
`src/main/resources/META-INF/licenses/desk-madeline.txt` and in the built JAR.

## WindowX (optional, compile-only)

`libs/windowx-2.2.0-Beta2-1.21.1-NeoForge.jar` is an unmodified third-party
build by **XuTrain**. It is not part of this project's source and is not covered
by this project's MIT license. It is vendored only so the optional
experimental `LAYERED` background can be compiled; normal builds, tests, and
all non-`LAYERED` rendering do not require WindowX at runtime.

The JAR's bundled `neoforge.mods.toml` declares the MIT license, while the
Modrinth project page lists Apache-2.0. Copyright and licensing remain with its
author. Remove `libs/windowx-2.2.0-Beta2-1.21.1-NeoForge.jar` and the
`client/windowx` / `WindowX*` sources if your fork does not want to carry it.

## Celeste game assets

This repository and the release JAR do **not** include Celeste graphics, audio,
fonts, or other commercial game assets, desk-madeline desktop assets, Cateline
assets, or third-party mod artwork.

The optional character and audio features read files from a locally installed,
legitimately purchased copy of Celeste at runtime. Those files remain subject to
their own licenses and are never redistributed by this project.

## Minecraft and NeoForge

Minecraft is a trademark of Mojang Synergies AB. NeoForge is a community
project. This mod is not affiliated with or endorsed by Mojang, Microsoft,
Maddy Makes Games, or the NeoForge team.
