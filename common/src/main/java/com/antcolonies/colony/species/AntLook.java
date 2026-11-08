package com.antcolonies.colony.species;

import java.util.Locale;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

/**
 * How a species' ants are drawn: one shared model per caste, tinted. Mineral species add crystal growths in their
 * crystal color and may glow; size scales the whole ant. Colors are written "#RRGGBB" (a number or [r, g, b] works too).
 *
 * @param body    head, thorax and legs
 * @param gaster  the abdomen
 * @param accent  the pattern over the gaster
 * @param crystal crystal growths on the back, if the species has them
 * @param glow    whether the crystals glow in the dark
 */
public record AntLook(int body, int gaster, int accent, Pattern pattern, Optional<Integer> crystal, boolean glow, float size) {
    public static final Codec<Integer> COLOR = Codec.either(Codec.STRING, ExtraCodecs.RGB_COLOR_CODEC).comapFlatMap(
            either -> either.map(AntLook::parseHex, DataResult::success),
            color -> Either.left(String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF)));

    public static final Codec<AntLook> CODEC = RecordCodecBuilder.create(i -> i.group(
            COLOR.fieldOf("body").forGetter(AntLook::body),
            COLOR.fieldOf("gaster").forGetter(AntLook::gaster),
            COLOR.optionalFieldOf("accent", 0x000000).forGetter(AntLook::accent),
            Pattern.CODEC.optionalFieldOf("pattern", Pattern.PLAIN).forGetter(AntLook::pattern),
            COLOR.optionalFieldOf("crystal").forGetter(AntLook::crystal),
            Codec.BOOL.optionalFieldOf("glow", false).forGetter(AntLook::glow),
            Codec.floatRange(0.25F, 4.0F).optionalFieldOf("size", 1.0F).forGetter(AntLook::size)
    ).apply(i, AntLook::new));

    /** Drawn for species that are unknown (a removed datapack). */
    public static final AntLook UNKNOWN = new AntLook(0x3A2A20, 0x2A1E18, 0x000000, Pattern.PLAIN, Optional.empty(), false, 1.0F);

    private static DataResult<Integer> parseHex(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) {
            return DataResult.error(() -> "Not a #RRGGBB color: " + text);
        }
        try {
            return DataResult.success(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a #RRGGBB color: " + text);
        }
    }
}
