package com.antcolonies.core.registry;

import com.antcolonies.AntColonies;
import com.antcolonies.content.worldgen.AnthillPiece;
import com.antcolonies.content.worldgen.GiantAnthill;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The anthills' structure types and their piece. Where and how often they generate is data (worldgen files and biome tags). */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, AntColonies.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, AntColonies.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<GiantAnthill>> GIANT_ANTHILL =
            STRUCTURE_TYPES.register("giant_anthill", () -> () -> GiantAnthill.CODEC);
    /** The same nest dug into the Nether's floor (its pieces are the giant anthill's, with a Nether flag). */
    public static final DeferredHolder<StructureType<?>, StructureType<com.antcolonies.content.worldgen.NetherAnthill>> NETHER_ANTHILL =
            STRUCTURE_TYPES.register("nether_anthill", () -> () -> com.antcolonies.content.worldgen.NetherAnthill.CODEC);
    /** Chorus Ants' nests in the End's outer islands. */
    public static final DeferredHolder<StructureType<?>, StructureType<com.antcolonies.content.worldgen.EndAnthill>> END_ANTHILL =
            STRUCTURE_TYPES.register("end_anthill", () -> () -> com.antcolonies.content.worldgen.EndAnthill.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> ANTHILL_PIECE =
            STRUCTURE_PIECES.register("giant_anthill", () -> (context, tag) -> new AnthillPiece(tag));

    private ModStructures() {}
}
