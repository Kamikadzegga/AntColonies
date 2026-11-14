package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/**
 * Mekanism (mod id {@code mekanism}): brine and heavy water. Both are entries of the ordinary fluid registry, held by any
 * tank or pipe. Mekanism's gases and other chemicals live in its own chemical registry and are left alone: nothing that
 * only exists there gets an ant. Ids from its {@code MekanismFluids} (branch 26.3).
 */
public final class MekanismLiquids {
    public static final String MOD = "mekanism";

    public static void offer() {
        LiquidKinds.offer(LiquidKind.of("brine", MOD, "mekanism:brine"));
        LiquidKinds.offer(LiquidKind.of("heavy_water", MOD, "mekanism:heavy_water"));
    }

    private MekanismLiquids() {}
}
