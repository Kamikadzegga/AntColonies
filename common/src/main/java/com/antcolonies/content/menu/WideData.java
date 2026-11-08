package com.antcolonies.content.menu;

import net.minecraft.world.inventory.ContainerData;

/**
 * Vanilla sends menu data slots as 16-bit shorts, which cuts off status flags above bit 15 and any value over 32767
 * (energy in FE, for example). This sends each int of a {@link ContainerData} as two data slots, its low and high half.
 * Pass the int data to {@link #slots} for {@code addDataSlots}: on the server the slots read the ints, on the client
 * they write the halves back into them.
 */
public final class WideData {
    private WideData() {
    }

    /** Twice as many 16-bit slots as {@code ints} has values. */
    public static ContainerData slots(ContainerData ints) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                int value = ints.get(index >> 1);
                return (index & 1) == 0 ? value & 0xFFFF : value >>> 16;
            }

            @Override
            public void set(int index, int half) {
                int value = ints.get(index >> 1);
                // The packet carries a signed short: keep only its 16 bits.
                value = (index & 1) == 0 ? (value & 0xFFFF0000) | (half & 0xFFFF) : (value & 0xFFFF) | (half & 0xFFFF) << 16;
                ints.set(index >> 1, value);
            }

            @Override
            public int getCount() {
                return ints.getCount() * 2;
            }
        };
    }
}
