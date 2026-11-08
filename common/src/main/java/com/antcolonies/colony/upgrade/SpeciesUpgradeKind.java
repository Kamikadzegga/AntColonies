package com.antcolonies.colony.upgrade;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The three upgrades every species has, made at the Upgrade Station from an Upgrade Base and the species' signature
 * material. They only work in Scavenging Sites of a colony of their species ("Diamond Extraction" does nothing for Gold
 * Ants). Each species' JSON-free names come from the language file ({@code upgrade.antcolonies.<species>.<kind>}).
 */
public enum SpeciesUpgradeKind implements StringRepresentable {
    /** More finds. */
    EXTRACTION("extraction"),
    /** Finds that have a processed form (nuggets, shards) come out processed, with a bonus. */
    PROCESSING("processing"),
    /** The site's workers eat less, and signature finds come more often. */
    EFFICIENCY("efficiency");

    public static final Codec<SpeciesUpgradeKind> CODEC = StringRepresentable.fromEnum(SpeciesUpgradeKind::values);
    public static final StreamCodec<ByteBuf, SpeciesUpgradeKind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], SpeciesUpgradeKind::ordinal);

    private final String name;

    SpeciesUpgradeKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
