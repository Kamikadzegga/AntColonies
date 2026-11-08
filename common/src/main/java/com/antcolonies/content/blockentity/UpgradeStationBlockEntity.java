package com.antcolonies.content.blockentity;

import java.util.Map;
import java.util.Optional;

import com.antcolonies.content.item.SpeciesUpgrade;
import com.antcolonies.content.menu.UpgradeStationMenu;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModRegistries;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.colony.upgrade.SpeciesUpgradeKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Makes species upgrades: an Upgrade Base and four of a species' signature material give its tier 1 upgrade of the
 * chosen kind; a tier 1 or 2 species upgrade and 8 or 16 more of the material raise it a tier.
 */
public class UpgradeStationBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOT_BASE = 0;
    public static final int SLOT_MATERIAL = 1;

    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private SpeciesUpgradeKind selected = SpeciesUpgradeKind.EXTRACTION;

    public UpgradeStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UPGRADE_STATION.get(), pos, state);
    }

    public SpeciesUpgradeKind selected() {
        return selected;
    }

    public void select(SpeciesUpgradeKind kind) {
        selected = kind;
        setChanged();
    }

    /** What the inputs make, and how much material it takes. */
    public record Result(ItemStack output, int materialCount) {}

    public static int materialFor(int tier) {
        return 4 << (tier - 1);
    }

    public static Optional<Result> result(HolderLookup.@Nullable Provider registries, ItemStack base, ItemStack material, SpeciesUpgradeKind kind) {
        if (registries == null || material.isEmpty()) {
            return Optional.empty();
        }
        Identifier species = speciesWithSignature(registries, material);
        if (species == null) {
            return Optional.empty();
        }
        SpeciesUpgrade upgrade;
        if (base.is(ModItems.UPGRADE_BASE.get())) {
            upgrade = new SpeciesUpgrade(species, kind, 1);
        } else {
            SpeciesUpgrade existing = base.get(ModDataComponents.SPECIES_UPGRADE.get());
            if (existing == null || existing.tier() >= 3 || !existing.species().equals(species)) {
                return Optional.empty();
            }
            upgrade = new SpeciesUpgrade(species, existing.kind(), existing.tier() + 1);
        }
        int needed = materialFor(upgrade.tier());
        if (material.getCount() < needed) {
            return Optional.empty();
        }
        ItemStack output = new ItemStack(ModItems.SPECIES_UPGRADE.get());
        output.set(ModDataComponents.SPECIES_UPGRADE.get(), upgrade);
        return Optional.of(new Result(output, needed));
    }

    public static @Nullable Identifier speciesWithSignature(HolderLookup.Provider registries, ItemStack material) {
        var lookup = registries.lookup(ModRegistries.SPECIES);
        if (lookup.isEmpty()) {
            return null;
        }
        for (var entry : lookup.get().listElements().toList()) {
            AntSpecies species = entry.value();
            if (species.signature().isPresent() && material.is(species.signature().get())) {
                return entry.key().identifier();
            }
        }
        return null;
    }

    /** Every species with a signature material, for JEI. */
    public static Map<Identifier, AntSpecies> withSignatures(HolderLookup.Provider registries) {
        java.util.LinkedHashMap<Identifier, AntSpecies> map = new java.util.LinkedHashMap<>();
        registries.lookup(ModRegistries.SPECIES).ifPresent(lookup -> lookup.listElements()
                .filter(entry -> entry.value().signature().isPresent())
                .forEach(entry -> map.put(entry.key().identifier(), entry.value())));
        return map;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.antcolonies.upgrade_station");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new UpgradeStationMenu(containerId, inventory, this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        selected = SpeciesUpgradeKind.values()[Math.clamp(input.getIntOr("selected", 0), 0, SpeciesUpgradeKind.values().length - 1)];
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("selected", selected.ordinal());
    }
}
