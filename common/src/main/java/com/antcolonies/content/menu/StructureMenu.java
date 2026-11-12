package com.antcolonies.content.menu;

import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Any colony chamber's GUI: its status and link, for working sites the ants it has and wants (+/- buttons), its own slots (Storage, a Foraging Site's store, Queen
 * Chamber) and its upgrade slots on the right.
 */
public class StructureMenu extends BaseMenu {
    public static final int LESS = 0;
    public static final int MORE = 1;
    public static final int LESS_10 = 2;
    public static final int MORE_10 = 3;
    public static final int AUTO = 4;
    public static final int LESS_100 = 5;
    public static final int MORE_100 = 6;
    /** Job sites: the Foraging Site's next mode, turning the site on or off, the next priority. */
    public static final int CYCLE_MODE = 7;
    public static final int TOGGLE = 8;
    public static final int CYCLE_PRIORITY = 9;
    /** Colony Connectors: channel (CHANNEL + colour), food mode, reserve and target -/+, rate limit, item mode. */
    public static final int CHANNEL = 20;
    public static final int FOOD_MODE = 40;
    public static final int RESERVE_LESS = 43;
    public static final int RESERVE_MORE = 44;
    public static final int TARGET_LESS = 45;
    public static final int TARGET_MORE = 46;
    public static final int CYCLE_RATE = 47;
    public static final int ITEM_MODE = 50;
    private static final int LAST_ACTION = 52;
    public static final int UPGRADE_X = 184;
    /** Where a Foraging Site's store slots are. */
    public static final int STORE_Y = 88;
    /** Where a Colony Connector's filter slots are. */
    public static final int FILTER_Y = 112;

    /** Menu button ids from here on scroll a Storage: SCROLL + the first row to show. */
    public static final int SCROLL = 100;

    private final StructureBlockEntity structure;
    private final ContainerData data;
    /** A Storage's scrolling view of six rows, and the row it starts at. */
    private @Nullable StorageWindow window;
    private int scroll;

    public StructureMenu(int containerId, Inventory inventory, StructureBlockEntity structure) {
        this(containerId, inventory, structure, structure.data());
    }

    private StructureMenu(int containerId, Inventory inventory, StructureBlockEntity structure, ContainerData data) {
        super(ModMenus.STRUCTURE.get(), containerId);
        this.structure = structure;
        this.data = data;
        StructureKind kind = structure.kind();
        switch (kind) {
            case STORAGE -> {
                window = new StorageWindow(structure, this::firstRow);
                for (int slot = 0; slot < StorageWindow.SIZE; slot++) {
                    int s = slot;
                    addSlot(new StorageSlot(window, structure, slot, 8 + (slot % 9) * 18, 18 + (slot / 9) * 18,
                            () -> window.storageSlot(s) < data.get(StructureBlockEntity.DATA_UNLOCKED)));
                }
            }
            case FORAGING_SITE, NETHER_FORAGING_SITE, END_FORAGING_SITE -> {
                for (int slot = 0; slot < kind.slots(); slot++) {
                    addSlot(new FilteredSlot(structure, slot, 8 + slot * 18, STORE_Y, stack -> true, 64));
                }
            }
            case COLONY_CONNECTOR -> {
                // The filter: one of each item to send (they stay in the connector).
                for (int slot = 0; slot < kind.slots(); slot++) {
                    addSlot(new FilteredSlot(structure, slot, 8 + slot * 18, FILTER_Y, stack -> true, 1));
                }
            }
            case QUEEN_CHAMBER -> {
                for (int slot = 0; slot < StructureKind.QUEEN_CHAMBER_MATERIALS; slot++) {
                    addSlot(new FilteredSlot(structure, slot, 8 + slot * 18, 52, stack -> true, 64));
                }
                addSlot(new FilteredSlot(structure, StructureKind.QUEEN_CHAMBER_TUBE, 98, 52, stack -> stack.is(ModItems.TEST_TUBE.get()), 64));
                addSlot(new FilteredSlot(structure, StructureKind.QUEEN_CHAMBER_SAMPLE, 116, 52, stack -> stack.is(ModItems.GENETIC_SAMPLE.get()), 1));
                addSlot(new FilteredSlot(structure, StructureKind.QUEEN_CHAMBER_OUTPUT, 152, 52, stack -> false, 1));
            }
            default -> {
            }
        }
        for (int slot = 0; slot < kind.upgradeSlots(); slot++) {
            int s = slot;
            addUpgradeSlot(new FilteredSlot(structure.upgrades(), slot, UPGRADE_X, 18 + slot * 18, structure::acceptsUpgrade, 1) {
                // A Storage Upgrade stays in while the Storage holds more than it could without it.
                @Override
                public boolean mayPickup(Player player) {
                    return player.level().isClientSide() ? !upgradeHeld(s) : structure.canRemoveUpgrade(s);
                }
            });
        }
        addPlayerInventory(inventory, 8, inventoryY(kind));
        addDataSlots(WideData.slots(data));
    }

    public static int inventoryY(StructureKind kind) {
        if (kind == StructureKind.STORAGE || kind == StructureKind.COLONY_CONNECTOR) {
            return 152;
        }
        return kind.foraging() ? 136 : kind.job().any() ? 104 : 84;
    }

