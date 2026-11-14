package com.antcolonies.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * How a fluid looks, taken from the game's own fluid models: its still texture and its tint. Whatever fluid a Droplet
 * Centrifuge holds (another mod's too) is drawn the way that fluid is drawn everywhere else.
 */
public final class FluidLook {
    /** The fluid's still texture (on the block atlas). */
    public static TextureAtlasSprite sprite(Fluid fluid) {
        return model(fluid).stillMaterial().sprite();
    }

    /** The fluid's tint, opaque (white for fluids that have none). */
    public static int color(Fluid fluid) {
        FluidTintSource tint = model(fluid).fluidTintSource();
        return ARGB.opaque(tint == null ? -1 : tint.colorAsStack(new FluidStack(fluid, 1)));
    }

    /**
     * One colour that stands for the fluid where its texture can't be shown (small marks in a GUI, a blur of liquid on
     * a spinning drum): its tint if it has one; else the crop colour of the Liquid Ants that make it; else a pale, glassy
     * tone.
     */
    public static int accent(Fluid fluid) {
        int tint = color(fluid);
        if ((tint & 0xFFFFFF) != 0xFFFFFF) {
            return tint;
        }
        net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        for (var species : ClientSpecies.all()) {
            var liquid = species.value().liquid();
            if (liquid.isPresent() && liquid.get().fluid().equals(id) && species.value().look().crop().isPresent()) {
                return ARGB.opaque(species.value().look().crop().get());
            }
        }
        return 0xFFBFD8E6;
    }

    private static FluidModel model(Fluid fluid) {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
    }

    private FluidLook() {}
}
