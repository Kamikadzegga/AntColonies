package com.antcolonies.core.registry;

import com.antcolonies.AntColonies;
import com.antcolonies.content.worldgen.AnthillPiece;
import com.antcolonies.content.worldgen.EndAnthill;
import com.antcolonies.content.worldgen.GiantAnthill;
import com.antcolonies.content.worldgen.NestGround;
import com.antcolonies.content.worldgen.NetherAnthill;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The anthills' structure types and their piece, and the wild nests' placement. Where and how often they generate is data
 * (worldgen files and biome tags).
 */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, AntColonies.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, AntColonies.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<GiantAnthill>> GIANT_ANTHILL =
            STRUCTURE_TYPES.register("giant_anthill", () -> () -> GiantAnthill.CODEC);
    /** The same nest dug into the Nether's floor (its pieces are the giant anthill's, with a Nether flag). */
    public static final DeferredHolder<StructureType<?>, StructureType<NetherAnthill>> NETHER_ANTHILL =
            STRUCTURE_TYPES.register("nether_anthill", () -> () -> NetherAnthill.CODEC);
    public static final DeferredHolder<StructureType<?>, StructureType<EndAnthill>> END_ANTHILL =
            STRUCTURE_TYPES.register("end_anthill", () -> () -> EndAnthill.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> ANTHILL_PIECE =
            STRUCTURE_PIECES.register("giant_anthill", () -> (context, tag) -> new AnthillPiece(tag));

    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, AntColonies.MODID);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<NestGround>> NEST_GROUND =
            PLACEMENT_MODIFIERS.register("nest_ground", () -> () -> NestGround.CODEC);

    private ModStructures() {}
}
