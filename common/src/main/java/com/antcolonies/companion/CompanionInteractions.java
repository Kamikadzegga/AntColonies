package com.antcolonies.companion;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.antcolonies.companion.item.CompanionGear;
import com.antcolonies.companion.menu.CompanionMenus;
import com.antcolonies.content.item.GuardAntItem;
import com.antcolonies.core.advancement.ModTriggers;
import com.antcolonies.core.registry.ModDataMaps;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** What an owner can do to a companion with an item in hand, one small step each. They run on both sides; only the server changes things. */
public final class CompanionInteractions {
    /** One step: a result when it was its turn, null to let the next one look. */
    public interface Interaction {
        @Nullable InteractionResult use(Context context);
    }

    /** One use of an item on a companion by its owner. */
    public record Context(Mob mob, CompanionData data, Player player, InteractionHand hand, ItemStack stack) {
        /** Whether this side may change things. */
        public boolean server() {
            return !player.level().isClientSide();
        }
    }

    private static final List<Interaction> INTERACTIONS = new CopyOnWriteArrayList<>(List.of(
            CompanionInteractions::form, CompanionInteractions::milk, CompanionInteractions::gear, CompanionInteractions::feed,
            CompanionInteractions::tube, CompanionInteractions::tweezers, CompanionInteractions::emptyHand));

    public static void register(Interaction interaction) {
        // Before the empty hand, which takes whatever is left.
        INTERACTIONS.add(INTERACTIONS.size() - 1, interaction);
    }

    static @Nullable InteractionResult run(Context context) {
        for (Interaction interaction : INTERACTIONS) {
            InteractionResult result = interaction.use(context);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static @Nullable InteractionResult form(Context c) {
        Identifier form = CompanionForms.of(c.stack());
        if (form == null) {
            return null;
        }
        if (c.server() && c.mob().level() instanceof ServerLevel level) {
            if (c.data().hasForm(form)) {
                c.player().sendOverlayMessage(Component.translatable("message.antcolonies.companion.form.already"));
                return InteractionResult.SUCCESS;
            }
            ItemStack left = CompanionForms.form(form).use().apply(c.player(), c.stack());
            if (left != c.stack()) {
                c.player().setItemInHand(c.hand(), left);
            }
            Companions.set(c.mob(), c.data().withForm(Optional.of(form)));
            level.sendParticles(ParticleTypes.POOF, c.mob().getX(), c.mob().getY(0.5), c.mob().getZ(), 12, 0.25, 0.2, 0.25, 0.02);
            level.playSound(null, c.mob().blockPosition(), ModSounds.EVOLUTION.get(), SoundSource.NEUTRAL, 0.6F, 1.5F);
            if (c.player() instanceof ServerPlayer player) {
                ModTriggers.COMPANION.get().trigger(player, CompanionForms.form(form).secret());
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static @Nullable InteractionResult milk(Context c) {
        if (!c.stack().is(Items.MILK_BUCKET) || c.data().form().isEmpty()) {
            return null;
        }
        if (c.server() && c.mob().level() instanceof ServerLevel level) {
            if (!c.player().hasInfiniteMaterials()) {
                c.player().setItemInHand(c.hand(), new ItemStack(Items.BUCKET));
            }
            Companions.set(c.mob(), c.data().withForm(Optional.empty()));
            level.playSound(null, c.mob().blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.6F, 1.4F);
            level.sendParticles(ParticleTypes.POOF, c.mob().getX(), c.mob().getY(0.5), c.mob().getZ(), 8, 0.2, 0.2, 0.2, 0.02);
        }
        return InteractionResult.SUCCESS;
    }

    /** Armor, a costume or a cart: put on, and what it wore there comes back. */
    private static @Nullable InteractionResult gear(Context c) {
        if (!(c.stack().getItem() instanceof CompanionGear gear)) {
            return null;
        }
        if (c.server()) {
            int slot = gear.slot();
            if (slot == CompanionData.SLOT_CART && c.data().hasCart()) {
                return InteractionResult.SUCCESS;
            }
            ItemStack worn = c.data().gear().get(slot);
            ItemStack put = c.stack().copyWithCount(1);
            c.stack().consume(1, c.player());
            Companions.set(c.mob(), c.data().withGear(c.data().gear().with(slot, put)));
            if (!worn.isEmpty()) {
                Companions.give(c.player(), worn.copy());
            }
            c.mob().playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.3F);
        }
        return InteractionResult.SUCCESS;
    }

    private static @Nullable InteractionResult feed(Context c) {
        ModDataMaps.AntFood food = ModDataMaps.food(c.stack());
        if (food == null || c.mob().getHealth() >= c.mob().getMaxHealth()) {
            return null;
        }
        if (c.server() && c.mob().level() instanceof ServerLevel level) {
            c.mob().heal((float) Math.max(2, food.food() / 5));
            c.stack().consume(1, c.player());
            level.sendParticles(ParticleTypes.HEART, c.mob().getX(), c.mob().getY() + 0.4, c.mob().getZ(), 2, 0.2, 0.1, 0.2, 0);
        }
        return InteractionResult.SUCCESS;
    }

    private static @Nullable InteractionResult tube(Context c) {
        if (!c.stack().is(ModItems.TEST_TUBE.get())) {
            return null;
        }
        if (c.server() && pickUp(c.mob(), c.data(), c.player())) {
            c.stack().consume(1, c.player());
        }
        return InteractionResult.SUCCESS;
    }

    /** Tweezers are for wild ants: a companion would lose all it is in them. */
    private static @Nullable InteractionResult tweezers(Context c) {
        if (!c.stack().is(ModItems.TWEEZERS.get())) {
            return null;
        }
        if (c.server()) {
            c.player().sendOverlayMessage(Component.translatable("message.antcolonies.companion.tweezers"));
        }
        return InteractionResult.SUCCESS;
    }

    /** Sneaking gives the next order; otherwise its screen opens, or its cart's. */
    private static @Nullable InteractionResult emptyHand(Context c) {
        if (!c.stack().isEmpty() || c.hand() != InteractionHand.MAIN_HAND) {
            return null;
        }
        if (c.server() && c.player() instanceof ServerPlayer player) {
            if (player.isShiftKeyDown()) {
                CompanionCommand next = c.data().command().next();
                Companions.command(c.mob(), c.data(), next);
                player.sendOverlayMessage(Component.translatable("message.antcolonies.companion.command."
                        + next.getSerializedName(), c.mob().getDisplayName()));
            } else if (c.data().hasCart()) {
                CompanionMenus.openCart(player, c.mob());
            } else {
                CompanionMenus.open(player, c.mob());
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Puts a companion in a tube, all it is with it. Refused while its cart holds anything. */
    public static boolean pickUp(Mob mob, CompanionData data, Player player) {
        if (!CompanionCart.of(mob).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.companion.cart_full").withStyle(ChatFormatting.RED));
            return false;
        }
        mob.stopRiding();
        mob.ejectPassengers();
        mob.dropLeash();
        ItemStack tube = GuardAntItem.tube(mob);
        if (tube.isEmpty()) {
            return false;
        }
        mob.discard();
        Companions.give(player, tube);
        return true;
    }

    private CompanionInteractions() {}
}
