package com.stevetomadeline.network;

import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.core.physics.PhysicsInput;
import com.stevetomadeline.core.physics.PhysicsFrame;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PhysicsInputPayload(int epoch, int sequence, PhysicsFrame input) implements CustomPacketPayload {
    public static final Type<PhysicsInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SteveToMadeline.MODID, "physics_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicsInputPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeVarInt(p.epoch); b.writeVarInt(p.sequence);
                for (int i = 0; i < 3; i++) {
                    PhysicsInput f = p.input.at(i);
                    b.writeByte(f.moveX()); b.writeByte(f.moveY()); b.writeByte(f.aimX()); b.writeByte(f.aimY());
                    b.writeBoolean(f.jumpHeld()); b.writeBoolean(f.grabHeld()); b.writeBoolean(f.jumpPressed());
                    b.writeBoolean(f.dashPressed()); b.writeBoolean(f.crouchDashPressed());
                }
            }, b -> new PhysicsInputPayload(b.readVarInt(), b.readVarInt(), new PhysicsFrame(read(b), read(b), read(b))));
    private static PhysicsInput read(RegistryFriendlyByteBuf b) {
        return new PhysicsInput(b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readBoolean(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
