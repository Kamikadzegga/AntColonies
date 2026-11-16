package com.antcolonies.content.blockentity;

import com.antcolonies.core.config.AntConfig;

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
    /** Food per minute a connector may move; the last is the config's maximum. */
    public static final int[] RATES = {250, 1000, 3000, -1};

    /** A dye colour's id: connectors on one channel in a dimension form a network. */
    public int channel = DyeColor.WHITE.getId();
    /** {@link #OFF}, {@link #SUPPLY} or {@link #REQUEST}. */
    public int foodMode = OFF;
    /** Percent of its food room: it supplies what is above {@code reserve} and requests while below {@code target}. */
    public int reserve = 50;
    public int target = 75;
    /** An index into {@link #RATES}. */
    public int rateLimit = 2;
    /** {@link #OFF}, {@link #SEND} or {@link #RECEIVE}. */
    public int itemMode = OFF;

    // --- What it moved (not saved): per minute, smoothed over the last passes ---
    public double foodIn;
    public double foodOut;
    public double itemsMoved;
    /** Other colonies online on its channel at the last pass. */
    public int members;

    /** The most food per minute it moves. */
    public int foodPerMinute() {
        int max = AntConfig.get(AntConfig.CONNECTOR_FOOD_RATE);
        int rate = RATES[Math.clamp(rateLimit, 0, RATES.length - 1)];
        return rate < 0 ? max : Math.min(rate, max);
    }

    public void save(ValueOutput output) {
        ValueOutput child = output.child("connector");
        child.putInt("channel", channel);
        child.putInt("food_mode", foodMode);
        child.putInt("reserve", reserve);
        child.putInt("target", target);
        child.putInt("rate", rateLimit);
        child.putInt("item_mode", itemMode);
    }

    public void load(ValueInput input) {
        input.child("connector").ifPresent(child -> {
            channel = Math.clamp(child.getIntOr("channel", 0), 0, 15);
            foodMode = Math.clamp(child.getIntOr("food_mode", OFF), 0, 2);
            reserve = Math.clamp(child.getIntOr("reserve", 50), 0, 100);
            target = Math.clamp(child.getIntOr("target", 75), 0, 100);
            rateLimit = Math.clamp(child.getIntOr("rate", 2), 0, RATES.length - 1);
            itemMode = Math.clamp(child.getIntOr("item_mode", OFF), 0, 2);
        });
    }
}
