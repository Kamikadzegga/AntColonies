package com.antcolonies.companion.menu;

import java.util.List;
import java.util.UUID;

import com.antcolonies.AntColonies;
import com.antcolonies.client.ClientHooks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to one client: that player's Guard Ants out in the world, for the roster's screen.
 *
 * @param most how many a player may have out (0: any number)
 */
public record RosterPayload(List<Row> rows, int most) implements CustomPacketPayload {
    public static final Type<RosterPayload> TYPE = new Type<>(AntColonies.id("companion_roster"));

    /**
     * One ant.
     *
     * @param name   the name its owner gave it ("" for none)
     * @param called whether it was called and is on its way
     */
    public record Row(UUID id, String name, Identifier species, int level, Identifier dimension, BlockPos pos, boolean called) {
        static final StreamCodec<ByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Row::id,
                ByteBufCodecs.STRING_UTF8, Row::name,
                Identifier.STREAM_CODEC, Row::species,
                ByteBufCodecs.VAR_INT, Row::level,
                Identifier.STREAM_CODEC, Row::dimension,
                BlockPos.STREAM_CODEC, Row::pos,
                ByteBufCodecs.BOOL, Row::called,
                Row::new);
    }

    public static final StreamCodec<ByteBuf, RosterPayload> STREAM_CODEC = StreamCodec.composite(
            Row.STREAM_CODEC.apply(ByteBufCodecs.list(4096)), RosterPayload::rows,
            ByteBufCodecs.VAR_INT, RosterPayload::most,
            RosterPayload::new);

    @Override
    public Type<RosterPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, RosterPayload::handle);
    }

    private static void handle(RosterPayload payload, IPayloadContext context) {
        ClientHooks.roster(payload);
    }
}
