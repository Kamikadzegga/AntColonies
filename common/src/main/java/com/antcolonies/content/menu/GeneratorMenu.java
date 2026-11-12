package com.antcolonies.content.menu;

import com.antcolonies.content.blockentity.GeneratorBlockEntity;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** The Generator: one fuel slot; its stored FE, burn time and output are synced for the screen. */
public class GeneratorMenu extends BaseMenu {
    public static final int FUEL_X = 44;
    public static final int FUEL_Y = 44;

    private final GeneratorBlockEntity generator;
    private final ContainerData data;

    public GeneratorMenu(int containerId, Inventory inventory, GeneratorBlockEntity generator) {
        this(containerId, inventory, generator, generator.data());
    }

    private GeneratorMenu(int containerId, Inventory inventory, GeneratorBlockEntity generator, ContainerData data) {
        super(ModMenus.GENERATOR.get(), containerId);
        this.generator = generator;
        this.data = data;
        addSlot(new FilteredSlot(generator, 0, FUEL_X, FUEL_Y, stack -> stack.getBurnTime(null, inventory.player.level().fuelValues()) > 0, 64));
        addPlayerInventory(inventory, 8, 84);
        addDataSlots(WideData.slots(data));
    }

    public static GeneratorMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        GeneratorBlockEntity generator = inventory.player.level().getBlockEntity(pos) instanceof GeneratorBlockEntity be ? be
                : new GeneratorBlockEntity(pos, ModBlocks.GENERATOR.get().defaultBlockState());
        return new GeneratorMenu(containerId, inventory, generator, new SimpleContainerData(GeneratorBlockEntity.DATA_COUNT));
    }

    public int data(int id) {
        return data.get(id);
    }

    @Override
    public boolean stillValid(Player player) {
        return generator.stillValid(player);
    }
}
