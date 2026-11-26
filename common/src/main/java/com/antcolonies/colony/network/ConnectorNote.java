package com.antcolonies.colony.network;

import java.util.Locale;

/** Why a Colony Connector moves food or items, or doesn't: one line on its screen. */
public enum ConnectorNote {
    OFF(Tone.QUIET),
    NO_COLONY(Tone.PROBLEM),
    PAUSED(Tone.WAITING),
    /** No other connector on its channel. */
    ALONE(Tone.PROBLEM),
    /** Connectors on its channel, but none within range. */
    FAR(Tone.PROBLEM),
    /** The others on its channel are in chunks that aren't loaded. */
    UNLOADED(Tone.WAITING),
    NO_RECEIVER(Tone.PROBLEM),
    NO_SENDER(Tone.PROBLEM),
    /** A sender with no food above what it keeps. */
    AT_KEEP(Tone.WAITING),
    RECEIVERS_FULL(Tone.WAITING),
    RECEIVERS_PAUSED(Tone.WAITING),
    /** A receiver at its fill level. */
    FILLED(Tone.GOOD),
    SENDERS_EMPTY(Tone.WAITING),
    NO_STORAGE(Tone.PROBLEM),
    NO_FILTER(Tone.PROBLEM),
    NOTHING_TO_SEND(Tone.WAITING),
    /** Items to send, but no room for them where they should go. */
    NO_ROOM(Tone.WAITING),
    SENDING(Tone.GOOD),
    RECEIVING(Tone.GOOD),
    /** Linked and able to receive, nothing on its way. */
    READY(Tone.QUIET);

    public enum Tone { GOOD, QUIET, WAITING, PROBLEM }

    private final Tone tone;

    ConnectorNote(Tone tone) {
        this.tone = tone;
    }

    public Tone tone() {
        return tone;
    }

    public String translationKey() {
        return "gui.antcolonies.connector.note." + name().toLowerCase(Locale.ROOT);
    }

    public static ConnectorNote byId(int id) {
        ConnectorNote[] all = values();
        return all[Math.clamp(id, 0, all.length - 1)];
    }
}
