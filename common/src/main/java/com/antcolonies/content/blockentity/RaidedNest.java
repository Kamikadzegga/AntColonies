package com.antcolonies.content.blockentity;

/** A nest that Tweezers took ants from: it knows the game time its ants are back, for the timer a player is shown. */
public interface RaidedNest {
    /** The game time the nest has recovered, or 0 when it was never raided. */
    long recoveredAt();

    void setRecoveredAt(long gameTime);
}
