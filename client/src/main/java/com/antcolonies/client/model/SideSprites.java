package com.antcolonies.client.model;

import com.antcolonies.colony.species.Caste;
import com.antcolonies.colony.species.Costume;

/**
 * The layout of entity/ant_side.png, the side-view ants of tunnels and screens: 24 x 12 cells, four walking frames
 * across. Rows 0 to 8 are a body, gaster and crystal row per caste, 10 to 15 the Liquid Ants' crops and plates, and
 * from row 16 a row per costume and caste, in its own colours. tools/TexGen.java paints it (sideSprites).
 */
public final class SideSprites {
    public static final int CELL_W = 24;
    public static final int CELL_H = 12;
    public static final int COSTUME_ROW = 16;
    public static final int TEX_W = CELL_W * 4;
    public static final int TEX_H = CELL_H * (COSTUME_ROW + Costume.values().length * Caste.values().length);

    /** The row of a costume's layer over one caste's sprite. */
    public static int costumeRow(Costume costume, Caste caste) {
        return COSTUME_ROW + costume.ordinal() * Caste.values().length + caste.ordinal();
    }

    private SideSprites() {}
}
