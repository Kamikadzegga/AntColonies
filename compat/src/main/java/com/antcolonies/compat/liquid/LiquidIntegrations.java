package com.antcolonies.compat.liquid;

/**
 * The fluids of other mods that Liquid Ants can make, one small class per mod. Each only <em>offers</em> its fluids by id
 * (no class of the other mod is ever named, so nothing here needs that mod to load): a kind becomes real only if the mod
 * is installed and the fluid is in the fluid registry, see {@link com.antcolonies.colony.liquid.LiquidKind#available()}.
 *
 * <p>Applied Energistics 2 is not here on purpose: it stores and moves fluids but registers none of its own, so there is
 * nothing for an ant to make.
 *
 * <p>A new integration is one more class like these and one line below, plus its data and art (tools/DataGen.java and
 * tools/TexGen.java, the LIQUIDS table in each). Another mod can do the same from its own constructor with
 * {@link com.antcolonies.colony.liquid.LiquidKinds#offer}.
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
