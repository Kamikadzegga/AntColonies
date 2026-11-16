package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/**
 * Create (mod id {@code create}): honey, chocolate and Builder's Tea (a registered fluid with no bucket or block).
 * Ids from its {@code AllFluids}.
 */
public final class CreateLiquids {
    public static final String MOD = "create";

    public static void offer() {
        LiquidKinds.offer(LiquidKind.of("honey", MOD, "create:honey"));
        LiquidKinds.offer(LiquidKind.of("chocolate", MOD, "create:chocolate"));
        LiquidKinds.offer(LiquidKind.of("tea", MOD, "create:tea"));
    }

    private CreateLiquids() {}
}
