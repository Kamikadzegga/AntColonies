package com.antcolonies.compat.liquid;

import com.antcolonies.colony.liquid.LiquidKind;
import com.antcolonies.colony.liquid.LiquidKinds;

/**
 * Create (mod id {@code create}): honey, chocolate and Builder's Tea. Tea has no bucket and no block, but it is a
 * registered fluid that tanks and pipes hold, which is all a droplet needs. Ids from its {@code AllFluids}.
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
