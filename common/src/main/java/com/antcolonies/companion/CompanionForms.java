package com.antcolonies.companion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import com.antcolonies.AntColonies;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jspecify.annotations.Nullable;

/** Transformations: lasting looks (and a few manners) an item brings out in a companion. Milk washes any of them off. */
public final class CompanionForms {
    public static final Identifier BINDLE = AntColonies.id("bindle");
    public static final Identifier JUMPING = AntColonies.id("jumping");

    private static final Map<Identifier, Form> FORMS = new LinkedHashMap<>();

    /**
     * One transformation.
     *
     * @param item   whether this item brings it on
     * @param use    what is left of the item afterwards; it gets the held stack and may change it
     * @param secret the name of its advancement trigger
     */
    public record Form(Predicate<ItemStack> item, BiFunction<Player, ItemStack, ItemStack> use, String secret) {
        /** One whose item is only shown to the ant and kept. */
        public static Form keeping(Predicate<ItemStack> item, String secret) {
            return new Form(item, (player, stack) -> stack, secret);
        }
    }

    public static synchronized void register(Identifier id, Form form) {
        FORMS.put(id, form);
    }

    /** The form an item brings on, or null. */
    static @Nullable Identifier of(ItemStack stack) {
        for (Map.Entry<Identifier, Form> entry : FORMS.entrySet()) {
            if (entry.getValue().item().test(stack)) {
                return entry.getKey();
            }
        }
        return null;
    }

    static Form form(Identifier id) {
        return FORMS.get(id);
    }

    public static Component label(Optional<Identifier> form) {
        return form.<Component>map(id -> Component.translatable("companion." + id.getNamespace() + ".form." + id.getPath()))
                .orElse(Component.translatable("companion.antcolonies.form.none"));
    }

    private static boolean jumpPotion(ItemStack stack) {
        if (!stack.is(Items.POTION)) {
            return false;
        }
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var effect : contents.getAllEffects()) {
            if (effect.is(MobEffects.JUMP_BOOST)) {
                return true;
            }
        }
        return false;
    }

    /** Drinks a potion up: the bottle comes back (not in Creative, where nothing is used up). */
    private static ItemStack drink(Player player, ItemStack stack) {
        if (player.hasInfiniteMaterials()) {
            return stack;
        }
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        stack.shrink(1);
        if (stack.isEmpty()) {
            return bottle;
        }
        Companions.give(player, bottle);
        return stack;
    }

    static {
        register(BINDLE, Form.keeping(stack -> stack.is(ItemTags.BUNDLES), "sad"));
        register(JUMPING, new Form(CompanionForms::jumpPotion, CompanionForms::drink, "jumping"));
    }

    private CompanionForms() {}
}
