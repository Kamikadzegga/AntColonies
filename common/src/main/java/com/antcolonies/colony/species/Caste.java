package com.antcolonies.colony.species;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The three kinds of ant drawn and caught: each has its own body shape. */
public enum Caste implements StringRepresentable {
    WORKER("worker"),
    SOLDIER("soldier"),
    QUEEN("queen");

    public static final Codec<Caste> CODEC = StringRepresentable.fromEnum(Caste::values);
    public static final StreamCodec<ByteBuf, Caste> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Caste::ordinal);

    private final String name;

    Caste(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
