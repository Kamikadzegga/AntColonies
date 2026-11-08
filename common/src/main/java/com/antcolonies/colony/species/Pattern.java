package com.antcolonies.colony.species;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** The marking over an ant's gaster: each is one grey texture layer, tinted with the species' accent color. */
public enum Pattern implements StringRepresentable {
    PLAIN("plain"),
    STRIPED("striped"),
    BANDED("banded"),
    SPECKLED("speckled"),
    FACETED("faceted"),
    METALLIC("metallic");

    public static final Codec<Pattern> CODEC = StringRepresentable.fromEnum(Pattern::values);

    private final String name;

    Pattern(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
