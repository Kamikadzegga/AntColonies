package com.antcolonies.content.entity;

import com.antcolonies.colony.species.Caste;

import net.minecraft.resources.Identifier;

/** An ant entity the ant renderer can draw: wild ants and Guard Ants. */
public interface AntLike {
    Identifier species();

    Caste caste();

    /** How much bigger than a normal ant of its caste it is drawn. */
    default float renderScale() {
        return 1.0F;
    }

    /** Sitting (a Guard Ant told to stay): drawn crouched and still. */
    default boolean sitting() {
        return false;
    }
}
