package com.antcolonies.colony.model;

import java.util.Arrays;
import java.util.Locale;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a colony looked like over the last while, for the Terrarium's Statistics page: one sample of each {@link Series}
 * every {@link #INTERVAL} ticks of game time, the last {@link #SIZE} of them (half an hour). The samples are the
 * colony's own numbers after a simulation step (population, food, and the step's per-minute rates), never estimates.
 *
 * <p>Saved with the colony, so the charts survive a restart. Clients get it only while someone has the Terrarium's
 * screen open (see {@code TerrariumBlockEntity}): it is the biggest part of a colony.
 */
public final class ColonyHistory {
    /** Ticks between samples (30 seconds). */
    public static final int INTERVAL = 600;
    /** Samples kept per series. */
    public static final int SIZE = 60;

    public enum Series {
        ADULTS(0xFF6CF08A),
        BROOD(0xFFFFD27A),
        FOOD(0xFFE0B040),
        EGGS_LAID(0xFFFFF2C0),
        FINDS(0xFF4FE3FF),
        FOOD_IN(0xFF8BE36C),
        FOOD_OUT(0xFFFF8A6E);

        private final int color;

        Series(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }

        public String translationKey() {
            return "gui.antcolonies.series." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final float[][] values = new float[Series.values().length][SIZE];
    /** Samples taken so far, up to {@link #SIZE}; the newest is at {@code (head - 1) mod SIZE}. */
    private int count;
    private int head;
    private long lastSample = Long.MIN_VALUE / 2;

    /** Takes a sample if one is due at {@code time}. */
    public void sample(long time, Colony colony) {
        if (time - lastSample < INTERVAL) {
            return;
        }
        lastSample = time;
        Colony.Stats stats = colony.stats();
        float[] sample = {
                (float) colony.exactAdults(),
                (float) (colony.eggs() + colony.larvae()),
                (float) colony.food(),
                (float) stats.eggsLaid(),
                (float) stats.finds(),
                (float) stats.foodIn(),
                (float) stats.foodOut()};
        for (int series = 0; series < sample.length; series++) {
            values[series][head] = sample[series];
        }
        head = (head + 1) % SIZE;
        count = Math.min(SIZE, count + 1);
    }

    public int count() {
        return count;
    }

    /** The samples of a series, oldest first. */
    public float[] values(Series series) {
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = values[series.ordinal()][Math.floorMod(head - count + i, SIZE)];
        }
        return out;
    }

    public float latest(Series series) {
        return count == 0 ? 0 : values[series.ordinal()][Math.floorMod(head - 1, SIZE)];
    }

    public void save(ValueOutput output) {
        ValueOutput child = output.child("history");
        child.putInt("count", count);
        child.putLong("last", lastSample);
        for (Series series : Series.values()) {
            float[] ordered = values(series);
            int[] bits = new int[ordered.length];
            for (int i = 0; i < ordered.length; i++) {
                bits[i] = Float.floatToIntBits(ordered[i]);
            }
            child.putIntArray(series.name().toLowerCase(Locale.ROOT), bits);
        }
    }

    /** Loads a saved or synced history; with none in {@code input} it keeps what it has. */
    public void load(ValueInput input) {
        input.child("history").ifPresent(child -> {
            int saved = Math.clamp(child.getIntOr("count", 0), 0, SIZE);
            for (float[] row : values) {
                Arrays.fill(row, 0);
            }
            int loaded = saved;
            for (Series series : Series.values()) {
                int[] bits = child.getIntArray(series.name().toLowerCase(Locale.ROOT)).orElse(new int[0]);
                loaded = Math.min(loaded, bits.length);
                for (int i = 0; i < Math.min(saved, bits.length); i++) {
                    values[series.ordinal()][i] = Float.intBitsToFloat(bits[i]);
                }
            }
            count = loaded;
            head = loaded % SIZE;
            lastSample = child.getLongOr("last", Long.MIN_VALUE / 2);
        });
    }
}
