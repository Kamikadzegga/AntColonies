package com.antcolonies.client.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.antcolonies.client.model.CostumeParts;
import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.CompanionForms;
import com.antcolonies.companion.Personality;
import com.antcolonies.companion.item.CompanionArmorItem;
import com.antcolonies.companion.item.CompanionCostumeItem;
import com.antcolonies.content.item.ChitinGear;

import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * How one companion is drawn this frame, over its species' own look.
 *
 * @param layers what it wears, from the skin outwards
 * @param hat    whether something sits on its head
 * @param marks  its veteran's marks, 0 to 4
 * @param idle   what it does with its time just now (null: nothing), and how far into it it is, 0 to 1
 */
public record CompanionLook(CompanionData data, List<Wear> layers, List<String> wears, boolean hat, int marks,
                            @Nullable Cart cart, Personality.@Nullable Idle idle, float idleAmount, float ownerYaw) {
    /** Levels from one step of marks to the next. */
    private static final int LEVELS_PER_MARK = 10;
    private static final int MAX_MARKS = 4;
    /** An idle act takes the first part of every such stretch of ticks. */
    private static final float IDLE_PERIOD = 120;
    private static final float IDLE_LENGTH = 60;

    /** One thing worn: its parts on the model and its texture's name. */
    public record Wear(String parts, String texture) {}

    /** The cart this frame: how far it swings out, its tilt, its wheels' turn (radians) and how it shakes (units). */
    public record Cart(float yaw, float pitch, float roll, float rattle) {}

    public boolean sad() {
        return data.hasForm(CompanionForms.BINDLE);
    }

    public boolean jumping() {
        return data.hasForm(CompanionForms.JUMPING);
    }

    /** A companion's look at this moment. */
    public static CompanionLook of(Mob mob, CompanionData data, float partialTicks) {
        List<Wear> layers = new ArrayList<>();
        data.form().ifPresent(form -> {
            if (form.equals(CompanionForms.BINDLE) || form.equals(CompanionForms.JUMPING)) {
                layers.add(new Wear("wear_" + form.getPath(), form.getPath()));
            }
        });
        CompanionCostumeItem costume = data.gear().costume().getItem() instanceof CompanionCostumeItem item ? item : null;
        boolean hat = costume != null && !costume.costume().getPath().equals("backpack");
        if (data.gear().armor().getItem() instanceof CompanionArmorItem plates) {
            layers.add(new Wear("wear_plates", "plates_" + tierName(plates.tier())));
            if (!hat) {
                // A hat is worn in the helm's place.
                layers.add(new Wear(CostumeParts.HELM, "helm_" + tierName(plates.tier())));
            }
        }
        if (costume != null) {
            layers.add(new Wear("wear_" + costume.costume().getPath(), costume.costume().getPath()));
        }
        float time = mob.tickCount + partialTicks;
        int seed = data.id().hashCode();
        int stretch = (int) Math.floor((time + (seed & 0xFF)) / IDLE_PERIOD);
        float into = (time + (seed & 0xFF)) - stretch * IDLE_PERIOD;
        Personality.Idle idle = null;
        float amount = 0;
        if (into < IDLE_LENGTH) {
            idle = data.personality().idle(roll(seed, stretch));
            amount = into / IDLE_LENGTH;
        }
        return new CompanionLook(data, List.copyOf(layers), layers.stream().map(Wear::parts).toList(), hat,
                Math.min(MAX_MARKS, data.level() / LEVELS_PER_MARK), data.hasCart() ? CartMotion.of(mob, partialTicks) : null, idle, amount,
                OwnerGaze.yaw(mob, data, partialTicks));
    }

    private static String tierName(ChitinGear.Tier tier) {
        return tier.name().toLowerCase(Locale.ROOT);
    }

    /** A number from 0 to 1 that is the same for the same ant and stretch of time. */
    private static float roll(int seed, int stretch) {
        int h = seed * 31 + stretch * 0x9E3779B9;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65536.0F;
    }
}
