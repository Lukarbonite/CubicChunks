package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cubicchunks.CubicChunksCommon;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fabric implementation of {@link NetworkHelper} using vanilla {@link CustomPacketPayload}s and the
 * Fabric networking API. Registered via
 * {@code META-INF/services/io.github.opencubicchunks.cubicchunks.network.NetworkHelper}.
 */
public final class FabricNetworkHelper implements NetworkHelper {

    /** A registered S2C type: the vanilla payload type plus the neutral packet type. */
    public record Registration(CustomPacketPayload.Type<CubicPayload> payloadType, CubicPacketType<?> cubicType) {
    }

    private final Map<String, Registration> registrations = new LinkedHashMap<>();

    @Override
    public void registerS2CType(CubicPacketType<?> cubicType) {
        Identifier id = Identifier.fromNamespaceAndPath(CubicChunksCommon.MOD_ID, cubicType.id());
        CustomPacketPayload.Type<CubicPayload> payloadType = new CustomPacketPayload.Type<>(id);

        StreamCodec<RegistryFriendlyByteBuf, CubicPayload> codec = CustomPacketPayload.codec(
                (payload, buf) -> payload.packet().write(buf),
                buf -> new CubicPayload(payloadType, cubicType.decode(buf)));

        PayloadTypeRegistry.clientboundPlay().register(payloadType, codec);
        registrations.put(cubicType.id(), new Registration(payloadType, cubicType));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CubicPacket packet) {
        Registration reg = registrations.get(packet.type().id());
        if (reg == null) {
            throw new IllegalStateException("Unregistered cubic packet type: " + packet.type().id());
        }
        ServerPlayNetworking.send(player, new CubicPayload(reg.payloadType(), packet));
    }

    /** Registered S2C types, for the client entry point to attach receivers to. */
    public Collection<Registration> registrations() {
        return registrations.values();
    }
}
