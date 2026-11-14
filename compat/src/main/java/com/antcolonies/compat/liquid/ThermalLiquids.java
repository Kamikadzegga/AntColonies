package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/**
 * Thermal Series (mod id {@code thermal}: every Thermal fluid is registered by Thermal Core under that one id): resin and
 * sap. Its crude oil and latex are left out, since MachinaOpus and Industrial Foregoing already give ants of those
 * names. Ids from its {@code TCoreFluids} (branch 1.20.x, the newest there is).
 */
public final class ThermalLiquids {
    public static final String MOD = "thermal";

    public static void offer() {
        LiquidKinds.offer(LiquidKind.of("resin", MOD, "thermal:resin"));
        LiquidKinds.offer(LiquidKind.of("sap", MOD, "thermal:sap"));
    }

    private ThermalLiquids() {}
}
