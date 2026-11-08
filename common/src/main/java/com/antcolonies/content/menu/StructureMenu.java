package com.antcolonies.content.menu;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModDataMaps;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Any colony structure's GUI: its status and link, for job sites the ants it has and wants (+/- buttons), its own slots
 * (Resource Pile, Food Storage, Queen Chamber) and its upgrade slots on the right.
 */
public class StructureMenu extends BaseMenu {
    public static final int LESS = 0;
    public static final int MORE = 1;
    public static final int LESS_10 = 2;
    public static final int MORE_10 = 3;
    public static final int AUTO = 4;
    public static final int LESS_100 = 5;
    public static final int MORE_100 = 6;
    public static final int UPGRADE_X = 184;

    private final StructureBlockEntity structure;
    private final ContainerData data;

    public StructureMenu(int containerId, Inventory inventory, StructureBlockEntity structure) {
        this(containerId, inventory, structure, structure.data());
    }

    private StructureMenu(int containerId, Inventory inventory, StructureBlockEntity structure, ContainerData data) {
        super(ModMenus.STRUCTURE.get(), containerId);
        this.structure = structure;
        this.data = data;
        StructureKind kind = structure.kind();
        switch (kind) {
            case RESOURCE_PILE -> {
                for (int slot = 0; slot < kind.slots(); slot++) {
                    int s = slot;
                    addSlot(new FilteredSlot(structure, slot, 8 + (slot % 9) * 18, 18 + (slot / 9) * 18, stack -> true, 64,
                            () -> s < data.get(StructureBlockEntity.DATA_UNLOCKED)));
                }
            }
            case FOOD_STORAGE -> {
                for (int slot = 0; slot < kind.slots(); slot++) {
                    addSlot(new FilteredSlot(structure, slot, 8 + slot * 18, 52, stack -> ModDataMaps.food(stack) != null, 64));
                }
            }
            case QUEEN_CHAMBER -> {
                for (int slot = 0; slot < StructureKind.QUEEN_CHAMBER_MATERIALS; slot++) {
                    addSlot(new FilteredSlot(structure, slot, 8 + slot * 18, 52, stack -> true, 64));
                }
                addSlot(new FilteredSlot(structure, StructureKind.QUEEN_CHAMBER_TUBE, 98, 52, stack -> stack.is(ModItems.TEST_TUBE.get()), 64));
                addSlot(new FilteredSlot(structure, StructureKind.QUEEN_CHAMBER_SAMPLE, 116, 52, stack -> stack.is(ModItems.GENETIC_SAMPLE.get()), 1));
                addSlot(new FilteredSlot(structure, StructureKind.QUEEN_CHAMBER_OUTPUT, 152, 52, stack -> false, 1));
            }
            default -> {
            }
        }
        for (int slot = 0; slot < kind.upgradeSlots(); slot++) {
            addSlot(new FilteredSlot(structure.upgrades(), slot, UPGRADE_X, 18 + slot * 18, structure::acceptsUpgrade, 1));
        }
        addPlayerInventory(inventory, 8, inventoryY(kind));
        addDataSlots(WideData.slots(data));
    }

    public static int inventoryY(StructureKind kind) {
        return kind == StructureKind.RESOURCE_PILE ? 152 : 84;
    }

    public static StructureMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        StructureBlockEntity structure = inventory.player.level().getBlockEntity(pos) instanceof StructureBlockEntity be ? be
                : new StructureBlockEntity(pos, ModBlocks.STRUCTURES.get(StructureKind.RESOURCE_PILE).get().defaultBlockState());
        return new StructureMenu(containerId, inventory, structure, new SimpleContainerData(StructureBlockEntity.DATA_COUNT));
    }

    public StructureBlockEntity structure() {
        return structure;
    }

    public int data(int id) {
        return data.get(id);
    }

    /** One of the assignment buttons, for this GUI and the Terrarium's job list. */
    public static void adjust(StructureBlockEntity site, int action) {
        int requested = site.requested() < 0 ? site.capacity() : site.requested();
        switch (action) {
            case LESS -> site.setRequested(Math.max(0, requested - 1));
            case MORE -> site.setRequested(requested + 1);
            case LESS_10 -> site.setRequested(Math.max(0, requested - 10));
            case MORE_10 -> site.setRequested(requested + 10);
            case LESS_100 -> site.setRequested(Math.max(0, requested - 100));
            case MORE_100 -> site.setRequested(requested + 100);
            case AUTO -> site.setRequested(site.requested() < 0 ? site.capacity() : -1);
            default -> {
            }
        }
        site.contentsChanged();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!structure.kind().job().any()) {
            return false;
        }
        adjust(structure, id);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return structure.stillValid(player);
    }
}
