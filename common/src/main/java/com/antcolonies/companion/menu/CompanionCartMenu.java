package com.antcolonies.companion.menu;

import com.antcolonies.companion.CompanionCart;
import com.antcolonies.content.menu.BaseMenu;
import com.antcolonies.core.registry.ModMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** What a Cart Ant carries. */
public class CompanionCartMenu extends BaseMenu {
    public static final int BUTTON_ANT = 0;
    public static final int CART_X = 8;
    public static final int CART_Y = 24;
    public static final int INVENTORY_Y = 58;

    private final @Nullable Mob mob;

    public CompanionCartMenu(int containerId, Inventory inventory, @Nullable Mob mob) {
        super(ModMenus.COMPANION_CART.get(), containerId);
        this.mob = mob;
        Container cart = mob == null || mob.level().isClientSide() ? new CompanionCart() : CompanionCart.of(mob);
        for (int slot = 0; slot < CompanionCart.SIZE; slot++) {
            addSlot(new Slot(cart, slot, CART_X + 18 * slot, CART_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return container.canPlaceItem(getContainerSlot(), stack);
                }
            });
        }
        addPlayerInventory(inventory, 8, INVENTORY_Y);
    }

    public static CompanionCartMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return new CompanionCartMenu(containerId, inventory, CompanionMenu.find(inventory, buffer));
    }

    public @Nullable Mob mob() {
        return mob;
    }

    /** Fills stacks that are there before it starts new ones. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < blockSlots ? moveItemStackTo(stack, blockSlots, slots.size(), true)
                : moveItemStackTo(stack, 0, blockSlots, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_ANT && mob != null && player instanceof ServerPlayer server && stillValid(player)) {
            CompanionMenus.open(server, mob);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return mob != null && CompanionCart.reachable(mob, player);
    }
}
