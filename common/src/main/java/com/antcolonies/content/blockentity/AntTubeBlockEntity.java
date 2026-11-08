package com.antcolonies.content.blockentity;

import com.antcolonies.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A Glass Ant Tube. It only knows which species walks through it (set by its colony's network scan while the colony
 * has ants), so its renderer can draw them. It never ticks and holds nothing else.
 */
public class AntTubeBlockEntity extends BlockEntity {
    private @Nullable Identifier species;

    public AntTubeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANT_TUBE.get(), pos, state);
    }

    public @Nullable Identifier species() {
        return species;
    }

    public void setSpecies(@Nullable Identifier species) {
        if (species == null ? this.species != null : !species.equals(this.species)) {
            this.species = species;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        species = input.read("species", Identifier.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("species", Identifier.CODEC, species);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
