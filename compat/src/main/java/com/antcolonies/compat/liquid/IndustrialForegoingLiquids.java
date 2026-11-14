package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/**
 * Industrial Foregoing (mod id {@code industrialforegoing}): pink slime and latex. Ids from its {@code ModuleCore}.
 */
public final class IndustrialForegoingLiquids {
    public static final String MOD = "industrialforegoing";

    public static void offer() {
        LiquidKinds.offer(LiquidKind.of("pink_slime", MOD, "industrialforegoing:pink_slime"));
        LiquidKinds.offer(LiquidKind.of("latex", MOD, "industrialforegoing:latex"));
    }

    private IndustrialForegoingLiquids() {}
}
