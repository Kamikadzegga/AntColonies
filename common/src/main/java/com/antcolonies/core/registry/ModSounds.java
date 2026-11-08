package com.antcolonies.core.registry;

import java.util.function.Supplier;

import com.antcolonies.AntColonies;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's sound events; sounds.json maps each to (pitched) vanilla sounds, so resource packs can replace them. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, AntColonies.MODID);

    public static final Supplier<SoundEvent> ANT_STEP = register("entity.ant.step");
    public static final Supplier<SoundEvent> ANT_HURT = register("entity.ant.hurt");
    public static final Supplier<SoundEvent> SOLDIER_BITE = register("entity.ant.bite");
    public static final Supplier<SoundEvent> COLONY_AMBIENCE = register("block.terrarium.ambience");
    public static final Supplier<SoundEvent> FOOD_COLLECTED = register("block.colony.food");
    public static final Supplier<SoundEvent> QUEEN_ACTIVITY = register("block.terrarium.queen");
    public static final Supplier<SoundEvent> EGG_LAID = register("block.terrarium.egg");
    public static final Supplier<SoundEvent> MACHINE_ON = register("block.colony.activate");
    public static final Supplier<SoundEvent> SOLDIERS = register("block.fighting_site.battle");
    public static final Supplier<SoundEvent> EVOLUTION = register("block.terrarium.evolution");
    public static final Supplier<SoundEvent> LAB_HUM = register("block.genetic_lab.hum");
    public static final Supplier<SoundEvent> LAB_COMPLETE = register("block.genetic_lab.complete");
    public static final Supplier<SoundEvent> CAPTURE = register("item.tweezers.capture");

    private static Supplier<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(AntColonies.id(name)));
    }

    private ModSounds() {}
}
