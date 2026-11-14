package com.antcolonies.content.blockentity;

import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * An Ant Egg Cluster's or Larva Cluster's species, and for one an anthill was built with, that anthill (its Queen's
 * spot): the ants it hatches into belong there. The species goes with the item when the cluster is picked up with Silk
 * Touch (and back into the block when it is placed), so the item can join a colony of that species.
 */
public class BroodClusterBlockEntity extends BlockEntity {
    private @Nullable Identifier species;
    private @Nullable BlockPos anthill;

    public BroodClusterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BROOD_CLUSTER.get(), pos, state);
    }

    public @Nullable Identifier species() {
        return species;
    }

    public @Nullable BlockPos anthill() {
        return anthill;
    }

    public void set(@Nullable Identifier species, @Nullable BlockPos anthill) {
        this.species = species;
        this.anthill = anthill == null ? null : anthill.immutable();
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (species != null) {
            output.putString("species", species.toString());
        }
        if (anthill != null) {
            output.putLong("anthill", anthill.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        species = input.getString("species").map(Identifier::tryParse).orElse(null);
        anthill = input.getLong("anthill").map(BlockPos::of).orElse(null);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (species != null) {
            components.set(ModDataComponents.SPECIES.get(), species);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        species = components.get(ModDataComponents.SPECIES.get());
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("species");
    }
}
