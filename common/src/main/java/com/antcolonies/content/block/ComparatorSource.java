package com.antcolonies.content.block;

/**
 * A block entity that gives comparators its own reading (0 to 15) instead of how full its slots are. Comparators are
 * told to look again whenever the block entity calls setChanged(), which these do whenever the values behind the
 * reading change.
 */
public interface ComparatorSource {
    int comparatorSignal();

    /** A fill level as a comparator reading: 0 when empty, 1 to 15 otherwise, 15 when full. */
    static int fill(double amount, double capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        return 1 + (int) Math.floor(Math.min(1, amount / capacity) * 14);
    }
}
