package com.antcolonies.content.item;

import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.antcolonies.AntColonies;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Chitin gear, in four tiers: Chitin (from Chitin Plates, at iron level), Hardened (Hardened Chitin, between iron and
 * diamond), Infernal (a Hardened piece upgraded at a smithing table with Infernal Chitin: diamond level, fireproof) and
 * Void (an Infernal piece upgraded with a netherite ingot: past netherite). A full armor set of any chitin, tiers mixed
 * or not, makes wild ants take you for one of their own; its other bonuses go by its lowest tier: digging faster, and
 * from Infernal up Fire Resistance.
 */
public final class ChitinGear {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Identifier SET_BONUS_ID = AntColonies.id("chitin_set");
    /** How long the set's Fire Resistance lasts; renewed every second while the set is worn. */
    public static final int FIRE_TICKS = 220;

    /** A tier of chitin gear: its materials, what repairs it, and its full set's bonuses. */
    public enum Tier {
        /** Iron's defense (2, 6, 5, 2), more durable (iron is 15) and enchantable; mines what iron mines (iron: 250 uses, 6, +2). */
        CHITIN("", 17, new int[] {2, 6, 5, 2}, 15, SoundEvents.ARMOR_EQUIP_TURTLE, 0.5F, 0.0F,
                BlockTags.INCORRECT_FOR_IRON_TOOL, 300, 6.5F, 2.0F, 16, 0.2, false),
        /** A step past iron: more armor, some toughness, three times the uses; still no obsidian. */
        HARDENED("hardened_", 24, new int[] {2, 7, 5, 2}, 13, SoundEvents.ARMOR_EQUIP_TURTLE, 1.0F, 0.0F,
                BlockTags.INCORRECT_FOR_IRON_TOOL, 900, 7.0F, 2.5F, 14, 0.25, false),
        /** Diamond's defense (3, 8, 6, 3) with more toughness and a little knockback resistance; mines what diamond mines. */
        INFERNAL("infernal_", 35, new int[] {3, 8, 6, 3}, 13, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.5F, 0.05F,
                BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1800, 8.5F, 3.5F, 14, 0.3, true),
        /** Past netherite (37; 3, 8, 6, 3; toughness 3; 2031 uses, 9, +4): one more point of leggings, tougher, longer-lasting. */
        VOID("void_", 42, new int[] {3, 8, 7, 3}, 17, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.5F, 0.15F,
                BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2400, 9.5F, 4.5F, 17, 0.4, true);

        private final String prefix;
        public final TagKey<Item> repairs;
        public final ResourceKey<EquipmentAsset> asset;
        public final ArmorMaterial armor;
        public final ToolMaterial tools;
        /** The full set's mining speed bonus (a multiplier on the base speed). */
        public final double digBonus;
        /** Fireproof items (they don't burn in lava), and a full set gives Fire Resistance. */
        public final boolean fireproof;

        Tier(String prefix, int durability, int[] defense, int armorEnchant, Holder<SoundEvent> sound, float toughness, float knockback,
                TagKey<Block> incorrect, int uses, float speed, float damage, int toolEnchant, double digBonus, boolean fireproof) {
            this.prefix = prefix;
            this.repairs = TagKey.create(Registries.ITEM, AntColonies.id("repairs_" + prefix + "chitin_gear"));
            this.asset = ResourceKey.create(EquipmentAssets.ROOT_ID, AntColonies.id(prefix + "chitin"));
            this.armor = new ArmorMaterial(durability, Map.of(ArmorType.HELMET, defense[0], ArmorType.CHESTPLATE, defense[1],
                    ArmorType.LEGGINGS, defense[2], ArmorType.BOOTS, defense[3]), armorEnchant, sound, toughness, knockback, repairs, asset);
            this.tools = new ToolMaterial(incorrect, uses, speed, damage, toolEnchant, repairs);
            this.digBonus = digBonus;
            this.fireproof = fireproof;
        }

        /** An item id of this tier: {@code chitin_sword}, {@code void_chitin_sword}. */
        public String id(String piece) {
            return prefix + "chitin_" + piece;
        }
    }

    // The first tier's materials, by their old names.
    public static final TagKey<Item> REPAIRS = Tier.CHITIN.repairs;
    public static final ResourceKey<EquipmentAsset> ASSET = Tier.CHITIN.asset;
    public static final ArmorMaterial ARMOR = Tier.CHITIN.armor;
    public static final ToolMaterial TOOLS = Tier.CHITIN.tools;
    /** The Chitin set's mining speed bonus. */
    public static final double SET_DIG_BONUS = Tier.CHITIN.digBonus;

    private ChitinGear() {}

    /** Wears chitin armor (any tiers) on head, chest, legs and feet. */
    public static boolean fullSet(LivingEntity entity) {
        return setTier(entity) != null;
    }

    /** The lowest tier of a full chitin armor set the entity wears, or null without one. */
    public static @Nullable Tier setTier(LivingEntity entity) {
        Tier lowest = null;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof Piece piece && piece.armor)) {
                return null;
            }
            if (lowest == null || piece.tier.ordinal() < lowest.ordinal()) {
                lowest = piece.tier;
            }
        }
        return lowest;
    }

    /** Adds or removes the set's bonuses; checked once a second. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && player.tickCount % 20 == 0) {
            updateSetBonus(player);
        }
    }

    /** Gives the set's bonuses to a player wearing a full set (by its lowest tier), and takes the digging bonus away from one who isn't. */
    public static void updateSetBonus(Player player) {
        Tier tier = setTier(player);
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (speed != null) {
            AttributeModifier current = speed.getModifier(SET_BONUS_ID);
            double wanted = tier == null ? 0 : tier.digBonus;
            if (current != null && current.amount() != wanted) {
                speed.removeModifier(SET_BONUS_ID);
                current = null;
            }
            if (current == null && tier != null) {
                speed.addTransientModifier(new AttributeModifier(SET_BONUS_ID, wanted, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
        if (tier != null && tier.fireproof) {
            MobEffectInstance fire = player.getEffect(MobEffects.FIRE_RESISTANCE);
            if (fire == null || (fire.isAmbient() && fire.getDuration() < FIRE_TICKS - 20)) {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_TICKS, 0, true, false, true));
            }
        }
    }

    /** A piece of chitin armor or a chitin tool, of a tier: armor pieces explain the set bonus. */
    public static class Piece extends Item {
        private final boolean armor;
        private final Tier tier;

        public Piece(boolean armor, Properties properties) {
            this(armor, Tier.CHITIN, properties);
        }

        public Piece(boolean armor, Tier tier, Properties properties) {
            super(properties);
            this.armor = armor;
            this.tier = tier;
        }

        public Tier tier() {
            return tier;
        }

        public boolean isArmor() {
            return armor;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
            if (armor) {
                tooltip.accept(Component.translatable("tooltip.antcolonies.chitin_set").withStyle(ChatFormatting.GOLD));
                tooltip.accept(Component.translatable("tooltip.antcolonies.chitin_set.ants").withStyle(ChatFormatting.GRAY));
                tooltip.accept(Component.translatable("tooltip.antcolonies.chitin_set.dig", Math.round(tier.digBonus * 100)).withStyle(ChatFormatting.GRAY));
                if (tier.fireproof) {
                    tooltip.accept(Component.translatable("tooltip.antcolonies.chitin_set.fire").withStyle(ChatFormatting.GRAY));
                }
                if (tier != Tier.CHITIN) {
                    tooltip.accept(Component.translatable("tooltip.antcolonies.chitin_set.mixed").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
    }
}
