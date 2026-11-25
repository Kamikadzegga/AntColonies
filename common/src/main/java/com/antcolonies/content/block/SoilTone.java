package com.antcolonies.content.block;

import java.util.Locale;
import java.util.Set;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** The colour anthill soil takes from the ground it lies in, so an anthill's own earth suits every biome. */
public enum SoilTone implements StringRepresentable {
    EARTH(MapColor.DIRT),
    SAND(MapColor.SAND, MapColor.SAND),
    RED(MapColor.COLOR_ORANGE, MapColor.COLOR_ORANGE, MapColor.TERRACOTTA_ORANGE, MapColor.TERRACOTTA_RED, MapColor.TERRACOTTA_YELLOW),
    FROST(MapColor.CLAY, MapColor.SNOW, MapColor.ICE, MapColor.QUARTZ, MapColor.TERRACOTTA_WHITE),
    STONE(MapColor.STONE, MapColor.STONE, MapColor.CLAY),
    PALE(MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_LIGHT_GRAY),
    DARK(MapColor.DEEPSLATE, MapColor.DEEPSLATE, MapColor.COLOR_BLACK, MapColor.COLOR_GRAY, MapColor.TERRACOTTA_BLACK),
    NETHER(MapColor.NETHER, MapColor.NETHER, MapColor.CRIMSON_NYLIUM, MapColor.CRIMSON_STEM, MapColor.CRIMSON_HYPHAE);

    /** The soil's own colour on a map, and the map colours of the ground it is the tone of. */
    public final MapColor mapColor;
    private final Set<MapColor> grounds;

    SoilTone(MapColor mapColor, MapColor... grounds) {
        this.mapColor = mapColor;
        this.grounds = Set.of(grounds);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The tone that suits a block of ground: another soil block's own, else by the ground's colour on a map, else earth. */
    public static SoilTone of(BlockState ground) {
        if (ground.hasProperty(AnthillSoilBlock.TONE)) {
            return ground.getValue(AnthillSoilBlock.TONE);
        }
        MapColor color = ground.getBlock().defaultMapColor();
        for (SoilTone tone : values()) {
            if (tone.grounds.contains(color)) {
                return tone;
            }
        }
        return EARTH;
    }
}
