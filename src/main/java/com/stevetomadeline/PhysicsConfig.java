package com.stevetomadeline;

import com.stevetomadeline.core.physics.PhysicsParameters;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Physical values are negotiated from the server, never read from a client's camera config. */
public final class PhysicsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final PhysicsParameters DEFAULT = PhysicsParameters.DEFAULT;
    public static final ModConfigSpec.IntValue MAX_DASHES = BUILDER.defineInRange("maxDashes", DEFAULT.maxDashes(), 1, 2);
    public static final ModConfigSpec.BooleanValue FALL_DAMAGE = BUILDER
            .comment("Keep Minecraft fall damage. Disable for Celeste-style platforming maps.")
            .define("fallDamage", true);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private PhysicsConfig() {
    }

    public static PhysicsParameters parameters() {
        return new PhysicsParameters(16, DEFAULT.runSpeed(), DEFAULT.jumpSpeed(), DEFAULT.dashSpeed(),
                DEFAULT.gravity(), DEFAULT.maxFall(), MAX_DASHES.get());
    }
}
