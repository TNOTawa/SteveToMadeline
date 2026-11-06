# Third-party notices

## desk-madeline

Movement constants, the Normal/Climb/Dash structure, jump and wall behavior, and the
TakeNewer directional-input rule were adapted from the GitHub repository
`solstice23/desk-madeline` at commit `8404a89e2a90cbafe35a97ef40f640083afd3c4c`.

Reference files: `Player.cs` (Normal/Climb/Dash, pixel movement, corrections, hair),
`IntegerAxis.cs`, `KeyBindings.cs`, `CelesteAtlas.cs`, `CelesteInstall.cs`,
`SpriteBank.cs`, `HairMeta.cs`, `Sprites.cs`, and the player/hair drawing sections of `PetWindow.cs`.
Presentation behavior also refers to `GrabMode.cs`, `SoundEffects.cs`, `Particles.cs`,
and the idle, dust, dash trail, and low-stamina sections of `Player.cs` and `PetWindow.cs`.
Regression scenarios also refer to `tests/DeskMadeline.Tests/MoveChecks.cs` and `InputChecks.cs`.

Copyright (c) 2026 solstice23. MIT license. The complete notice is included in
`src/main/resources/META-INF/licenses/desk-madeline.txt` and in the built JAR.

This is a Minecraft adaptation with its own world collision, networking, input
persistence and lifecycle handling. It does not ship Celeste graphics or audio,
desk-madeline desktop assets, Cateline assets, or other mod artwork.
