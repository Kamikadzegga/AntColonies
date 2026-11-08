package com.antcolonies.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.antcolonies.AntColonies;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Wraps the long description lines of this mod's item tooltips, keeping their colors, so a tooltip stays a readable
 * column instead of a line across the whole screen. The item's name (the first line) is left alone.
 */
@EventBusSubscriber(modid = AntColonies.MODID, value = Dist.CLIENT)
public final class TooltipWrapper {
    /** The widest a description line may be, in GUI pixels. */
    private static final int WIDTH = 200;

    private TooltipWrapper() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event) {
        if (!AntColonies.MODID.equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getNamespace())) {
            return;
        }
        // Measuring text may bake font glyphs, which only the render thread may do. Recipe viewers build their search
        // index from tooltips on a worker thread (EMI does): leave those lines unwrapped, the words are the same.
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        List<Component> lines = event.getToolTip();
        List<Component> wrapped = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (i == 0 || font.width(line) <= WIDTH) {
                wrapped.add(line);
                continue;
            }
            for (FormattedText part : font.getSplitter().splitLines(line, WIDTH, Style.EMPTY)) {
                MutableComponent rebuilt = Component.empty();
                part.visit((style, text) -> {
                    rebuilt.append(Component.literal(text).withStyle(style));
                    return Optional.empty();
                }, Style.EMPTY);
                wrapped.add(rebuilt);
            }
        }
        lines.clear();
        lines.addAll(wrapped);
    }
}
