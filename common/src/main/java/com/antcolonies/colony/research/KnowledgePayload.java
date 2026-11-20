package com.antcolonies.colony.research;

import java.util.List;
import java.util.Optional;

import com.antcolonies.AntColonies;
import com.antcolonies.client.ClientHooks;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to one client: everything that player knows, their own part and their team's part apart.
 *
 * @param team empty while the player is in no team
 * @param news what was just discovered (for the notification); empty on a plain refresh
 */
public record KnowledgePayload(Knowledge personal, Optional<Knowledge> team, List<News> news) implements CustomPacketPayload {
    public static final Type<KnowledgePayload> TYPE = new Type<>(AntColonies.id("knowledge"));

    /** One new discovery. */
    public record News(Knowledge.Kind kind, Identifier id) {
        static final StreamCodec<ByteBuf, News> STREAM_CODEC = StreamCodec.composite(
                Knowledge.Kind.STREAM_CODEC, News::kind,
                Identifier.STREAM_CODEC, News::id,
                News::new);
    }

    public static final StreamCodec<ByteBuf, KnowledgePayload> STREAM_CODEC = StreamCodec.composite(
            Knowledge.STREAM_CODEC, KnowledgePayload::personal,
            ByteBufCodecs.optional(Knowledge.STREAM_CODEC), KnowledgePayload::team,
            News.STREAM_CODEC.apply(ByteBufCodecs.list(64)), KnowledgePayload::news,
            KnowledgePayload::new);

    @Override
    public Type<KnowledgePayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, KnowledgePayload::handle);
    }

    private static void handle(KnowledgePayload payload, IPayloadContext context) {
        ClientHooks.knowledge(payload);
    }
}
