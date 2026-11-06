# Headless Movement Comparison

`ReferenceTrace.cs` compiles the **unmodified** `Player.cs` and supporting
`PetHoldable.cs`/`SpriteBank.cs` from `solstice23/desk-madeline` commit
`8404a89e2a90cbafe35a97ef40f640083afd3c4c`. `HeadlessDependencies.cs`
only stubs unreachable desktop, skin, and asset integrations. It throws if one
is called. No Celeste or Minecraft process starts.

The checked-in `src/test/resources/reference-trace.json.gz` is the compressed
JSON output: 17 scenarios and 2,905 60 Hz frames. `ReferenceTraceTest` compares
every frame's position, speed, stamina, dashes, state, duck flag, ground flag,
and seven timers against the Java adaptation. SHA256 of the gzip fixture:
`499F4ED50C2F289ACA1E5188DF942ED1172D4F6793C9A6F76E37F22899F974AC`.

To regenerate intentionally, clone the exact upstream commit under
`build/reference-tmp/desk-madeline-current`. Compile the five C# files listed
above with a .NET 10 C# compiler (`csc`) and the matching
`Microsoft.NETCore.App` / `Microsoft.WindowsDesktop.App` reference assemblies
from your local .NET installation. Execute the result with `dotnet exec
--runtimeconfig tools/reference/ReferenceTrace.runtimeconfig.json` and pass an
output JSON path.
Compress the resulting bytes as gzip to the fixture path. Review a semantic
diff of the decoded JSON before accepting a changed fixture. The .NET runtime
and compiler versions are tooling only, not mod dependencies.

This comparison isolates static rectangular solids and the implemented
Normal/Climb/Dash routes. It does not prove equivalence for Minecraft voxel
shapes, external forces, dynamic platforms, game rendering, sound, or the
desktop-only states. Run `gradlew build` for the whole Java suite.
