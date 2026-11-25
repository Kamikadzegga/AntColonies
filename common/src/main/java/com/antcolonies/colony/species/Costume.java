package com.antcolonies.colony.species;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * What a species wears over its colours: horns, a hat, a coat. Each costume is a set of small parts on the ant model and
 * a painted layer, in its own colours (see the client's CostumeParts).
 */
public enum Costume implements StringRepresentable {
    COW("cow"),
    CHICKEN("chicken"),
    SHEEP("sheep"),
    BAKER("baker"),
    BOOKWORM("bookworm"),
    PARTY("party"),
    PIRATE("pirate"),
    PIG("pig"),
    RABBIT("rabbit"),
    FISHER("fisher"),
    CARPENTER("carpenter"),
    LEAFCUTTER("leafcutter"),
    WEAVER("weaver");

    public static final Codec<Costume> CODEC = StringRepresentable.fromEnum(Costume::values);

    private final String name;

    Costume(String name) {
        this.name = name;
    }

    /** Whether it is a hat: what a companion wears on its head takes its place. */
    public boolean hat() {
        return this == BAKER || this == PARTY || this == PIRATE || this == FISHER || this == CARPENTER || this == LEAFCUTTER || this == WEAVER;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
