package com.antcolonies.content.menu;

import com.antcolonies.AntColonies;
import com.antcolonies.content.blockentity.ConnectorSettings;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player to the server: the new name of the Colony Connector whose screen they have open. */
public record RenameConnectorPayload(String name) implements CustomPacketPayload {
    public static final Type<RenameConnectorPayload> TYPE = new Type<>(AntColonies.id("rename_connector"));
    public static final StreamCodec<ByteBuf, RenameConnectorPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(ConnectorSettings.MAX_NAME).map(RenameConnectorPayload::new, RenameConnectorPayload::name);

    @Override
    public Type<RenameConnectorPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, RenameConnectorPayload::handle);
    }

    private static void handle(RenameConnectorPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof StructureMenu menu) {
            menu.rename(context.player(), payload.name());
        }
    }
}
