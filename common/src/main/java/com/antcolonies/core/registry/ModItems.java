package com.antcolonies.core.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.antcolonies.AntColonies;
import com.antcolonies.content.block.TerrariumBlock;
import com.antcolonies.content.item.AntTestTubeItem;
import com.antcolonies.content.item.ChitinGear;
import com.antcolonies.content.item.GuardAntItem;
import com.antcolonies.content.item.ColonyScannerItem;
import com.antcolonies.content.item.DescribedItem;
import com.antcolonies.content.item.GeneticAnalyzerItem;
import com.antcolonies.content.item.IdentificationBookItem;
import com.antcolonies.content.item.SpeciesItem;
import com.antcolonies.content.item.SpeciesUpgradeItem;
import com.antcolonies.content.item.TweezersItem;
import com.antcolonies.content.item.UpgradeItem;
import com.antcolonies.colony.upgrade.UpgradeType;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AntColonies.MODID);

    // --- Catching and carrying ants ---
    public static final DeferredItem<TweezersItem> TWEEZERS = ITEMS.registerItem("tweezers", TweezersItem::new, p -> p.durability(256));
    public static final DeferredItem<DescribedItem> TEST_TUBE = ITEMS.registerItem("test_tube", DescribedItem::new);
    public static final DeferredItem<AntTestTubeItem> ANT_TEST_TUBE = ITEMS.registerItem("ant_test_tube",
            p -> new AntTestTubeItem(AntTestTubeItem.Contents.ANTS, p), p -> p.stacksTo(1));
    public static final DeferredItem<AntTestTubeItem> QUEEN_TEST_TUBE = ITEMS.registerItem("queen_test_tube",
            p -> new AntTestTubeItem(AntTestTubeItem.Contents.QUEEN, p), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<AntTestTubeItem> GENETIC_SAMPLE = ITEMS.registerItem("genetic_sample",
            p -> new AntTestTubeItem(AntTestTubeItem.Contents.SAMPLE, p), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<SpeciesItem> ANT_EGG = ITEMS.registerItem("ant_egg", SpeciesItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<SpeciesItem> ANT_LARVA = ITEMS.registerItem("ant_larva", SpeciesItem::new, p -> p.stacksTo(16));

    // --- Food ---
    public static final DeferredItem<DescribedItem> ANT_FOOD = ITEMS.registerItem("ant_food", DescribedItem::new);
    public static final DeferredItem<DescribedItem> MINERAL_ANT_FOOD = ITEMS.registerItem("mineral_ant_food", DescribedItem::new);
    public static final DeferredItem<DescribedItem> CRYSTAL_ANT_FOOD = ITEMS.registerItem("crystal_ant_food", DescribedItem::new,
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<DescribedItem> NETHER_ANT_FOOD = ITEMS.registerItem("nether_ant_food", DescribedItem::new,
            p -> p.rarity(Rarity.RARE));

    // --- Breeding, evolution and genetics materials ---
    public static final DeferredItem<DescribedItem> ROYAL_JELLY = ITEMS.registerItem("royal_jelly", DescribedItem::new);
    public static final DeferredItem<DescribedItem> NUPTIAL_PHEROMONE = ITEMS.registerItem("nuptial_pheromone", DescribedItem::new);
    public static final DeferredItem<DescribedItem> EVOLUTION_CATALYST = ITEMS.registerItem("evolution_catalyst", DescribedItem::new,
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<DescribedItem> REROLL_SERUM = ITEMS.registerItem("reroll_serum", DescribedItem::new,
            p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<DescribedItem> CHITIN_PLATE = ITEMS.registerItem("chitin_plate", DescribedItem::new);
    public static final DeferredItem<DescribedItem> UPGRADE_BASE = ITEMS.registerItem("upgrade_base", DescribedItem::new);
    public static final DeferredItem<DescribedItem> GENETIC_CIRCUIT = ITEMS.registerItem("genetic_circuit", DescribedItem::new);
    public static final DeferredItem<DescribedItem> CRYSTAL_CORE = ITEMS.registerItem("crystal_core", DescribedItem::new,
            p -> p.rarity(Rarity.UNCOMMON));
    /** What Diamond and Emerald Ants mostly find: nine make a gem, so gem output stays slow and steady. */
    public static final DeferredItem<DescribedItem> DIAMOND_FRAGMENT = ITEMS.registerItem("diamond_fragment", DescribedItem::new);
    public static final DeferredItem<DescribedItem> EMERALD_FRAGMENT = ITEMS.registerItem("emerald_fragment", DescribedItem::new);

    // --- Tools ---
    public static final DeferredItem<IdentificationBookItem> ANT_IDENTIFICATION_BOOK = ITEMS.registerItem("ant_identification_book",
            IdentificationBookItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ColonyScannerItem> COLONY_SCANNER = ITEMS.registerItem("colony_scanner", ColonyScannerItem::new,
            p -> p.stacksTo(1));
    public static final DeferredItem<GeneticAnalyzerItem> GENETIC_ANALYZER = ITEMS.registerItem("genetic_analyzer", GeneticAnalyzerItem::new,
            p -> p.stacksTo(1));

    // --- Upgrades ---
    public static final DeferredItem<SpeciesUpgradeItem> SPECIES_UPGRADE = ITEMS.registerItem("species_upgrade", SpeciesUpgradeItem::new,
            p -> p.stacksTo(1));
    public static final Map<UpgradeType, List<DeferredItem<UpgradeItem>>> UPGRADES = new EnumMap<>(UpgradeType.class);

    static {
        for (UpgradeType type : UpgradeType.values()) {
            List<DeferredItem<UpgradeItem>> tiers = new ArrayList<>();
            for (int tier = 1; tier <= UpgradeType.MAX_TIER; tier++) {
                int t = tier;
                tiers.add(ITEMS.registerItem(type.itemName(tier), p -> new UpgradeItem(type, t, p),
                        p -> p.stacksTo(16).rarity(t == 3 ? Rarity.RARE : t == 2 ? Rarity.UNCOMMON : Rarity.COMMON)));
            }
            UPGRADES.put(type, List.copyOf(tiers));
        }
    }

    public static Item upgrade(UpgradeType type, int tier) {
        return UPGRADES.get(type).get(tier - 1).get();
    }

    // --- Block items ---
    public static final DeferredItem<BlockItem> TERRARIUM = ITEMS.registerItem("terrarium",
            p -> new BlockItem(ModBlocks.TERRARIUM.get(), p) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                            TooltipFlag flag) {
                    tooltip.accept(Component.translatable("tooltip.antcolonies.terrarium").withStyle(net.minecraft.ChatFormatting.GRAY));
                    TerrariumBlock.describeColony(stack, tooltip);
                }
            }, p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> ANT_TUBE = block(ModBlocks.ANT_TUBE);
    public static final DeferredItem<BlockItem> ANT_TUNNEL = block(ModBlocks.ANT_TUNNEL);

    static {
        ModBlocks.STRUCTURES.values().forEach(ModItems::described);
    }

    public static final DeferredItem<BlockItem> UPGRADE_STATION = described(ModBlocks.UPGRADE_STATION);
    public static final DeferredItem<BlockItem> GENETIC_LAB_CONTROLLER = described(ModBlocks.GENETIC_LAB_CONTROLLER);
    public static final DeferredItem<BlockItem> GENERATOR = described(ModBlocks.GENERATOR);

    static {
        ModBlocks.LAB_PARTS.values().forEach(ModItems::described);
        ModBlocks.NESTS.values().forEach(ModItems::described);
    }

    public static final DeferredItem<SpawnEggItem> WILD_ANT_SPAWN_EGG = ITEMS.registerItem("wild_ant_spawn_egg", SpawnEggItem::new,
            p -> p.spawnEgg(ModEntities.WILD_ANT.get()));

    // --- Guard Ants ---
    public static final DeferredItem<DescribedItem> RALLY_PHEROMONE = ITEMS.registerItem("rally_pheromone", DescribedItem::new);
    public static final DeferredItem<GuardAntItem> GUARD_ANT = ITEMS.registerItem("guard_ant", GuardAntItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    // --- Chitin gear ---
    public static final DeferredItem<ChitinGear.Piece> CHITIN_HELMET = armor("chitin_helmet", ArmorType.HELMET);
    public static final DeferredItem<ChitinGear.Piece> CHITIN_CHESTPLATE = armor("chitin_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<ChitinGear.Piece> CHITIN_LEGGINGS = armor("chitin_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<ChitinGear.Piece> CHITIN_BOOTS = armor("chitin_boots", ArmorType.BOOTS);
    public static final DeferredItem<ChitinGear.Piece> CHITIN_SWORD = ITEMS.registerItem("chitin_sword", p -> new ChitinGear.Piece(false, p),
            p -> p.sword(ChitinGear.TOOLS, 3.0F, -2.4F));
    public static final DeferredItem<ChitinGear.Piece> CHITIN_PICKAXE = ITEMS.registerItem("chitin_pickaxe", p -> new ChitinGear.Piece(false, p),
            p -> p.pickaxe(ChitinGear.TOOLS, 1.0F, -2.8F));
    public static final DeferredItem<ChitinGear.Piece> CHITIN_AXE = ITEMS.registerItem("chitin_axe", p -> new ChitinGear.Piece(false, p),
            p -> p.axe(ChitinGear.TOOLS, 6.0F, -3.1F));
    public static final DeferredItem<ChitinGear.Piece> CHITIN_SHOVEL = ITEMS.registerItem("chitin_shovel", p -> new ChitinGear.Piece(false, p),
            p -> p.shovel(ChitinGear.TOOLS, 1.5F, -3.0F));
    public static final DeferredItem<ChitinGear.Piece> CHITIN_HOE = ITEMS.registerItem("chitin_hoe", p -> new ChitinGear.Piece(false, p),
            p -> p.hoe(ChitinGear.TOOLS, -2.0F, -1.0F));

    private static DeferredItem<ChitinGear.Piece> armor(String name, ArmorType type) {
        return ITEMS.registerItem(name, p -> new ChitinGear.Piece(true, p), p -> p.humanoidArmor(ChitinGear.ARMOR, type));
    }

    private static DeferredItem<BlockItem> block(DeferredBlock<?> block) {
        return ITEMS.registerSimpleBlockItem(block);
    }

    /** A block item with a line saying what the block is for ({@code tooltip.antcolonies.<block>}). */
    private static DeferredItem<BlockItem> described(DeferredBlock<?> block) {
        String key = "tooltip.antcolonies." + block.getId().getPath();
        return ITEMS.registerItem(block.getId().getPath(), p -> new BlockItem(block.get(), p) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
                tooltip.accept(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }, p -> p.useBlockDescriptionPrefix());
    }

    private ModItems() {}
}
