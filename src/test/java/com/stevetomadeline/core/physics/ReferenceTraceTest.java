package com.stevetomadeline.core.physics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The fixture is recorded by the unchanged desk-madeline Player.cs at 8404a89. */
class ReferenceTraceTest {
    private record Rect(double l, double b, double r, double t) { }
    private static final class Room implements PhysicsWorld {
        final List<Rect> solids = new ArrayList<>();
        @Override public boolean solid(double x, double y, boolean ducking) {
            double h = (ducking ? 6 : 11) / 16.0;
            return solids.stream().anyMatch(r -> x - 0.25 < r.r && x + 0.25 > r.l && y < r.t && y + h > r.b);
        }
        @Override public boolean solidPoint(double x, double y) {
            return solids.stream().anyMatch(r -> x >= r.l && x < r.r && y > r.b && y <= r.t);
        }
    }
    @Test void compareEveryFrameAgainstUpstreamPlayer() throws Exception {
        JsonObject root;
        try (var stream = getClass().getResourceAsStream("/reference-trace.json.gz")) {
            if (stream == null) throw new IOException("Missing upstream reference trace");
            try (var reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        assertEquals("8404a89e2a90cbafe35a97ef40f640083afd3c4c", root.get("commit").getAsString());
        for (var scenarioElement : root.getAsJsonArray("scenarios")) {
            JsonObject scenario = scenarioElement.getAsJsonObject();
            String name = scenario.get("name").getAsString();
            Room world = new Room();
            for (var rectElement : scenario.getAsJsonArray("boxes")) {
                JsonArray rect = rectElement.getAsJsonArray();
                world.solids.add(new Rect(rect.get(0).getAsDouble() / 16, rect.get(1).getAsDouble() / 16,
                        rect.get(2).getAsDouble() / 16, rect.get(3).getAsDouble() / 16));
            }
            JsonArray initial = scenario.getAsJsonArray("initial");
            var state = new PhysicsState();
            state.x = initial.get(0).getAsDouble() / 16; state.y = initial.get(1).getAsDouble() / 16;
            state.vx = initial.get(2).getAsFloat() / 16; state.vy = initial.get(3).getAsFloat() / 16;
            state.stamina = scenario.get("initialStamina").getAsFloat();
            state.motion = switch (scenario.get("initialState").getAsInt()) {
                case 0 -> PhysicsState.Motion.NORMAL;
                case 1 -> PhysicsState.Motion.CLIMB;
                case 2 -> PhysicsState.Motion.DASH;
                default -> throw new IllegalArgumentException(name);
            };
            state.ducking = scenario.get("initialDucking").getAsBoolean();
            int tick = 0;
            for (var frameElement : scenario.getAsJsonArray("frames")) {
                JsonObject frame = frameElement.getAsJsonObject(); JsonArray raw = frame.getAsJsonArray("input");
                var input = new PhysicsInput(raw.get(0).getAsInt(), raw.get(1).getAsInt(), raw.get(0).getAsInt(), raw.get(1).getAsInt(),
                        raw.get(2).getAsInt() != 0, raw.get(5).getAsInt() != 0,
                        raw.get(3).getAsInt() != 0, raw.get(4).getAsInt() != 0, raw.get(6).getAsInt() != 0);
                // Adding a presentation observer must leave every replay field unchanged.
                var observed = state.copy();
                var events = new ArrayList<PhysicsEffects.Event>();
                CelestePhysics.step(observed, input, PhysicsParameters.DEFAULT, world, (event, current) -> events.add(event));
                CelestePhysics.step(state, input, PhysicsParameters.DEFAULT, world);
                for (var field : PhysicsState.class.getFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                        assertEquals(field.get(state), field.get(observed), name + " observer " + field.getName());
                }
                JsonObject expected = frame.getAsJsonObject("state");
                String context = name + " frame " + tick++;
                assertEquals(expected.get("x").getAsDouble(), state.x * 16, 0.001, context + " x");
                assertEquals(expected.get("y").getAsDouble(), state.y * 16, 0.001, context + " y");
                assertEquals(expected.get("vx").getAsDouble(), state.vx * 16, 0.002, context + " vx");
                assertEquals(expected.get("vy").getAsDouble(), state.vy * 16, 0.002, context + " vy");
                assertEquals(expected.get("stamina").getAsDouble(), state.stamina, 0.002, context + " stamina");
                assertEquals(expected.get("dashes").getAsInt(), state.dashes, context + " dashes");
                int motion = switch (state.motion) {
                    case NORMAL -> 0; case CLIMB -> 1; case DASH -> 2;
                    case SWIM -> throw new AssertionError("Dry reference scene entered water: " + context);
                };
                assertEquals(expected.get("motion").getAsInt(), motion, context + " motion");
                assertEquals(expected.get("ducking").getAsBoolean(), state.ducking, context + " ducking");
                assertEquals(expected.get("grounded").getAsBoolean(), state.grounded, context + " grounded");
                for (String field : new String[]{"coyote", "variableJump", "dashTime", "dashAttack",
                        "refillCooldown", "wallBoost", "wallRetention"}) {
                    float actual = switch (field) {
                        case "coyote" -> state.coyote;
                        case "variableJump" -> state.variableJump;
                        case "dashTime" -> state.dashTime;
                        case "dashAttack" -> state.dashAttack;
                        case "refillCooldown" -> state.refillCooldown;
                        case "wallBoost" -> state.wallBoost;
                        case "wallRetention" -> state.wallRetention;
                        default -> throw new AssertionError(field);
                    };
                    assertEquals(expected.get(field).getAsDouble(), actual, 0.0001, context + " " + field);
                }
            }
        }
    }
}
