package com.antcolonies.core.registry;

import java.util.function.Supplier;

import com.antcolonies.AntColonies;
import com.antcolonies.content.menu.CentrifugeMenu;
import com.antcolonies.content.menu.GeneratorMenu;
import com.antcolonies.content.menu.GeneticLabMenu;
import com.antcolonies.content.menu.StructureMenu;
import com.antcolonies.content.menu.TerrariumMenu;
import com.antcolonies.content.menu.UpgradeStationMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AntColonies.MODID);

    public static final Supplier<MenuType<TerrariumMenu>> TERRARIUM = MENUS.register("terrarium",
            () -> IMenuTypeExtension.create(TerrariumMenu::fromNetwork));
    public static final Supplier<MenuType<StructureMenu>> STRUCTURE = MENUS.register("structure",
            () -> IMenuTypeExtension.create(StructureMenu::fromNetwork));
    public static final Supplier<MenuType<GeneticLabMenu>> GENETIC_LAB = MENUS.register("genetic_lab",
            () -> IMenuTypeExtension.create(GeneticLabMenu::fromNetwork));
    public static final Supplier<MenuType<UpgradeStationMenu>> UPGRADE_STATION = MENUS.register("upgrade_station",
            () -> IMenuTypeExtension.create(UpgradeStationMenu::fromNetwork));

    public static final Supplier<MenuType<GeneratorMenu>> GENERATOR = MENUS.register("generator",
            () -> IMenuTypeExtension.create(GeneratorMenu::fromNetwork));

    public static final Supplier<MenuType<CentrifugeMenu>> CENTRIFUGE = MENUS.register("centrifuge",
            () -> IMenuTypeExtension.create(CentrifugeMenu::fromNetwork));

    private ModMenus() {}
}
