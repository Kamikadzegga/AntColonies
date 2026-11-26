package com.antcolonies.content.blockentity;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.network.ConnectorNote;
import com.antcolonies.core.config.AntConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A Colony Connector's saved settings, and what it moved lately (for its screen and the Network page). */
public final class ConnectorSettings {
    public static final int OFF = 0;
    public static final int SUPPLY = 1;
    public static final int REQUEST = 2;
    public static final int SEND = 1;
    public static final int RECEIVE = 2;
    /** Filter modes: send only the filter's items, or everything but them. */
    public static final int ONLY = 0;
    public static final int ALL_BUT = 1;
    /** Who a sender serves: every receiver each time, one receiver a time in turns, or the colony lowest on food first. */
    public static final int EVERYONE = 0;
    public static final int IN_TURNS = 1;
    public static final int HUNGRIEST = 2;
    /** Food in one package, items in one package, seconds between two packages. */
    public static final int[] FOOD_PACKAGES = {10, 25, 50, 100, 250, 500, 1000, 2500};
    public static final int[] ITEM_PACKAGES = {1, 4, 8, 16, 32, 64, 128, 256};
    public static final int[] INTERVALS = {60, 30, 10, 5, 2, 1};
    /** How many of a filter item may be told to stay in the sender's Storage. */
    public static final int[] STOCKS = {0, 1, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096};
    public static final int FILTER_SLOTS = 9;
    public static final int MAX_NAME = 20;

    /** A dye colour's id: connectors on one channel in a dimension form a network. */
    public int channel = DyeColor.WHITE.getId();
    /** What its colony is called on the network; empty for its species' name. */
    public String name = "";
    /** {@link #OFF}, {@link #SUPPLY} or {@link #REQUEST}. */
    public int foodMode = OFF;
    /** Percent of its food room: it sends what is above {@code reserve} and receives while below {@code target}. */
    public int reserve = 50;
    public int target = 75;
    /** Indexes into {@link #FOOD_PACKAGES}, {@link #ITEM_PACKAGES} and {@link #INTERVALS}. */
    public int foodPackage = 2;
    public int itemPackage = 2;
    public int interval = INTERVALS.length - 1;
    /** {@link #OFF}, {@link #SEND} or {@link #RECEIVE}. */
    public int itemMode = OFF;
    /** {@link #ONLY} or {@link #ALL_BUT}. */
    public int filterMode = ONLY;
    /** {@link #EVERYONE}, {@link #IN_TURNS} or {@link #HUNGRIEST}. */
    public int order = EVERYONE;
    /** Per filter slot: how many of that item stay in its colony's Storage. */
    public final int[] stock = new int[FILTER_SLOTS];
    /** Saved before the filter became marks: its slots still hold real items, to be handed back. */
    public boolean heldItems;

    // --- What it moved (not saved): per minute, smoothed over the last passes ---
    public double foodIn;
    public double foodOut;
    public double itemsIn;
    public double itemsOut;
    /** Other colonies online on its channel at the last pass. */
    public int members;
    /** Why food and items move or don't, as of the last pass. */
    public ConnectorNote foodNote = ConnectorNote.OFF;
    public ConnectorNote itemNote = ConnectorNote.OFF;
    /** Game times: when its last package left, and until when a package is on its way in. */
    public long lastFood = Long.MIN_VALUE;
    public long lastItems = Long.MIN_VALUE;
    public long foodInUntil;
    public long itemsInUntil;
    /** Whose turn it is to be served first, for food and for items. */
    public int foodTurn;
    public int itemTurn;
    /** The connectors it sends to (synced: the client draws the links). */
    public List<BlockPos> targets = List.of();

    public int foodPerPackage() {
        return Math.min(FOOD_PACKAGES[Math.clamp(foodPackage, 0, FOOD_PACKAGES.length - 1)], AntConfig.get(AntConfig.CONNECTOR_FOOD_PACKAGE));
    }

    public int itemsPerPackage() {
        return Math.min(ITEM_PACKAGES[Math.clamp(itemPackage, 0, ITEM_PACKAGES.length - 1)], AntConfig.get(AntConfig.CONNECTOR_ITEM_PACKAGE));
    }

    public int intervalTicks() {
        return INTERVALS[Math.clamp(interval, 0, INTERVALS.length - 1)] * 20;
    }

    /** Whether the wait after its last food package is over, by the Speed it has now. */
    public boolean foodDue(long now) {
        return now >= lastFood + intervalTicks();
    }

    public boolean itemsDue(long now) {
        return now >= lastItems + intervalTicks();
    }

    /** The next (or previous) stock amount of a filter slot. */
    public void stepStock(int slot, int direction) {
        int at = 0;
        while (at < STOCKS.length - 1 && STOCKS[at] < stock[slot]) {
            at++;
        }
        stock[slot] = STOCKS[Math.clamp(at + direction, 0, STOCKS.length - 1)];
    }

    public void save(ValueOutput output) {
        ValueOutput child = output.child("connector");
        child.putInt("channel", channel);
        child.putString("name", name);
        child.putInt("food_mode", foodMode);
        child.putInt("reserve", reserve);
        child.putInt("target", target);
        child.putInt("food_package", foodPackage);
        child.putInt("item_package", itemPackage);
        child.putInt("interval", interval);
        child.putInt("item_mode", itemMode);
        child.putInt("filter_mode", filterMode);
        child.putInt("order", order);
        child.putIntArray("stock", stock);
        if (heldItems) {
            child.putBoolean("held_items", true);
        }
        ValueOutput.TypedOutputList<BlockPos> list = child.list("targets", BlockPos.CODEC);
        targets.forEach(list::add);
    }

    public void load(ValueInput input) {
        input.child("connector").ifPresent(child -> {
            channel = Math.clamp(child.getIntOr("channel", 0), 0, 15);
            name = clean(child.getStringOr("name", ""));
            foodMode = Math.clamp(child.getIntOr("food_mode", OFF), 0, 2);
            reserve = Math.clamp(child.getIntOr("reserve", 50), 0, 100);
            target = Math.clamp(child.getIntOr("target", 75), 0, 100);
            foodPackage = Math.clamp(child.getIntOr("food_package", 2), 0, FOOD_PACKAGES.length - 1);
            itemPackage = Math.clamp(child.getIntOr("item_package", 2), 0, ITEM_PACKAGES.length - 1);
            interval = Math.clamp(child.getIntOr("interval", INTERVALS.length - 1), 0, INTERVALS.length - 1);
            itemMode = Math.clamp(child.getIntOr("item_mode", OFF), 0, 2);
            filterMode = Math.clamp(child.getIntOr("filter_mode", ONLY), 0, 1);
            order = Math.clamp(child.getIntOr("order", EVERYONE), 0, 2);
            // Saves from before the rework have no order.
            heldItems = child.getInt("order").isEmpty() || child.getBooleanOr("held_items", false);
            int[] saved = child.getIntArray("stock").orElse(new int[0]);
            for (int slot = 0; slot < FILTER_SLOTS; slot++) {
                stock[slot] = slot < saved.length ? Math.clamp(saved[slot], 0, STOCKS[STOCKS.length - 1]) : 0;
            }
            List<BlockPos> list = new ArrayList<>();
            child.listOrEmpty("targets", BlockPos.CODEC).forEach(list::add);
            targets = List.copyOf(list);
        });
    }

    /** A typed name, trimmed to what the screens have room for. */
    public static String clean(String typed) {
        String trimmed = typed.strip();
        return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
    }
}
