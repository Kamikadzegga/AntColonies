package com.antcolonies.content.menu;

import com.antcolonies.content.blockentity.CentrifugeBlockEntity;
import com.antcolonies.content.recipe.CentrifugingRecipe;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** The Droplet Centrifuge: its droplet slot; its FE, run, tank and status are synced for the screen. */
public class CentrifugeMenu extends BaseMenu {
    public static final int INPUT_X = 20;
    public static final int INPUT_Y = 40;
    public static final int INVENTORY_Y = 126;

    private final CentrifugeBlockEntity centrifuge;
    private final ContainerData data;

    public CentrifugeMenu(int containerId, Inventory inventory, CentrifugeBlockEntity centrifuge) {
        this(containerId, inventory, centrifuge, centrifuge.data());
    }

    private CentrifugeMenu(int containerId, Inventory inventory, CentrifugeBlockEntity centrifuge, ContainerData data) {
        super(ModMenus.CENTRIFUGE.get(), containerId);
        this.centrifuge = centrifuge;
        this.data = data;
        // Only what a Centrifuge recipe takes: the server knows its recipes, the client the copy it was sent.
        addSlot(new FilteredSlot(centrifuge, 0, INPUT_X, INPUT_Y, stack -> CentrifugingRecipe.find(inventory.player.level(), stack) != null, 64));
        addPlayerInventory(inventory, 8, INVENTORY_Y);
        addDataSlots(WideData.slots(data));
    }

    public static CentrifugeMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        CentrifugeBlockEntity centrifuge = inventory.player.level().getBlockEntity(pos) instanceof CentrifugeBlockEntity be ? be
                : new CentrifugeBlockEntity(pos, ModBlocks.CENTRIFUGE.get().defaultBlockState());
        return new CentrifugeMenu(containerId, inventory, centrifuge, new SimpleContainerData(CentrifugeBlockEntity.DATA_COUNT));
    }

    public int data(int id) {
        return data.get(id);
    }

    public CentrifugeBlockEntity centrifuge() {
        return centrifuge;
    }

    @Override
    public boolean stillValid(Player player) {
        return centrifuge.stillValid(player);
    }
}
