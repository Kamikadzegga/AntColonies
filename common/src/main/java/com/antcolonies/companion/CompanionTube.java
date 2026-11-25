package com.antcolonies.companion;

import com.antcolonies.core.registry.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/**
 * A whole companion in a tube.
 *
 * @param entity the saved mob, exactly as a chunk would save it
 * @param data   its companion data, for the tooltip
 * @param name   its name as a Guard
 */
public record CompanionTube(CustomData entity, CompanionData data, Component name, float health, float maxHealth) {
    public static final Codec<CompanionTube> CODEC = RecordCodecBuilder.create(i -> i.group(
            CustomData.CODEC.fieldOf("entity").forGetter(CompanionTube::entity),
            CompanionData.CODEC.fieldOf("data").forGetter(CompanionTube::data),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(CompanionTube::name),
            Codec.FLOAT.optionalFieldOf("health", 0F).forGetter(CompanionTube::health),
            Codec.FLOAT.optionalFieldOf("max_health", 0F).forGetter(CompanionTube::maxHealth)
    ).apply(i, CompanionTube::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionTube> STREAM_CODEC = StreamCodec.composite(
            CustomData.STREAM_CODEC, CompanionTube::entity,
            CompanionData.STREAM_CODEC, CompanionTube::data,
            ComponentSerialization.STREAM_CODEC, CompanionTube::name,
            ByteBufCodecs.FLOAT, CompanionTube::health,
            ByteBufCodecs.FLOAT, CompanionTube::maxHealth,
            CompanionTube::new);

    /** The same companion without its owner: gone from its data and from the saved mob. */
    public CompanionTube freed() {
        CompoundTag tag = entity.copyTag();
        tag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).flatMap(attachments -> attachments.getCompound(ModAttachments.COMPANION_ID.toString()))
                .ifPresent(companion -> companion.remove("owner"));
        return new CompanionTube(CustomData.of(tag), data.withoutOwner(), name, health, maxHealth);
    }
}
