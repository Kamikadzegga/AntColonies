package com.antcolonies.companion;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * One companion: who it is, whose it is, how far it has come and what it wears.
 *
 * @param id       its own id, kept through tubes, dimensions and restarts
 * @param owner    empty only in a tube nobody has opened yet
 * @param species  its species (for another mod's ant: its entity type)
 * @param strength Hunting Strength, 1 to 10
 * @param xp       experience towards the next level
 * @param post     where it was told to stay or guard
 * @param form     a transformation (see {@link CompanionForms})
 */
public record CompanionData(UUID id, Optional<Owner> owner, Identifier type, Identifier species, int strength, int level, int xp,
                            CompanionCommand command, Optional<BlockPos> post, Gear gear, Optional<Identifier> form,
                            Personality personality) {
    // Read leniently: one bad value (an item whose mod is gone) must not take the whole companion with it.
    public static final MapCodec<CompanionData> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(CompanionData::id),
            Owner.CODEC.optionalFieldOf("owner").forGetter(CompanionData::owner),
            Identifier.CODEC.fieldOf("type").forGetter(CompanionData::type),
            Identifier.CODEC.fieldOf("species").forGetter(CompanionData::species),
            Codec.INT.lenientOptionalFieldOf("strength", 1).forGetter(CompanionData::strength),
            Codec.INT.lenientOptionalFieldOf("level", 1).forGetter(CompanionData::level),
            Codec.INT.lenientOptionalFieldOf("xp", 0).forGetter(CompanionData::xp),
            CompanionCommand.CODEC.lenientOptionalFieldOf("command", CompanionCommand.FOLLOW).forGetter(CompanionData::command),
            BlockPos.CODEC.lenientOptionalFieldOf("post").forGetter(CompanionData::post),
            Gear.CODEC.lenientOptionalFieldOf("gear", Gear.EMPTY).forGetter(CompanionData::gear),
            Identifier.CODEC.lenientOptionalFieldOf("form").forGetter(CompanionData::form),
            Personality.CODEC.lenientOptionalFieldOf("personality", Personality.CURIOUS).forGetter(CompanionData::personality)
    ).apply(i, CompanionData::new));
    public static final Codec<CompanionData> CODEC = MAP_CODEC.codec();
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static final int SLOT_ARMOR = 0;
    public static final int SLOT_COSTUME = 1;
    public static final int SLOT_CART = 2;
    public static final int GEAR_SLOTS = 3;

    public record Owner(UUID id, String name) {
        public static final Codec<Owner> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Owner::id),
                Codec.STRING.lenientOptionalFieldOf("name", "").forGetter(Owner::name)
        ).apply(i, Owner::new));
    }

    /** What it wears: armor, a costume and a cart, each in a slot of its own. */
    public record Gear(ItemStack armor, ItemStack costume, ItemStack cart) {
        public static final Gear EMPTY = new Gear(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
        public static final Codec<Gear> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.OPTIONAL_CODEC.lenientOptionalFieldOf("armor", ItemStack.EMPTY).forGetter(Gear::armor),
                ItemStack.OPTIONAL_CODEC.lenientOptionalFieldOf("costume", ItemStack.EMPTY).forGetter(Gear::costume),
                ItemStack.OPTIONAL_CODEC.lenientOptionalFieldOf("cart", ItemStack.EMPTY).forGetter(Gear::cart)
        ).apply(i, Gear::new));

        // Item stacks have no equality of their own.
        @Override
        public boolean equals(Object other) {
            return other instanceof Gear gear && ItemStack.matches(armor, gear.armor) && ItemStack.matches(costume, gear.costume)
                    && ItemStack.matches(cart, gear.cart);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * ItemStack.hashItemAndComponents(armor) + ItemStack.hashItemAndComponents(costume))
                    + ItemStack.hashItemAndComponents(cart);
        }

        public ItemStack get(int slot) {
            return switch (slot) {
                case SLOT_ARMOR -> armor;
                case SLOT_COSTUME -> costume;
                default -> cart;
            };
        }

        public Gear with(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_ARMOR -> new Gear(stack, costume, cart);
                case SLOT_COSTUME -> new Gear(armor, stack, cart);
                default -> new Gear(armor, costume, stack);
            };
        }
    }

    public boolean ownedBy(UUID player) {
        return owner.isPresent() && owner.get().id().equals(player);
    }

    public boolean hasCart() {
        return !gear.cart().isEmpty();
    }

    public boolean hasForm(Identifier wanted) {
        return form.isPresent() && form.get().equals(wanted);
    }

    public CompanionData withId(UUID next) {
        return new CompanionData(next, owner, type, species, strength, level, xp, command, post, gear, form, personality);
    }

    public CompanionData withOwner(Owner next) {
        return new CompanionData(id, Optional.of(next), type, species, strength, level, xp, command, post, gear, form, personality);
    }

    /** The same companion with nobody to belong to. */
    public CompanionData withoutOwner() {
        return new CompanionData(id, Optional.empty(), type, species, strength, level, xp, command, post, gear, form, personality);
    }

    public CompanionData withProgress(int nextLevel, int nextXp) {
        return new CompanionData(id, owner, type, species, strength, nextLevel, nextXp, command, post, gear, form, personality);
    }

    /** A new order: staying and guarding happen where it stands. */
    public CompanionData withCommand(CompanionCommand next, BlockPos at) {
        Optional<BlockPos> place = next == CompanionCommand.FOLLOW ? Optional.empty() : Optional.of(at.immutable());
        return new CompanionData(id, owner, type, species, strength, level, xp, next, place, gear, form, personality);
    }

    public CompanionData withGear(Gear next) {
        return new CompanionData(id, owner, type, species, strength, level, xp, command, post, next, form, personality);
    }

    public CompanionData withForm(Optional<Identifier> next) {
        return new CompanionData(id, owner, type, species, strength, level, xp, command, post, gear, next, personality);
    }
}
