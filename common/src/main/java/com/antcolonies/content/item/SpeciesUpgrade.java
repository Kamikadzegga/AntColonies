package com.antcolonies.content.item;

import com.antcolonies.colony.upgrade.SpeciesUpgradeKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Which species a Species Upgrade is for, which of its three upgrades, and its tier (1-3). */
public record SpeciesUpgrade(Identifier species, SpeciesUpgradeKind kind, int tier) {
    public static final Codec<SpeciesUpgrade> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("species").forGetter(SpeciesUpgrade::species),
            SpeciesUpgradeKind.CODEC.fieldOf("kind").forGetter(SpeciesUpgrade::kind),
            Codec.intRange(1, 3).optionalFieldOf("tier", 1).forGetter(SpeciesUpgrade::tier)
    ).apply(i, SpeciesUpgrade::new));
    public static final StreamCodec<ByteBuf, SpeciesUpgrade> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SpeciesUpgrade::species,
            SpeciesUpgradeKind.STREAM_CODEC, SpeciesUpgrade::kind,
            ByteBufCodecs.VAR_INT, SpeciesUpgrade::tier,
            SpeciesUpgrade::new);

    public String translationKey() {
        return "upgrade." + species.getNamespace() + "." + species.getPath() + "." + kind.getSerializedName();
    }
}
