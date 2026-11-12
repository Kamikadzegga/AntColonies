package com.antcolonies.content.blockentity;

import com.antcolonies.core.config.AntConfig;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Colony Connector's settings, saved with it, and what it moved lately (for its screen and the Network page).
 *
 * <ul>
 *     <li>{@code channel}: a dye colour; connectors on the same channel in one dimension form a network.</li>
 *     <li>{@code foodMode}: {@link #OFF}, {@link #SUPPLY} (gives the food it has above {@code reserve}% of its room) or
 *     {@link #REQUEST} (takes food while below {@code target}% of its room).</li>
 *     <li>{@code rateLimit}: an index into {@link #RATES}, the most food per minute it moves.</li>
 *     <li>{@code itemMode}: {@link #OFF}, {@link #SEND} (the items its filter names leave its colony's Storage) or
 *     {@link #RECEIVE} (items sent on the channel arrive in its colony's Storage).</li>
 * </ul>
 */
public final class ConnectorSettings {
    public static final int OFF = 0;
    public static final int SUPPLY = 1;
    public static final int REQUEST = 2;
    public static final int SEND = 1;
    public static final int RECEIVE = 2;
    /** Food per minute a connector may move; the last is the config's maximum. */
    public static final int[] RATES = {250, 1000, 3000, -1};

    public int channel = DyeColor.WHITE.getId();
    public int foodMode = OFF;
    public int reserve = 50;
    public int target = 75;
    public int rateLimit = 2;
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
