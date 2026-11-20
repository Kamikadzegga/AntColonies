package com.antcolonies.content.menu;

import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The Genetic Laboratory: six pages over one Queen slot. Each page that works on her has its own material and
 * output slots, shown only while that page is open.
 */
public class GeneticLabMenu extends BaseMenu {
    /** Buttons 0-9 select a trait. */
    public static final int BUTTON_UPGRADE = 10;
    public static final int BUTTON_REROLL = 11;
    public static final int BUTTON_MUTATE = 12;
    public static final int BUTTON_PURGE = 13;
    public static final int BUTTON_SAMPLE = 14;
    public static final int BUTTON_CANCEL = 15;
    /** Buttons 20-25 open a page. */
    public static final int BUTTON_TAB = 20;
    public static final int BUTTON_ANALYSE = 30;
    public static final int BUTTON_TRANSFORM = 31;
    public static final int BUTTON_PREPARE_SAMPLE = 32;
    public static final int BUTTON_STABILIZE = 33;
    public static final int BUTTON_RELEASE = 34;
    public static final int BUTTON_SCAN = 35;
    /** Buttons from 100 choose a species mutation; from 200, a gene to stabilize. */
    public static final int BUTTON_MUTATION = 100;
    public static final int BUTTON_GENE = 200;

    public static final int TAB_RESEARCH = 0;
    public static final int TAB_ANALYSIS = 1;
    public static final int TAB_MUTATION = 2;
    public static final int TAB_EDITING = 3;
    public static final int TAB_HYBRID = 4;
    public static final int TAB_STABILITY = 5;
    public static final int TABS = 6;

    public static final int WIDTH = 320;
    public static final int HEIGHT = 248;
    public static final int INVENTORY_X = 80;
    public static final int INVENTORY_Y = 164;
    /** Where the slots sit in the console (item corners). */
    public static final int QUEEN_X = 35;
    public static final int QUEEN_Y = 34;
    public static final int MATERIAL_X = 224;
    public static final int MATERIAL_Y = 96;
    public static final int TOOLS_Y = 131;
    public static final int UPGRADE_X = 10;
    public static final int CATALYST_X = 90;
    public static final int MUTATION_OUTPUT_X = 292;
    public static final int TUBE_X = 90;
    public static final int SAMPLE_OUTPUT_X = 130;
    public static final int SCAN_X = 90;

    private final GeneticLabBlockEntity lab;
    private final ContainerData data;
    private int tab = TAB_RESEARCH;

    public GeneticLabMenu(int containerId, Inventory inventory, GeneticLabBlockEntity lab) {
        this(containerId, inventory, lab, lab.data());
    }

    /** Empty Test Tubes are what Copy takes from the catalyst slot: shift-clicked ones go there, not to the materials. */
    @Override
    protected boolean quickMovesInto(int slot, ItemStack stack) {
        return !(slot >= 1 && slot <= GeneticLabBlockEntity.MATERIAL_SLOTS && stack.is(ModItems.TEST_TUBE.get()));
    }

