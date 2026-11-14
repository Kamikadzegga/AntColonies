package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/**
 * Immersive Engineering (mod id {@code immersiveengineering}): creosote oil and plant oil (whose id has no underscore).
 * Ids from its {@code IEFluids}.
 */
public final class ImmersiveEngineeringLiquids {
    public static final String MOD = "immersiveengineering";

    public static void offer() {
        LiquidKinds.offer(LiquidKind.of("creosote", MOD, "immersiveengineering:creosote"));
        LiquidKinds.offer(LiquidKind.of("plant_oil", MOD, "immersiveengineering:plantoil"));
    }

    private ImmersiveEngineeringLiquids() {}
}
