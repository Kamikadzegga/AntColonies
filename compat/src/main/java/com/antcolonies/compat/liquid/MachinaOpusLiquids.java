package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/** MachinaOpus (mod id {@code machinaopus}): crude oil and liquid blaze. Ids from its {@code ModFluids}. */
public final class MachinaOpusLiquids {
    public static final String MOD = "machinaopus";

    public static void offer() {
        LiquidKinds.offer(LiquidKind.of("crude_oil", MOD, "machinaopus:crude_oil"));
        LiquidKinds.offer(LiquidKind.of("liquid_blaze", MOD, "machinaopus:liquid_blaze"));
    }

    private MachinaOpusLiquids() {}
}
