package com.antcolonies.companion.menu;

import java.util.UUID;

import com.antcolonies.AntColonies;
import com.antcolonies.companion.CompanionRoster;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player to the server: call this one of my Guard Ants to me. The server checks that it is theirs. */
public record CallCompanionPayload(UUID id) implements CustomPacketPayload {
    public static final Type<CallCompanionPayload> TYPE = new Type<>(AntColonies.id("call_companion"));
    public static final StreamCodec<ByteBuf, CallCompanionPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(CallCompanionPayload::new, CallCompanionPayload::id);

    @Override
    public Type<CallCompanionPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, CallCompanionPayload::handle);
    }

    private static void handle(CallCompanionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CompanionRoster roster = CompanionRoster.of(player.level().getServer());
            roster.call(player, payload.id());
            roster.show(player);
        }
    }
}
