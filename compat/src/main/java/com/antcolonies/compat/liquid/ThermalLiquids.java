package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/**
 * Thermal Series (all under mod id {@code thermal}): resin and sap. Its crude oil and latex are left out: other mods
 * already give ants of those names. Ids from its {@code TCoreFluids}.
 */
public final class ThermalLiquids {
    public static final String MOD = "thermal";

    public static void offer() {
        LiquidKinds.offer(LiquidKind.of("resin", MOD, "thermal:resin"));
        LiquidKinds.offer(LiquidKind.of("sap", MOD, "thermal:sap"));
    }

    private ThermalLiquids() {}
}
