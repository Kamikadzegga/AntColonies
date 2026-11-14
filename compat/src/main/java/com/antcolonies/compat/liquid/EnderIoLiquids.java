package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/**
 * Ender IO (mod id {@code enderio}): nutrient distillation and hootch. Its still fluids are registered as
 * {@code enderio:fluid_<name>_still}, not under their plain names. Ids from its {@code EIOFluids} and
 * {@code FluidDeferredRegister} (branch 26.3).
 */
public final class EnderIoLiquids {
    public static final String MOD = "enderio";

    public static void offer() {
        LiquidKinds.offer(LiquidKind.of("nutrient_distillation", MOD, "enderio:fluid_nutrient_distillation_still"));
        LiquidKinds.offer(LiquidKind.of("hootch", MOD, "enderio:fluid_hootch_still"));
    }

    private EnderIoLiquids() {}
}
