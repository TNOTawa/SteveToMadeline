package com.stevetomadeline.network;

import com.stevetomadeline.core.mode.ServerPlaneController;
import com.stevetomadeline.core.physics.PhysicsController;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;

/** Registration is common-side; the client installs its handler without a server-side client class reference. */
public final class PlaneNetwork {
    private static Consumer<PlaneSnapshotPayload> clientHandler = payload -> {};
    private static Consumer<PhysicsSnapshotPayload> physicsHandler = payload -> {};
    public static void setPhysicsHandler(Consumer<PhysicsSnapshotPayload> handler) { physicsHandler = handler; }

    private PlaneNetwork() {
    }

    public static void setClientHandler(Consumer<PlaneSnapshotPayload> handler) {
        clientHandler = handler;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("4").optional();
        registrar.playToServer(PhysicsInputPayload.TYPE, PhysicsInputPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) PhysicsController.input(player, payload);
        });
        registrar.playToClient(PhysicsSnapshotPayload.TYPE, PhysicsSnapshotPayload.STREAM_CODEC,
                (payload, context) -> physicsHandler.accept(payload));
        registrar.playToServer(PlaneActionPayload.TYPE, PlaneActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) ServerPlaneController.handle(player, payload);
        });
        registrar.playToClient(PlaneSnapshotPayload.TYPE, PlaneSnapshotPayload.STREAM_CODEC,
                (payload, context) -> clientHandler.accept(payload));
    }
}
