package com.stevetomadeline.network;

import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.core.plane.Heading;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlaneSnapshotPayload(int requestId, int entityId, ResourceLocation dimension, boolean active,
                                   Heading heading, double anchorX, double anchorZ, Result result)
        implements CustomPacketPayload {
    public enum Result { ENTERED, EXITED, TURNED, BLOCKED, UNSUPPORTED, RESET, BUSY, EXIT_BLOCKED }
    public static final Type<PlaneSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SteveToMadeline.MODID, "plane_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaneSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.requestId);
                buffer.writeVarInt(payload.entityId);
                buffer.writeResourceLocation(payload.dimension);
                buffer.writeBoolean(payload.active);
                buffer.writeEnum(payload.heading);
                buffer.writeDouble(payload.anchorX);
                buffer.writeDouble(payload.anchorZ);
                buffer.writeEnum(payload.result);
            }, buffer -> new PlaneSnapshotPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readResourceLocation(),
                    buffer.readBoolean(), buffer.readEnum(Heading.class), buffer.readDouble(), buffer.readDouble(), buffer.readEnum(Result.class)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
