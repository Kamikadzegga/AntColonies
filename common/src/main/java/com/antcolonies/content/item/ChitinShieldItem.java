package com.antcolonies.content.item;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The Chitin Shield: blocks like the vanilla shield, but is light. It is up in 0.15 s instead of 0.25 s, an axe knocks
 * it down for three quarters as long, and it lasts longer (450 uses against 336).
 * Repaired with Chitin Plates. A {@link ShieldItem}, so other mods treat it as a shield; its looks are a plain item model
 * (no banner patterns).
 */
public class ChitinShieldItem extends ShieldItem {
    public static final int DURABILITY = 450;
    public static final float BLOCK_DELAY = 0.15F;
    public static final float DISABLE_SCALE = 0.75F;

    public ChitinShieldItem(Properties properties) {
        super(properties);
    }

    /** The item's properties: durability, repair, the off hand and how it blocks. */
    public static Properties properties(Properties properties) {
        return properties.durability(DURABILITY)
                .repairable(ChitinGear.REPAIRS)
                .equippableUnswappable(EquipmentSlot.OFFHAND)
                .delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(BLOCK_DELAY, DISABLE_SCALE,
                        List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
                        new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
                        Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                        Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
                .component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.chitin_shield").withStyle(ChatFormatting.GRAY));
    }
}
