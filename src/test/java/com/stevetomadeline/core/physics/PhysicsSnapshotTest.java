package com.stevetomadeline.core.physics;

import com.stevetomadeline.core.plane.Heading;
import com.stevetomadeline.network.PhysicsInputPayload;
import com.stevetomadeline.network.PhysicsSnapshotPayload;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Modifier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class PhysicsSnapshotTest {
    @ParameterizedTest
    @EnumSource(PhysicsState.Motion.class)
    void everyReplayFieldSurvivesTheSnapshotCodec(PhysicsState.Motion motion) throws Exception {
        var state = new PhysicsState(); int number = 1;
        for (var field : PhysicsState.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() == float.class) field.setFloat(state, number + 0.125f);
            else if (field.getType() == double.class) field.setDouble(state, number + 0.0625);
            else if (field.getType() == int.class) field.setInt(state, number);
            else if (field.getType() == boolean.class) field.setBoolean(state, true);
            number++;
        }
        state.motion = motion;
        var packet = new PhysicsSnapshotPayload(8, ResourceLocation.parse("minecraft:overworld"), Heading.WEST, 3, 42, PhysicsParameters.DEFAULT, state);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PhysicsSnapshotPayload.STREAM_CODEC.encode(buffer, packet);
            var copy = PhysicsSnapshotPayload.STREAM_CODEC.decode(buffer);
            for (var field : PhysicsState.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) assertEquals(field.get(state), field.get(copy.state()), field.getName());
            }
            assertEquals(packet.heading(), copy.heading()); assertEquals(packet.acknowledged(), copy.acknowledged());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void packetKeepsDifferentEdgesInAllThreeSubframes() {
        var first = new PhysicsInput(1, 0, 1, 0, true, false, true, false);
        var second = new PhysicsInput(-1, -1, -1, -1, false, true, false, true);
        var third = new PhysicsInput(0, 1, 0, 1, true, true, true, false, true);
        var packet = new PhysicsInputPayload(4, 9, new PhysicsFrame(first, second, third));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PhysicsInputPayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, PhysicsInputPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