    public static StructureMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        StructureBlockEntity structure = inventory.player.level().getBlockEntity(pos) instanceof StructureBlockEntity be ? be
                : new StructureBlockEntity(pos, ModBlocks.STRUCTURES.get(StructureKind.STORAGE).get().defaultBlockState());
        return new StructureMenu(containerId, inventory, structure, new SimpleContainerData(StructureBlockEntity.DATA_COUNT));
    }

    public StructureBlockEntity structure() {
        return structure;
    }

    public int data(int id) {
        return data.get(id);
    }

    /** Whether the upgrade in this upgrade slot is held in (the server says so: it sees the whole Storage). */
    public boolean upgradeHeld(int upgradeSlot) {
        return (data.get(StructureBlockEntity.DATA_UPGRADES_HELD) & (1 << upgradeSlot)) != 0;
    }

    // --- Storage scrolling ---

    /** The rows of open Storage slots. */
    public int storageRows() {
        return (data.get(StructureBlockEntity.DATA_UNLOCKED) + 8) / 9;
    }

    /** The furthest a Storage scrolls: its last open row at the bottom (0 when all fit). */
    public int maxScroll() {
        return Math.max(0, storageRows() - StorageWindow.ROWS);
    }

    /** The Storage row the window starts at. */
    public int firstRow() {
        return Math.clamp(scroll, 0, maxScroll());
    }

    /** Scrolls a Storage (the screen does this here and asks the server to do the same). */
    public void scrollTo(int row) {
        scroll = Math.clamp(row, 0, maxScroll());
    }

    /**
     * Shift-clicking into a Storage fills the whole Storage, not only the rows on screen: first the slots already
     * holding the item, then empty ones.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (window == null || index < blockSlots || isUpgrade(slots.get(index).getItem()) || !slots.get(index).hasItem()) {
            return super.quickMoveStack(player, index);
        }
        Slot from = slots.get(index);
        ItemStack stack = from.getItem();
        ItemStack original = stack.copy();
        int open = structure.unlockedSlots();
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int slot = 0; slot < open && !stack.isEmpty(); slot++) {
                ItemStack held = structure.getItem(slot);
                if (pass == 0 ? !ItemStack.isSameItemSameComponents(held, stack) : !held.isEmpty()) {
                    continue;
                }
                int moving = Math.min(stack.getCount(), structure.slotLimit(stack) - held.getCount());
                if (moving > 0) {
                    structure.setItem(slot, stack.copyWithCount(held.getCount() + moving));
                    stack.shrink(moving);
                }
            }
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY);
        } else {
            from.setChanged();
        }
        from.onTake(player, stack);
        return ItemStack.EMPTY;
    }

    /** One of the assignment buttons, for this GUI and the Terrarium's job list. */
    public static void adjust(StructureBlockEntity site, int action) {
        int current = site.requested();
        int requested = current < 0 ? site.capacity() : current;
        int wanted = switch (action) {
            case LESS -> Math.max(0, requested - 1);
            case MORE -> requested + 1;
            case LESS_10 -> Math.max(0, requested - 10);
            case MORE_10 -> requested + 10;
            case LESS_100 -> Math.max(0, requested - 100);
            case MORE_100 -> requested + 100;
            case AUTO -> current < 0 ? site.capacity() : -1;
            default -> current;
        };
        switch (action) {
            case CYCLE_MODE -> {
                if (site.kind() == StructureKind.FORAGING_SITE) {
                    site.setMode(site.mode().next());
                }
                return;
            }
            case TOGGLE -> {
                site.setEnabled(!site.enabled());
                return;
            }
            case CYCLE_PRIORITY -> {
                site.setPriority((site.priority() + 1) % 3);
                return;
            }
            default -> {
            }
        }
        if (action >= CHANNEL && action <= LAST_ACTION) {
            connector(site, action);
            return;
        }
        site.setRequested(wanted);
        site.contentsChanged();
    }

    /** A Colony Connector's buttons. */
    private static void connector(StructureBlockEntity site, int action) {
        com.antcolonies.content.blockentity.ConnectorSettings settings = site.connector();
        if (settings == null) {
            return;
        }
        if (action >= CHANNEL && action < CHANNEL + 16) {
            settings.channel = action - CHANNEL;
        } else if (action >= FOOD_MODE && action <= FOOD_MODE + 2) {
            settings.foodMode = action - FOOD_MODE;
        } else if (action >= ITEM_MODE && action <= ITEM_MODE + 2) {
            settings.itemMode = action - ITEM_MODE;
        } else {
            switch (action) {
                case RESERVE_LESS -> settings.reserve = Math.max(0, settings.reserve - 5);
                case RESERVE_MORE -> settings.reserve = Math.min(100, settings.reserve + 5);
                case TARGET_LESS -> settings.target = Math.max(0, settings.target - 5);
                case TARGET_MORE -> settings.target = Math.min(100, settings.target + 5);
                case CYCLE_RATE -> settings.rateLimit = (settings.rateLimit + 1) % com.antcolonies.content.blockentity.ConnectorSettings.RATES.length;
                default -> {
                    return;
                }
            }
        }
        site.connectorChanged();
    }

    /** Swapping a hotbar slot with a Storage slot that holds more than a stack would put it all in the hotbar: not allowed. */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput clickType, Player player) {
        if (clickType == net.minecraft.world.inventory.ContainerInput.SWAP && slotId >= 0 && slotId < slots.size()
                && slots.get(slotId) instanceof StorageSlot storage && storage.overfull()) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (window != null && id >= SCROLL) {
            scrollTo(id - SCROLL);
            // The slots now show other Storage slots: send them all, even where they look the same as before.
            sendAllDataToRemote();
            return true;
        }
        if (structure.kind() == StructureKind.COLONY_CONNECTOR) {
            if (id < CHANNEL || id > LAST_ACTION) {
                return false;
            }
            adjust(structure, id);
            return true;
        }
        if (!structure.kind().job().any() || id < 0 || id > CYCLE_PRIORITY) {
            return false;
        }
        adjust(structure, id);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return structure.stillValid(player);
    }
}
