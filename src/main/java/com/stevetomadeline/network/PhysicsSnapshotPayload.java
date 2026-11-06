package com.stevetomadeline.network;

import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.core.physics.PhysicsParameters;
import com.stevetomadeline.core.physics.PhysicsState;
import com.stevetomadeline.core.plane.Heading;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PhysicsSnapshotPayload(int entityId, ResourceLocation dimension, Heading heading, int epoch, int acknowledged,
                                      PhysicsParameters parameters, PhysicsState state) implements CustomPacketPayload {
    public static final Type<PhysicsSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SteveToMadeline.MODID, "physics_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicsSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            PhysicsSnapshotPayload::write, PhysicsSnapshotPayload::read);

    private static void write(RegistryFriendlyByteBuf b, PhysicsSnapshotPayload p) {
        b.writeVarInt(p.entityId); b.writeResourceLocation(p.dimension); b.writeEnum(p.heading);
        b.writeVarInt(p.epoch); b.writeVarInt(p.acknowledged); b.writeVarInt(p.parameters.maxDashes());
        var s = p.state;
        b.writeDouble(s.x); b.writeDouble(s.y);
        for (float value : new float[]{s.vx, s.vy, s.remainderX, s.remainderY, s.dashX, s.dashY, s.beforeDashX,
                s.variableSpeed, s.maxFallSpeed, s.stamina, s.retainedSpeed, s.hopWaitSpeed,
                s.coyote, s.jumpBuffer, s.dashBuffer, s.crouchDashBuffer, s.variableJump, s.dashCooldown, s.refillCooldown,
                s.dashWindup, s.dashTime, s.dashAttack, s.wallSlide, s.forceTime, s.climbNoMove, s.wallBoost,
                s.wallRetention, s.spriteScaleX, s.spriteScaleY}) b.writeFloat(value);
        for (int value : new int[]{s.facing, s.dashes, s.forceDirection, s.wallBoostDirection, s.previousMove, s.previousMoveY,
                s.hopWaitX, s.lastClimbMove, s.simulationFrame, s.dashSequence, s.jumpSequence, s.landingSequence}) b.writeVarInt(value);
        b.writeEnum(s.motion);
        for (boolean value : new boolean[]{s.grounded, s.sliding, s.ducking, s.autoJump, s.dashAimPending, s.dashStartedOnGround, s.nativeMovement}) b.writeBoolean(value);
    }

    private static PhysicsSnapshotPayload read(RegistryFriendlyByteBuf b) {
        int entity = b.readVarInt(); ResourceLocation dimension = b.readResourceLocation(); Heading heading = b.readEnum(Heading.class);
        int epoch = b.readVarInt(), ack = b.readVarInt(), dashes = b.readVarInt();
        var parameters = new PhysicsParameters(16, 90.0 / 16, 105.0 / 16, 240.0 / 16, 900.0 / 16, 160.0 / 16, dashes);
        var s = new PhysicsState();
        s.x = b.readDouble(); s.y = b.readDouble();
        s.vx = b.readFloat(); s.vy = b.readFloat(); s.remainderX = b.readFloat(); s.remainderY = b.readFloat();
        s.dashX = b.readFloat(); s.dashY = b.readFloat(); s.beforeDashX = b.readFloat(); s.variableSpeed = b.readFloat();
        s.maxFallSpeed = b.readFloat(); s.stamina = b.readFloat(); s.retainedSpeed = b.readFloat(); s.hopWaitSpeed = b.readFloat();
        s.coyote = b.readFloat(); s.jumpBuffer = b.readFloat(); s.dashBuffer = b.readFloat(); s.crouchDashBuffer = b.readFloat();
        s.variableJump = b.readFloat(); s.dashCooldown = b.readFloat(); s.refillCooldown = b.readFloat();
        s.dashWindup = b.readFloat(); s.dashTime = b.readFloat(); s.dashAttack = b.readFloat(); s.wallSlide = b.readFloat();
        s.forceTime = b.readFloat(); s.climbNoMove = b.readFloat(); s.wallBoost = b.readFloat(); s.wallRetention = b.readFloat();
        s.spriteScaleX = b.readFloat(); s.spriteScaleY = b.readFloat();
        s.facing = b.readVarInt(); s.dashes = b.readVarInt(); s.forceDirection = b.readVarInt(); s.wallBoostDirection = b.readVarInt();
        s.previousMove = b.readVarInt(); s.previousMoveY = b.readVarInt(); s.hopWaitX = b.readVarInt(); s.lastClimbMove = b.readVarInt();
        s.simulationFrame = b.readVarInt(); s.dashSequence = b.readVarInt(); s.jumpSequence = b.readVarInt(); s.landingSequence = b.readVarInt();
        s.motion = b.readEnum(PhysicsState.Motion.class);
        s.grounded = b.readBoolean(); s.sliding = b.readBoolean(); s.ducking = b.readBoolean(); s.autoJump = b.readBoolean();
        s.dashAimPending = b.readBoolean(); s.dashStartedOnGround = b.readBoolean();
        s.nativeMovement = b.readBoolean();
        return new PhysicsSnapshotPayload(entity, dimension, heading, epoch, ack, parameters, s);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
