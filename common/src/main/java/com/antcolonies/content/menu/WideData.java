package com.antcolonies.content.menu;

import net.minecraft.world.inventory.ContainerData;

/** Sends each int of a {@link ContainerData} as two data slots: vanilla sends 16-bit shorts and would cut off the high half. */
public final class WideData {
    private WideData() {
    }

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
