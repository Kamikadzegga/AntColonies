package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/**
 * Mekanism (mod id {@code mekanism}): brine and heavy water. Its gases and other chemicals are not in the fluid registry
 * and are left alone. Ids from its {@code MekanismFluids}.
 */
public final class MekanismLiquids {
    public static final String MOD = "mekanism";

    public static void offer() {
        LiquidKinds.offer(LiquidKind.of("brine", MOD, "mekanism:brine"));
        LiquidKinds.offer(LiquidKind.of("heavy_water", MOD, "mekanism:heavy_water"));
    }

    private MekanismLiquids() {}
}
