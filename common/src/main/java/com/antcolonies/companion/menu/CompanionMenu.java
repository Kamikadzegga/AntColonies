package com.antcolonies.companion.menu;

import com.antcolonies.companion.CompanionCart;
import com.antcolonies.companion.CompanionCommand;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.CompanionInteractions;
import com.antcolonies.companion.CompanionStats;
import com.antcolonies.companion.Companions;
import com.antcolonies.companion.item.CompanionGear;
import com.antcolonies.content.menu.BaseMenu;
import com.antcolonies.content.menu.FilteredSlot;
import com.antcolonies.core.registry.ModMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** A companion's own screen: what it wears, and the orders it takes as menu buttons. Its numbers come from its synced data. */
public class CompanionMenu extends BaseMenu {
    public static final int BUTTON_PICK_UP = 10;
    public static final int BUTTON_CART = 11;
    public static final int MAX_NAME = 32;
    public static final int GEAR_X = 8;
    public static final int GEAR_Y = 96;
    public static final int INVENTORY_Y = 148;

    private final @Nullable Mob mob;
    private int damage;

    public CompanionMenu(int containerId, Inventory inventory, @Nullable Mob mob) {
        super(ModMenus.COMPANION.get(), containerId);
        this.mob = mob;
        GearContainer gear = new GearContainer(mob);
        for (int i = 0; i < CompanionData.GEAR_SLOTS; i++) {
            int slot = i;
            addSlot(new FilteredSlot(gear, slot, GEAR_X + 20 * slot, GEAR_Y,
                    stack -> stack.getItem() instanceof CompanionGear item && item.slot() == slot, 1) {
                @Override
                public boolean mayPickup(Player player) {
                    // A loaded cart stays on.
                    return super.mayPickup(player) && (slot != CompanionData.SLOT_CART || mob == null || mob.level().isClientSide()
                            || CompanionCart.of(mob).isEmpty());
                }
            });
        }
        addPlayerInventory(inventory, 8, INVENTORY_Y);
        // What its bite does is not told to clients otherwise.
        addDataSlot(new DataSlot() {
            private int synced;

            @Override
            public int get() {
                CompanionData data = Companions.get(mob);
                return mob == null || mob.level().isClientSide() || data == null ? synced : (int) Math.round(CompanionStats.damage(mob) * 10);
            }

            @Override
            public void set(int value) {
                synced = value;
                damage = value;
            }
        });
    }

    public static CompanionMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return new CompanionMenu(containerId, inventory, find(inventory, buffer));
    }

    static @Nullable Mob find(Inventory inventory, RegistryFriendlyByteBuf buffer) {
        return inventory.player.level().getEntity(buffer.readVarInt()) instanceof Mob mob ? mob : null;
    }

    /** Its attack damage, as the server has it. */
    public double damage() {
        return damage / 10.0;
    }

    public @Nullable Mob mob() {
        return mob;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        CompanionData data = Companions.get(mob);
        if (mob == null || data == null || !(player instanceof ServerPlayer server) || !stillValid(player)) {
            return false;
        }
        if (id >= 0 && id < CompanionCommand.values().length) {
            Companions.command(mob, data, CompanionCommand.values()[id]);
            return true;
        }
        if (id == BUTTON_CART && data.hasCart()) {
            CompanionMenus.openCart(server, mob);
            return true;
        }
        if (id == BUTTON_PICK_UP && CompanionInteractions.pickUp(mob, data, player)) {
            server.closeContainer();
            return true;
        }
        return false;
    }

    /** Names the companion; an empty name gives it its species' name back. */
    public void rename(Player player, String name) {
        if (mob == null || !stillValid(player)) {
            return;
        }
        String clean = StringUtil.filterText(name).strip();
        if (clean.length() <= MAX_NAME) {
            mob.setCustomName(clean.isEmpty() ? null : Component.literal(clean));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return mob != null && !mob.isRemoved() && mob.isAlive() && Companions.isOwner(Companions.get(mob), player)
                && player.level() == mob.level() && player.distanceToSqr(mob) <= 64;
    }

    /** The gear slots: a copy of what the companion wears that writes every change back to it. */
    private static final class GearContainer extends SimpleContainer {
        private final @Nullable Mob mob;
        private boolean ready;

        GearContainer(@Nullable Mob mob) {
            super(CompanionData.GEAR_SLOTS);
            this.mob = mob;
            CompanionData data = Companions.get(mob);
            if (data != null) {
                for (int slot = 0; slot < CompanionData.GEAR_SLOTS; slot++) {
                    setItem(slot, data.gear().get(slot).copy());
                }
            }
            ready = true;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!ready || mob == null || mob.level().isClientSide() || mob.isRemoved()) {
                return;
            }
            CompanionData data = Companions.get(mob);
            if (data != null) {
                CompanionData.Gear gear = data.gear();
                for (int slot = 0; slot < CompanionData.GEAR_SLOTS; slot++) {
                    ItemStack stack = getItem(slot);
                    if (!ItemStack.matches(stack, gear.get(slot))) {
                        gear = gear.with(slot, stack.copy());
                    }
                }
                if (gear != data.gear()) {
                    Companions.set(mob, data.withGear(gear));
                }
            }
        }
    }
}
