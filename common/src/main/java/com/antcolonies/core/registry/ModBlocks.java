package com.antcolonies.core.registry;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.antcolonies.AntColonies;
import com.antcolonies.content.block.AntTubeBlock;
import com.antcolonies.content.block.GeneticLabControllerBlock;
import com.antcolonies.content.block.LabPartBlock;
import com.antcolonies.content.block.StructureBlock;
import com.antcolonies.content.block.TerrariumBlock;
import com.antcolonies.content.block.TunnelBlock;
import com.antcolonies.content.block.UpgradeStationBlock;
import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.colony.model.StructureKind;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AntColonies.MODID);

    public static final DeferredBlock<TerrariumBlock> TERRARIUM = BLOCKS.registerBlock("terrarium", TerrariumBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.GLASS).noOcclusion()
                    .isViewBlocking((state, level, pos, near) -> false).isSuffocating((state, level, pos) -> false)
                    .isRedstoneConductor((state, level, pos) -> false));

    public static final DeferredBlock<AntTubeBlock> ANT_TUBE = BLOCKS.registerBlock("ant_tube", AntTubeBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(0.3F).sound(SoundType.GLASS).noOcclusion()
                    .isViewBlocking((state, level, pos, near) -> false).isSuffocating((state, level, pos) -> false)
                    .isRedstoneConductor((state, level, pos) -> false));

    public static final DeferredBlock<TunnelBlock> ANT_TUNNEL = BLOCKS.registerBlock("ant_tunnel", TunnelBlock::new,
            p -> p.mapColor(MapColor.DIRT).strength(0.6F).sound(SoundType.ROOTED_DIRT));

    /** The colony structures, in {@link StructureKind} order. */
    public static final Map<StructureKind, DeferredBlock<StructureBlock>> STRUCTURES = new EnumMap<>(StructureKind.class);

    static {
        for (StructureKind kind : StructureKind.values()) {
            STRUCTURES.put(kind, BLOCKS.registerBlock(kind.getSerializedName(), p -> new StructureBlock(kind, p), p -> structureProperties(kind, p)));
        }
    }

    private static BlockBehaviour.Properties structureProperties(StructureKind kind, BlockBehaviour.Properties p) {
        return switch (kind) {
            case RESOURCE_PILE, FOOD_STORAGE -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion();
            case FARMER_GARDEN -> p.mapColor(MapColor.COLOR_GREEN).strength(0.8F).sound(SoundType.ROOTED_DIRT).noOcclusion();
            case FIGHTING_SITE -> p.mapColor(MapColor.STONE).strength(1.5F).sound(SoundType.BONE_BLOCK).noOcclusion();
            case SCAVENGING_SITE -> p.mapColor(MapColor.DIRT).strength(0.8F).sound(SoundType.ROOTED_DIRT).noOcclusion();
            case COLONY_CONNECTOR -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.GLASS).noOcclusion();
            default -> p.mapColor(MapColor.DIRT).strength(1.2F).sound(SoundType.PACKED_MUD).noOcclusion();
        };
    }

    public static final DeferredBlock<UpgradeStationBlock> UPGRADE_STATION = BLOCKS.registerBlock("upgrade_station", UpgradeStationBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion());

    public static final DeferredBlock<com.antcolonies.content.block.GeneratorBlock> GENERATOR = BLOCKS.registerBlock("generator", com.antcolonies.content.block.GeneratorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(com.antcolonies.content.block.GeneratorBlock.LIT) ? 10 : 0));

    public static final DeferredBlock<GeneticLabControllerBlock> GENETIC_LAB_CONTROLLER = BLOCKS.registerBlock("genetic_lab_controller",
            GeneticLabControllerBlock::new, p -> labProperties(p));

    public static final Map<LabPartBlock.Part, DeferredBlock<LabPartBlock>> LAB_PARTS = new EnumMap<>(LabPartBlock.Part.class);

    static {
        for (LabPartBlock.Part part : LabPartBlock.Part.values()) {
            LAB_PARTS.put(part, BLOCKS.registerBlock(part.id(), p -> new LabPartBlock(part, p), ModBlocks::labProperties));
        }
    }

    private static BlockBehaviour.Properties labProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.COPPER).noOcclusion().requiresCorrectToolForDrops()
                .lightLevel(state -> state.hasProperty(LabPartBlock.FORMED) && state.getValue(LabPartBlock.FORMED) ? 9 : 2);
    }

    /** One wild nest per species that lives in the wild, by species path. */
    public static final Map<String, DeferredBlock<WildNestBlock>> NESTS = new LinkedHashMap<>();

    static {
        for (String species : new String[] {"common", "wood", "stone", "snow", "desert", "farmer", "jungle", "swamp", "savanna", "shore", "blossom",
                "pale", "badlands"}) {
            NESTS.put(species, BLOCKS.registerBlock(species + "_ant_nest", p -> new WildNestBlock(species, p),
                    p -> p.mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.ROOTED_DIRT).randomTicks()));
        }
    }

    private ModBlocks() {}
}
