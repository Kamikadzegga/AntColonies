package com.antcolonies.core.registry;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.content.item.AntSample;
import com.antcolonies.content.item.TweezersItem;
import com.antcolonies.colony.species.Caste;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's tab: every item, then a wild Queen and a tube of workers of each species (made from the loaded data). */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AntColonies.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.antcolonies"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.ANT_HEAD.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ModItems.ITEMS.getEntries().forEach(item -> {
                    if (item.get() != ModItems.ANT_TEST_TUBE.get() && item.get() != ModItems.QUEEN_TEST_TUBE.get()
                            && item.get() != ModItems.GENETIC_SAMPLE.get() && item.get() != ModItems.GUARD_ANT.get() && item.get() != ModItems.ANT_HEAD.get()) {
                        output.accept(item.get());
                    }
                });
                net.minecraft.world.item.ItemStack guard = new net.minecraft.world.item.ItemStack(ModItems.GUARD_ANT.get());
                guard.set(ModDataComponents.GUARD.get(), new com.antcolonies.content.item.GuardData(com.antcolonies.core.registry.ModRegistries.COMMON, 5, -1));
                output.accept(guard);
                // A fixed seed, so the tab shows the same Queens every time.
                RandomSource random = RandomSource.create(42);
                parameters.holders().lookup(ModRegistries.SPECIES).ifPresent(lookup -> lookup.listElements().forEach(species -> {
                    output.accept(TweezersItem.queenTube(Genetics.wildQueen(species.key().identifier(), random)));
                    output.accept(TweezersItem.antTube(new AntSample(species.key().identifier(), Caste.WORKER, AntSample.PINCH)));
                }));
            })
            .build());

    private ModCreativeTabs() {}
}