    private GeneticLabMenu(int containerId, Inventory inventory, GeneticLabBlockEntity lab, ContainerData data) {
        super(ModMenus.GENETIC_LAB.get(), containerId);
        this.lab = lab;
        this.data = data;
        // The lab's own slots come first and in its order, so a menu slot's number is the lab's slot number.
        addSlot(new FilteredSlot(lab, GeneticLabBlockEntity.SLOT_QUEEN, QUEEN_X, QUEEN_Y, stack -> stack.is(ModItems.QUEEN_TEST_TUBE.get()), 1));
        for (int i = 0; i < GeneticLabBlockEntity.MATERIAL_SLOTS; i++) {
            addSlot(labSlot(GeneticLabBlockEntity.SLOT_MATERIAL + i, MATERIAL_X + i * 18, MATERIAL_Y, TAB_EDITING));
        }
        addSlot(labSlot(GeneticLabBlockEntity.SLOT_CATALYST, CATALYST_X, TOOLS_Y, TAB_EDITING));
        for (int i = 0; i < GeneticLabBlockEntity.MUTATION_SLOTS; i++) {
            addSlot(labSlot(GeneticLabBlockEntity.SLOT_MUTATION + i, MATERIAL_X + i * 18, MATERIAL_Y, TAB_MUTATION));
        }
        addSlot(labSlot(GeneticLabBlockEntity.SLOT_MUTATION_OUTPUT, MUTATION_OUTPUT_X, MATERIAL_Y, TAB_MUTATION));

        addSlot(labSlot(GeneticLabBlockEntity.SLOT_TUBE, TUBE_X, TOOLS_Y, TAB_HYBRID));
        addSlot(labSlot(GeneticLabBlockEntity.SLOT_SAMPLE_OUTPUT, SAMPLE_OUTPUT_X, TOOLS_Y, TAB_HYBRID));
        addSlot(labSlot(GeneticLabBlockEntity.SLOT_STABILIZER, MATERIAL_X, MATERIAL_Y, TAB_STABILITY));
        addSlot(labSlot(GeneticLabBlockEntity.SLOT_SCAN, SCAN_X, TOOLS_Y, TAB_RESEARCH));
        for (int i = 0; i < GeneticLabBlockEntity.UPGRADE_SLOTS; i++) {
            addUpgradeSlot(new FilteredSlot(lab.upgrades(), i, UPGRADE_X + i * 18, TOOLS_Y, stack -> lab.upgrades().canPlaceItem(0, stack), 1));
        }
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y);
        addDataSlots(WideData.slots(data));
    }

    /** One of the lab's slots that takes what the lab says it takes, shown on one page. */
    private FilteredSlot labSlot(int slot, int x, int y, int page) {
        return new FilteredSlot(lab, slot, x, y, stack -> lab.canPlaceItem(slot, stack), 64, () -> tab == page);
    }

    public static GeneticLabMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        GeneticLabBlockEntity lab = inventory.player.level().getBlockEntity(pos) instanceof GeneticLabBlockEntity be ? be
                : new GeneticLabBlockEntity(pos, ModBlocks.GENETIC_LAB.get().defaultBlockState());
        return new GeneticLabMenu(containerId, inventory, lab, new SimpleContainerData(GeneticLabBlockEntity.DATA_COUNT));
    }

    public GeneticLabBlockEntity lab() {
        return lab;
    }

    public int data(int id) {
        return data.get(id);
    }

    public int tab() {
        return tab;
    }

    /** Opens a page on this side; the screen also sends the page's button so the server's menu follows. */
    public void setTab(int tab) {
        this.tab = Math.clamp(tab, 0, TABS - 1);
    }

    public ItemStack queenStack() {
        return slots.get(GeneticLabBlockEntity.SLOT_QUEEN).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < Trait.values().length) {
            lab.selectTrait(id);
            return true;
        }
        if (id >= BUTTON_TAB && id < BUTTON_TAB + TABS) {
            setTab(id - BUTTON_TAB);
            return true;
        }
        var registries = player.level().registryAccess();
        if (id >= BUTTON_GENE && id < BUTTON_GENE + 64) {
            lab.selectGene(id - BUTTON_GENE);
            return true;
        }
        if (id >= BUTTON_MUTATION && id < BUTTON_MUTATION + 64) {
            lab.selectMutation(id - BUTTON_MUTATION, registries);
            return true;
        }
        switch (id) {
            case BUTTON_UPGRADE -> lab.start(GeneticLabBlockEntity.Operation.UPGRADE, registries, player);
            case BUTTON_REROLL -> lab.start(GeneticLabBlockEntity.Operation.REROLL, registries, player);
            case BUTTON_MUTATE -> lab.start(GeneticLabBlockEntity.Operation.MUTATE, registries, player);
            case BUTTON_PURGE -> lab.start(GeneticLabBlockEntity.Operation.PURGE, registries, player);
            case BUTTON_SAMPLE -> lab.start(GeneticLabBlockEntity.Operation.SAMPLE, registries, player);
            case BUTTON_CANCEL -> lab.cancel(0);
            case BUTTON_ANALYSE -> lab.analyse(player);
            case BUTTON_TRANSFORM -> lab.start(GeneticLabBlockEntity.Operation.TRANSFORM, registries, player);
            case BUTTON_PREPARE_SAMPLE -> lab.start(GeneticLabBlockEntity.Operation.PREPARE_SAMPLE, registries, player);
            case BUTTON_STABILIZE -> lab.start(GeneticLabBlockEntity.Operation.STABILIZE, registries, player);
            case BUTTON_RELEASE -> lab.release(registries);
            case BUTTON_SCAN -> lab.scan(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return lab.stillValid(player);
    }
}
