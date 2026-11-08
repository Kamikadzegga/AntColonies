package com.antcolonies.content.menu;

import java.util.Optional;

import com.antcolonies.content.blockentity.UpgradeStationBlockEntity;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModMenus;
import com.antcolonies.colony.upgrade.SpeciesUpgradeKind;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Upgrade Station: base and material in, the species upgrade out (made when taken). */
public class UpgradeStationMenu extends BaseMenu {
    private final UpgradeStationBlockEntity station;
    private final SimpleContainer result = new SimpleContainer(1);
    private final DataSlot selected;
    private final Player player;

    public UpgradeStationMenu(int containerId, Inventory inventory, UpgradeStationBlockEntity station) {
        super(ModMenus.UPGRADE_STATION.get(), containerId);
        this.station = station;
        this.player = inventory.player;
        addSlot(new Slot(station, UpgradeStationBlockEntity.SLOT_BASE, 27, 47) {
            @Override
            public void setChanged() {
                super.setChanged();
                slotsChanged(station);
            }
        });
        addSlot(new Slot(station, UpgradeStationBlockEntity.SLOT_MATERIAL, 76, 47) {
            @Override
            public void setChanged() {
                super.setChanged();
                slotsChanged(station);
            }
        });
        addSlot(new Slot(result, 0, 134, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player taker, ItemStack stack) {
                Optional<UpgradeStationBlockEntity.Result> made = current();
                if (made.isPresent()) {
                    station.getItem(UpgradeStationBlockEntity.SLOT_BASE).shrink(1);
                    station.getItem(UpgradeStationBlockEntity.SLOT_MATERIAL).shrink(made.get().materialCount());
                    station.setChanged();
                }
                slotsChanged(station);
                super.onTake(taker, stack);
            }
        });
        addPlayerInventory(inventory, 8, 104);
        // The result slot isn't a block slot for shift-clicking into.
        selected = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return station.selected().ordinal();
            }

            @Override
            public void set(int value) {
                station.select(SpeciesUpgradeKind.values()[Math.clamp(value, 0, SpeciesUpgradeKind.values().length - 1)]);
            }
        });
        slotsChanged(station);
    }

    public static UpgradeStationMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UpgradeStationBlockEntity station = inventory.player.level().getBlockEntity(pos) instanceof UpgradeStationBlockEntity be ? be
                : new UpgradeStationBlockEntity(pos, ModBlocks.UPGRADE_STATION.get().defaultBlockState());
        return new UpgradeStationMenu(containerId, inventory, station);
    }

    public SpeciesUpgradeKind selected() {
        return SpeciesUpgradeKind.values()[Math.clamp(selected.get(), 0, SpeciesUpgradeKind.values().length - 1)];
    }

    private Optional<UpgradeStationBlockEntity.Result> current() {
        return UpgradeStationBlockEntity.result(player.level().registryAccess(), station.getItem(UpgradeStationBlockEntity.SLOT_BASE),
                station.getItem(UpgradeStationBlockEntity.SLOT_MATERIAL), selected());
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        result.setItem(0, current().map(UpgradeStationBlockEntity.Result::output).orElse(ItemStack.EMPTY));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < SpeciesUpgradeKind.values().length) {
            station.select(SpeciesUpgradeKind.values()[id]);
            slotsChanged(station);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == 2) {
            Slot slot = slots.get(2);
            if (!slot.hasItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = slot.getItem().copy();
            if (!moveItemStackTo(stack, 3, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
            return stack;
        }
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean stillValid(Player player) {
        return station.stillValid(player);
    }
}
