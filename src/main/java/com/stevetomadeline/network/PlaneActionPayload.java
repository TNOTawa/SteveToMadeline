package com.stevetomadeline.network;

import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.core.plane.Heading;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlaneActionPayload(int requestId, Action action, Heading heading, ResourceLocation dimension)
        implements CustomPacketPayload {
    public enum Action { ENTER, EXIT, TURN }
    public static final Type<PlaneActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SteveToMadeline.MODID, "plane_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaneActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.requestId);
                buffer.writeEnum(payload.action);
                buffer.writeEnum(payload.heading);
                buffer.writeResourceLocation(payload.dimension);
            }, buffer -> new PlaneActionPayload(buffer.readVarInt(), buffer.readEnum(Action.class),
                    buffer.readEnum(Heading.class), buffer.readResourceLocation()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
