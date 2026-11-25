package com.antcolonies.companion.menu;

import com.antcolonies.AntColonies;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player to the server: the new name of the companion whose screen they have open. */
public record RenameCompanionPayload(String name) implements CustomPacketPayload {
    public static final Type<RenameCompanionPayload> TYPE = new Type<>(AntColonies.id("rename_companion"));
    public static final StreamCodec<ByteBuf, RenameCompanionPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(CompanionMenu.MAX_NAME).map(RenameCompanionPayload::new, RenameCompanionPayload::name);

    @Override
    public Type<RenameCompanionPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, RenameCompanionPayload::handle);
    }

    private static void handle(RenameCompanionPayload payload, IPayloadContext context) {
        // Only the ant whose screen this player has open, and only while that screen is theirs.
        if (context.player().containerMenu instanceof CompanionMenu menu) {
            menu.rename(context.player(), payload.name());
        }
    }
}
