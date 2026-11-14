package com.antcolonies.content.item;

import java.util.function.Consumer;

import com.antcolonies.colony.liquid.LiquidKind;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.material.Fluid;

/**
 * A droplet of one fluid, as Liquid Ants give it: a raw drop that a Droplet Centrifuge spins into the real fluid. One
 * item per fluid ({@code antcolonies:<kind>_droplet}), registered only while the fluid exists. Its tooltip names the
 * fluid by the fluid's own name, so it reads right in every language the fluid's mod is translated into.
 */
public class DropletItem extends Item {
    private final LiquidKind kind;

    public DropletItem(LiquidKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public LiquidKind kind() {
        return kind;
    }

    /** The fluid it is a droplet of. */
    public Fluid fluid() {
        return BuiltInRegistries.FLUID.getValue(kind.fluid());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.droplet", fluid().getFluidType().getDescription()).withStyle(ChatFormatting.GRAY));
    }
}
