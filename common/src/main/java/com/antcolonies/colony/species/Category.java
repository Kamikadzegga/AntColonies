package com.antcolonies.colony.species;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** What kind of species it is: decides its place in JEI and the colony specialization it suits. */
public enum Category implements StringRepresentable {
    BASE("base"),
    FARMER("farmer"),
    MINERAL("mineral"),
    HYBRID("hybrid"),
    ENDGAME("endgame"),
    /** Hunters: their workers hunt mobs at a Hunting Ground. */
    HUNTER("hunter");

    public static final Codec<Category> CODEC = StringRepresentable.fromEnum(Category::values);

    private final String name;

    Category(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
