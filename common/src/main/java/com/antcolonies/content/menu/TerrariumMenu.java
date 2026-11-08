package com.antcolonies.content.menu;

import java.util.List;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The colony overview. Its slots are the Terrarium's (Queen, feed, empty tubes) and its upgrades; everything shown about
 * the colony comes from the client's copy of the Terrarium, which the server keeps in sync.
 */
public class TerrariumMenu extends BaseMenu {
    public static final int BUTTON_SOLDIERS_LESS = 0;
    public static final int BUTTON_SOLDIERS_MORE = 1;
    public static final int BUTTON_REMOVE_QUEEN = 2;
    /** Assignment buttons: base + site index * 20 + action (see {@link StructureMenu#adjust}). */
    public static final int BUTTON_ASSIGNMENT = 1000;
    public static final int INVENTORY_X = 44;
    public static final int INVENTORY_Y = 140;

    private final TerrariumBlockEntity terrarium;

    public TerrariumMenu(int containerId, Inventory inventory, TerrariumBlockEntity terrarium) {
        super(ModMenus.TERRARIUM.get(), containerId);
        this.terrarium = terrarium;
        addSlot(new FilteredSlot(terrarium, TerrariumBlockEntity.SLOT_QUEEN, 8, 20,
                stack -> stack.is(ModItems.QUEEN_TEST_TUBE.get()) || stack.is(ModItems.TEST_TUBE.get()), 1));
        addSlot(new FilteredSlot(terrarium, TerrariumBlockEntity.SLOT_FEED, 8, 44, stack -> true, 64));
        addSlot(new FilteredSlot(terrarium, TerrariumBlockEntity.SLOT_OUTPUT, 8, 68, stack -> false, 64));
        for (int i = 0; i < TerrariumBlockEntity.UPGRADE_SLOTS; i++) {
            addUpgradeSlot(new FilteredSlot(terrarium.upgrades(), i, 8 + (i % 2) * 18, 94 + (i / 2) * 18, TerrariumBlockEntity::acceptsUpgrade, 1));
        }
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y);
    }

    public static TerrariumMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof TerrariumBlockEntity terrarium) {
            return new TerrariumMenu(containerId, inventory, terrarium);
        }
        return new TerrariumMenu(containerId, inventory, new TerrariumBlockEntity(pos, com.antcolonies.core.registry.ModBlocks.TERRARIUM.get().defaultBlockState()));
    }

    public TerrariumBlockEntity terrarium() {
        return terrarium;
    }

    public Colony colony() {
        return terrarium.colony();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        Colony colony = terrarium.colony();
        switch (id) {
            case BUTTON_SOLDIERS_LESS -> colony.setSoldierShare(colony.soldierShare() - 0.05);
            case BUTTON_SOLDIERS_MORE -> colony.setSoldierShare(colony.soldierShare() + 0.05);
            case BUTTON_REMOVE_QUEEN -> {
                return terrarium.removeQueen();
            }
            default -> {
                if (id < BUTTON_ASSIGNMENT) {
                    return false;
                }
                int index = (id - BUTTON_ASSIGNMENT) / 20;
                int action = (id - BUTTON_ASSIGNMENT) % 20;
                List<Colony.Assignment> assignments = colony.assignments();
                if (index >= assignments.size()) {
                    return false;
                }
                BlockPos pos = assignments.get(index).pos();
                if (!(player.level().getBlockEntity(pos) instanceof StructureBlockEntity site)) {
                    return false;
                }
                StructureMenu.adjust(site, action);
            }
        }
        terrarium.setChanged();
        terrarium.syncNow();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return terrarium.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return super.quickMoveStack(player, index);
    }

    public @Nullable ItemStack queenSlot() {
        return terrarium.getItem(TerrariumBlockEntity.SLOT_QUEEN);
    }
}
