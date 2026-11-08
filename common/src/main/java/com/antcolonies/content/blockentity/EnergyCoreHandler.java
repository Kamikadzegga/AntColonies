package com.antcolonies.content.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The FE input of a Genetic Energy Core: passes energy on to the formed laboratory whose controller stands within the
 * lab's range. The controller is looked up on every call (from the last one found, else by a search), so cables that
 * keep this handler never feed a laboratory that was broken or rebuilt elsewhere.
 */
public final class EnergyCoreHandler implements EnergyHandler {
    private final Level level;
    private final BlockPos core;
    private @Nullable BlockPos controller;

    public EnergyCoreHandler(Level level, BlockPos core) {
        this.level = level;
        this.core = core.immutable();
    }

    private @Nullable GeneticLabBlockEntity lab() {
        if (controller != null && level.getBlockEntity(controller) instanceof GeneticLabBlockEntity lab && lab.formed()) {
            return lab;
        }
        controller = null;
        int range = GeneticLabBlockEntity.RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(core.offset(-range, -1, -range), core.offset(range, 1, range))) {
            if (level.getBlockEntity(pos) instanceof GeneticLabBlockEntity lab && lab.formed()) {
                controller = pos.immutable();
                return lab;
            }
        }
        return null;
    }

    @Override
    public long getAmountAsLong() {
        GeneticLabBlockEntity lab = lab();
        return lab == null ? 0 : lab.energyHandler().getAmountAsLong();
    }

    @Override
    public long getCapacityAsLong() {
        GeneticLabBlockEntity lab = lab();
        return lab == null ? 0 : lab.energyHandler().getCapacityAsLong();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        GeneticLabBlockEntity lab = lab();
        return lab == null ? 0 : lab.energyHandler().insert(amount, transaction);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return 0;
    }
}
