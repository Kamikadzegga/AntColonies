package com.antcolonies.compat.liquid;

/**
 * The fluids of other mods that Liquid Ants can make, one small class per mod. Each offers its fluids by id only, so
 * nothing here needs the other mod to load.
 */
public final class LiquidIntegrations {
    public static void offerAll() {
        MachinaOpusLiquids.offer();
        MekanismLiquids.offer();
        CreateLiquids.offer();
        IndustrialForegoingLiquids.offer();
        EnderIoLiquids.offer();
        ThermalLiquids.offer();
        ImmersiveEngineeringLiquids.offer();
    }

    private LiquidIntegrations() {}
}
