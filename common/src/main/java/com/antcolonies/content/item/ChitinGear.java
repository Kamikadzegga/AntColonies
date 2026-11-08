package com.antcolonies.content.item;

import java.util.Map;
import java.util.function.Consumer;

import com.antcolonies.AntColonies;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Chitin gear, made from Chitin Plates: tools and armor at iron level (a little longer-lasting and more enchantable).
 * The full armor set is what makes it worth wearing: wild ants take you for one of their own and never attack you
 * (giant anthills' defenders included), and you dig {@link #SET_DIG_BONUS} faster.
 */
public final class ChitinGear {
    public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM, AntColonies.id("repairs_chitin_gear"));
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, AntColonies.id("chitin"));
    /** Iron's defense (2, 6, 5, 2), a bit more durable (iron is 15) and enchantable (iron is 9), a little toughness. */
    public static final ArmorMaterial ARMOR = new ArmorMaterial(17, Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 5,
            ArmorType.BOOTS, 2), 15, SoundEvents.ARMOR_EQUIP_TURTLE, 0.5F, 0.0F, REPAIRS, ASSET);
    /** Mines what iron mines; iron is 250 uses, speed 6, +2 damage, enchantability 14. */
    public static final ToolMaterial TOOLS = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 300, 6.5F, 2.0F, 16, REPAIRS);

    /** The full set's mining speed bonus (a multiplier on the base speed). */
    public static final double SET_DIG_BONUS = 0.2;
    private static final Identifier SET_BONUS_ID = AntColonies.id("chitin_set");
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ChitinGear() {}

    /** Wears Chitin on head, chest, legs and feet. */
    public static boolean fullSet(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof Piece piece && piece.armor)) {
                return false;
            }
        }
        return true;
    }

    /** Adds or removes the set's digging bonus; checked once a second. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && player.tickCount % 20 == 0) {
            updateSetBonus(player);
        }
    }

    /** Gives the set's digging bonus to a player wearing the full set, and takes it away from one who isn't. */
    public static void updateSetBonus(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (speed == null) {
            return;
        }
        boolean wearing = fullSet(player);
        if (wearing && !speed.hasModifier(SET_BONUS_ID)) {
            speed.addTransientModifier(new AttributeModifier(SET_BONUS_ID, SET_DIG_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!wearing && speed.hasModifier(SET_BONUS_ID)) {
            speed.removeModifier(SET_BONUS_ID);
        }
    }

    /** A piece of Chitin armor or a Chitin tool: armor pieces explain the set bonus. */
    public static class Piece extends Item {
        private final boolean armor;

        public Piece(boolean armor, Properties properties) {
            super(properties);
            this.armor = armor;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            if (armor) {
                tooltip.accept(Component.translatable("tooltip.antcolonies.chitin_set").withStyle(ChatFormatting.GOLD));
                tooltip.accept(Component.translatable("tooltip.antcolonies.chitin_set.ants").withStyle(ChatFormatting.GRAY));
                tooltip.accept(Component.translatable("tooltip.antcolonies.chitin_set.dig", Math.round(SET_DIG_BONUS * 100)).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
