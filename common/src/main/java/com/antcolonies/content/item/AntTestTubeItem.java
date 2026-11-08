package com.antcolonies.content.item;

import java.util.function.Consumer;

import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.core.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The filled tubes: caught ants (workers or soldiers, up to 16 of a species), a Queen (with her whole genome and
 * lineage), or a Genetic Sample (a copy of a Queen's genes). Their names and colors follow the species inside.
 */
public class AntTestTubeItem extends Item {
    public enum Contents {
        ANTS, QUEEN, SAMPLE
    }

    private final Contents contents;

    public AntTestTubeItem(Contents contents, Properties properties) {
        super(properties);
        this.contents = contents;
    }

    @Override
    public Component getName(ItemStack stack) {
        AntSample ants = stack.get(ModDataComponents.ANTS.get());
        QueenData queen = stack.get(ModDataComponents.QUEEN.get());
        if (contents == Contents.ANTS && ants != null) {
            return Component.translatable("item.antcolonies.ant_test_tube.of", Tooltips.species(ants.species()),
                    Component.translatable("caste.antcolonies." + ants.caste().getSerializedName()));
        }
        if (queen != null) {
            return Component.translatable(getDescriptionId() + ".of", Tooltips.species(queen.species()));
        }
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        QueenData queen = stack.get(ModDataComponents.QUEEN.get());
        return queen != null && queen.genome().rarity() >= 4;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        AntSample ants = stack.get(ModDataComponents.ANTS.get());
        if (ants != null) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.ant_test_tube.count", ants.count(),
                    Component.translatable("caste.antcolonies." + ants.caste().getSerializedName())).withStyle(ChatFormatting.GRAY));
            Tooltips.speciesFacts(context.registries(), ants.species(), tooltip, flag.hasShiftDown());
            tooltip.accept(Component.translatable("tooltip.antcolonies.ant_test_tube.use").withStyle(ChatFormatting.DARK_GRAY));
        }
        QueenData queen = stack.get(ModDataComponents.QUEEN.get());
        if (queen != null) {
            Tooltips.queen(queen, tooltip, flag.hasShiftDown(), context.registries());
            if (contents == Contents.SAMPLE) {
                tooltip.accept(Component.translatable("tooltip.antcolonies.genetic_sample.use").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (contents == Contents.QUEEN) {
            tooltip.accept(Component.translatable("tooltip.antcolonies.queen_test_tube.empty").withStyle(ChatFormatting.RED));
        }
    }
}
