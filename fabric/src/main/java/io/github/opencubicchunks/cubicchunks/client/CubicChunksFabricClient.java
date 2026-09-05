package io.github.opencubicchunks.cubicchunks.client;

import io.github.opencubicchunks.cubicchunks.network.FabricNetworkHelper;
import io.github.opencubicchunks.cubicchunks.network.NetworkHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Fabric client entry point. Runs after the main entry point, so packet types are already
 * registered; here we attach the client-side receivers that dispatch to each packet's
 * {@code handle()} on the client main thread.
 */
public class CubicChunksFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        if (NetworkHelper.INSTANCE instanceof FabricNetworkHelper helper) {
            for (FabricNetworkHelper.Registration reg : helper.registrations()) {
                ClientPlayNetworking.registerGlobalReceiver(reg.payloadType(),
                        (payload, context) -> context.client().execute(payload.packet()::handle));
            }
        }
    }
}
