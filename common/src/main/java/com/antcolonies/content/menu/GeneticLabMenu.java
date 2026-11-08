package com.antcolonies.content.menu;

import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.colony.genetics.Trait;
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

/** The Genetic Laboratory: Queen, materials, fuel and catalyst slots; a button per trait and per operation. */
public class GeneticLabMenu extends BaseMenu {
    /** Buttons 0-9 select a trait. */
    public static final int BUTTON_UPGRADE = 10;
    public static final int BUTTON_REROLL = 11;
    public static final int BUTTON_MUTATE = 12;
    public static final int BUTTON_PURGE = 13;
    public static final int BUTTON_SAMPLE = 14;
    public static final int BUTTON_CANCEL = 15;
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

    private final GeneticLabBlockEntity lab;
    private final ContainerData data;

    public GeneticLabMenu(int containerId, Inventory inventory, GeneticLabBlockEntity lab) {
        this(containerId, inventory, lab, lab.data());
    }

    private GeneticLabMenu(int containerId, Inventory inventory, GeneticLabBlockEntity lab, ContainerData data) {
        super(ModMenus.GENETIC_LAB.get(), containerId);
        this.lab = lab;
        this.data = data;
        addSlot(new FilteredSlot(lab, GeneticLabBlockEntity.SLOT_QUEEN, QUEEN_X, QUEEN_Y, stack -> stack.is(ModItems.QUEEN_TEST_TUBE.get()), 1));
        for (int i = 0; i < GeneticLabBlockEntity.MATERIAL_SLOTS; i++) {
            addSlot(new FilteredSlot(lab, GeneticLabBlockEntity.SLOT_MATERIAL + i, MATERIAL_X + i * 18, MATERIAL_Y, stack -> true, 64));
        }
        addSlot(new FilteredSlot(lab, GeneticLabBlockEntity.SLOT_CATALYST, CATALYST_X, TOOLS_Y, stack -> true, 64));
        for (int i = 0; i < GeneticLabBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new FilteredSlot(lab.upgrades(), i, UPGRADE_X + i * 18, TOOLS_Y, stack -> lab.upgrades().canPlaceItem(0, stack), 1));
        }
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y);
        addDataSlots(WideData.slots(data));
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

    public ItemStack queenStack() {
        return slots.get(GeneticLabBlockEntity.SLOT_QUEEN).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < Trait.values().length) {
            lab.selectTrait(id);
            return true;
        }
        var registries = player.level().registryAccess();
        switch (id) {
            case BUTTON_UPGRADE -> lab.start(GeneticLabBlockEntity.Operation.UPGRADE, registries);
            case BUTTON_REROLL -> lab.start(GeneticLabBlockEntity.Operation.REROLL, registries);
            case BUTTON_MUTATE -> lab.start(GeneticLabBlockEntity.Operation.MUTATE, registries);
            case BUTTON_PURGE -> lab.start(GeneticLabBlockEntity.Operation.PURGE, registries);
            case BUTTON_SAMPLE -> lab.start(GeneticLabBlockEntity.Operation.SAMPLE, registries);
            case BUTTON_CANCEL -> lab.cancel(0);
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
