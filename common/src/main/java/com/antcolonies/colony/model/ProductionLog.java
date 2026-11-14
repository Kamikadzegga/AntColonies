package com.antcolonies.colony.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a colony really brought in over the last half hour, item by item: every item its ants put into its stores
 * (finds, harvests, hunting loot, chitin, droplets), counted per minute of game time, the last {@link #MINUTES} minutes
 * kept. The Terrarium's Production page shows it as items per minute, so the numbers stay there when the Storage is
 * emptied (by hand, hoppers or pipes) and are what came in, not an estimate.
 *
 * <p>Saved with the colony. Clients get it only while someone has the Terrarium's screen open, with the rest of the
 * colony's details.
 */
public final class ProductionLog {
    /** Minutes kept. */
    public static final int MINUTES = 30;
    /** Ticks in a minute. */
    public static final int MINUTE = 1200;
    /** Most items sent to a client. */
    private static final int SYNCED = 64;

    /** Per item, a ring of counts: slot {@code minute mod MINUTES} holds what came in during that minute. */
    private final Map<Item, int[]> counts = new LinkedHashMap<>();
    /** The latest minute the rings are up to date for (every slot older than {@link #MINUTES} before it is zero). */
    private long newest = Long.MIN_VALUE;
    /** The game time of the first thing in the log, or -1 while it is empty: a young log averages over its own age. */
    private long start = -1;

    /** One item's part of the log, as saved: its counts for the minutes up to the newest, oldest first. */
    private record Entry(Item item, List<Integer> counts) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Entry::item),
                Codec.INT.listOf().fieldOf("counts").forGetter(Entry::counts)
        ).apply(i, Entry::new));
    }

    /** An item and how many of it came in within the window. */
    public record Total(Item item, long count) {}

    /** Counts {@code count} of an item as brought in at game time {@code time}. */
    public void record(long time, Item item, int count) {
        if (count <= 0) {
            return;
        }
        advance(Math.floorDiv(time, MINUTE));
        if (start < 0) {
            start = time;
        }
        counts.computeIfAbsent(item, key -> new int[MINUTES])[slot(newest)] += count;
    }

    /** Moves the rings on to {@code minute}: the slots of the minutes passed are cleared, items with nothing left go. */
    private void advance(long minute) {
        if (newest == Long.MIN_VALUE || minute - newest >= MINUTES) {
            counts.clear();
            start = -1;
            newest = minute;
            return;
        }
        if (minute <= newest) {
            return;
        }
        for (long passed = newest + 1; passed <= minute; passed++) {
            int slot = slot(passed);
            for (int[] ring : counts.values()) {
                ring[slot] = 0;
            }
        }
        newest = minute;
        counts.values().removeIf(ring -> Arrays.stream(ring).allMatch(value -> value == 0));
        if (counts.isEmpty()) {
            start = -1;
        }
    }

    private static int slot(long minute) {
        return (int) Math.floorMod(minute, (long) MINUTES);
    }

    /** How many of an item came in during the {@link #MINUTES} minutes up to game time {@code time}. */
    public long total(long time, Item item) {
        int[] ring = counts.get(item);
        return ring == null ? 0 : total(ring, Math.floorDiv(time, MINUTE));
    }

    private long total(int[] ring, long minuteNow) {
        if (newest == Long.MIN_VALUE) {
            return 0;
        }
        long sum = 0;
        // The slots that are both filled (within MINUTES up to the newest) and still inside the window at this time.
        for (long minute = Math.max(newest - MINUTES + 1, minuteNow - MINUTES + 1); minute <= newest; minute++) {
            sum += ring[slot(minute)];
        }
        return sum;
    }

    /** Every item that came in within the window at {@code time}, with how many; most first. */
    public List<Total> totals(long time) {
        long minuteNow = Math.floorDiv(time, MINUTE);
        List<Total> totals = new ArrayList<>();
        counts.forEach((item, ring) -> {
            long sum = total(ring, minuteNow);
            if (sum > 0) {
                totals.add(new Total(item, sum));
            }
        });
        totals.sort((a, b) -> Long.compare(b.count(), a.count()));
        return totals;
    }

    /** Whether nothing came in within the window at {@code time}. */
    public boolean isEmpty(long time) {
        return totals(time).isEmpty();
    }

    /**
     * How many minutes the totals at {@code time} cover: the log's own age while it is younger than {@link #MINUTES}
     * minutes (so a colony that began ten minutes ago is averaged over ten), never less than one.
     */
    public double minutes(long time) {
        if (start < 0) {
            return 1;
        }
        long from = Math.max(start, (Math.floorDiv(time, MINUTE) - MINUTES + 1) * MINUTE);
        return Math.clamp((time - from) / (double) MINUTE, 1, MINUTES);
    }

    /** An item's average per minute over the window at {@code time}. */
    public double perMinute(long time, Item item) {
        return total(time, item) / minutes(time);
    }

    public void save(ValueOutput output) {
        ValueOutput child = output.child("production");
        child.putLong("newest", newest);
        child.putLong("start", start);
        ValueOutput.TypedOutputList<Entry> list = child.list("items", Entry.CODEC);
        int sent = 0;
        for (Map.Entry<Item, int[]> entry : counts.entrySet()) {
            if (sent++ >= SYNCED) {
                break;
            }
            List<Integer> ordered = new ArrayList<>(MINUTES);
            for (long minute = newest - MINUTES + 1; minute <= newest; minute++) {
                ordered.add(entry.getValue()[slot(minute)]);
            }
            list.add(new Entry(entry.getKey(), ordered));
        }
    }

    /** Loads a saved or synced log; with none in {@code input} it keeps what it has. */
    public void load(ValueInput input) {
        input.child("production").ifPresent(child -> {
            counts.clear();
            newest = child.getLongOr("newest", Long.MIN_VALUE);
            start = child.getLongOr("start", -1);
            if (newest == Long.MIN_VALUE) {
                start = -1;
                return;
            }
            for (Entry entry : child.listOrEmpty("items", Entry.CODEC)) {
                int[] ring = new int[MINUTES];
                for (int i = 0; i < Math.min(MINUTES, entry.counts().size()); i++) {
                    ring[slot(newest - MINUTES + 1 + i)] = Math.max(0, entry.counts().get(i));
                }
                counts.put(entry.item(), ring);
            }
        });
    }
}
