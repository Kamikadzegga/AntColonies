package com.antcolonies.content.menu;

import com.antcolonies.content.blockentity.ConnectorSettings;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Any colony chamber's GUI: its status and link, a job site's ants and their buttons, its own slots and its upgrade slots. */
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
    /** Colony Connectors: channel (CHANNEL + colour), food mode, reserve and target -/+, the next sending order, item mode. */
    public static final int CHANNEL = 20;
    public static final int FOOD_MODE = 40;
    public static final int RESERVE_LESS = 43;
    public static final int RESERVE_MORE = 44;
    public static final int TARGET_LESS = 45;
    public static final int TARGET_MORE = 46;
    public static final int CYCLE_ORDER = 47;
    public static final int ITEM_MODE = 50;
    /** Colony Connectors: package sizes and speed -/+, the other filter mode, a filter slot's stock -/+ (STOCK + slot). */
    public static final int FOOD_PACKAGE_LESS = 53;
    public static final int FOOD_PACKAGE_MORE = 54;
    public static final int ITEM_PACKAGE_LESS = 55;
    public static final int ITEM_PACKAGE_MORE = 56;
    public static final int SLOWER = 57;
    public static final int FASTER = 58;
    public static final int FILTER_MODE = 59;
    public static final int STOCK_LESS = 60;
    public static final int STOCK_MORE = 70;
    private static final int LAST_ACTION = STOCK_MORE + ConnectorSettings.FILTER_SLOTS - 1;
    public static final int UPGRADE_X = 184;
    public static final int STORE_Y = 88;
    /** A Colony Connector's screen is wider than the other chambers'; its filter slots and the inventory sit in its middle. */
    public static final int CONNECTOR_WIDTH = 252;
    public static final int CONNECTOR_SLOT_X = 45;
    public static final int FILTER_Y = 137;

    /** Menu button ids from here on scroll a Storage: SCROLL + the first row to show. */
    public static final int SCROLL = 100;

    private final StructureBlockEntity structure;
    private final ContainerData data;
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
            case FORAGING_SITE, NETHER_FORAGING_SITE, END_FORAGING_SITE, LIQUID_CHAMBER, HUNTING_GROUND, HARVESTING_SITE -> {
                for (int slot = 0; slot < kind.slots(); slot++) {
                    addSlot(new FilteredSlot(structure, slot, 8 + slot * 18, STORE_Y, stack -> true, 64));
                }
            }
            case COLONY_CONNECTOR -> {
                // The filter only names items: nothing goes in or comes out, a click marks the slot (see clicked).
                for (int slot = 0; slot < kind.slots(); slot++) {
                    addSlot(new FilteredSlot(structure, slot, CONNECTOR_SLOT_X + slot * 18, FILTER_Y, stack -> false, 1) {
                        @Override
                        public boolean mayPickup(Player player) {
                            return false;
                        }
                    });
                }
            }
            case QUEEN_CHAMBER -> {
                for (int slot = 0; slot < StructureKind.QUEEN_CHAMBER_MATERIALS; slot++) {
                    int s = slot;
                    addSlot(new FilteredSlot(structure, slot, 8 + slot * 18, 52, stack -> structure.canPlaceItem(s, stack), 64));
                }
                addSlot(new FilteredSlot(structure, StructureKind.QUEEN_CHAMBER_TUBE, 116, 52, stack -> stack.is(ModItems.TEST_TUBE.get()), 64));
                // The sample slots have a row of their own; the first comes before the output, as it always did.
                addSlot(sampleSlot(0));
                addSlot(new FilteredSlot(structure, StructureKind.QUEEN_CHAMBER_OUTPUT, 152, 52, stack -> false, 1));
                for (int i = 1; i < StructureKind.QUEEN_CHAMBER_SAMPLES.length; i++) {
                    addSlot(sampleSlot(i));
                }
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
        addPlayerInventory(inventory, kind == StructureKind.COLONY_CONNECTOR ? CONNECTOR_SLOT_X : 8, inventoryY(kind));
        addDataSlots(WideData.slots(data));
    }

    /** Where the Queen Chamber's row of sample slots is. */
    public static final int SAMPLE_Y = 74;

    private FilteredSlot sampleSlot(int index) {
        return new FilteredSlot(structure, StructureKind.QUEEN_CHAMBER_SAMPLES[index], 8 + index * 18, SAMPLE_Y,
                stack -> stack.is(ModItems.GENETIC_SAMPLE.get()), 1);
    }

    public static int inventoryY(StructureKind kind) {
        if (kind == StructureKind.COLONY_CONNECTOR) {
            return 170;
        }
        if (kind == StructureKind.STORAGE) {
            return 152;
        }
        // Nether and End sites have a line more under their store: the species they need, when the colony's are not.
        if (kind == StructureKind.QUEEN_CHAMBER) {
            // Under its two rows of slots, four lines on what the next Genetic Sample will do.
            return 144;
        }
        return kind.expedition() ? 145 : kind.siteStore() ? 136 : kind.job().any() ? 104 : 84;
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

    public int storageRows() {
        return (data.get(StructureBlockEntity.DATA_UNLOCKED) + 8) / 9;
    }

    public int maxScroll() {
        return Math.max(0, storageRows() - StorageWindow.ROWS);
    }

    public int firstRow() {
        return Math.clamp(scroll, 0, maxScroll());
    }

    /** Scrolls a Storage (the screen does this here and asks the server to do the same). */
    public void scrollTo(int row) {
        scroll = Math.clamp(row, 0, maxScroll());
    }

    /** A job site's store is where its ants put their finds: nothing is shift-clicked into it. */
    @Override
    protected boolean quickMovesInto(int slot, ItemStack stack) {
        return !(structure.kind().siteStore() && slot < structure.kind().slots());
    }

    /** Shift-clicking into a Storage fills the whole Storage, not only the rows on screen: slots holding the item first. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (structure.kind() == StructureKind.COLONY_CONNECTOR) {
            markFilter(slots.get(index).getItem());
            return ItemStack.EMPTY;
        }
        if (window == null || index < blockSlots || structure.acceptsUpgrade(slots.get(index).getItem()) || !slots.get(index).hasItem()) {
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

    private static void connector(StructureBlockEntity site, int action) {
        ConnectorSettings settings = site.connector();
        if (settings == null) {
            return;
        }
        if (action >= CHANNEL && action < CHANNEL + 16) {
            settings.channel = action - CHANNEL;
        } else if (action >= FOOD_MODE && action <= FOOD_MODE + 2) {
            settings.foodMode = action - FOOD_MODE;
        } else if (action >= ITEM_MODE && action <= ITEM_MODE + 2) {
            settings.itemMode = action - ITEM_MODE;
        } else if (action >= STOCK_LESS && action < STOCK_LESS + ConnectorSettings.FILTER_SLOTS) {
            settings.stepStock(action - STOCK_LESS, -1);
        } else if (action >= STOCK_MORE && action < STOCK_MORE + ConnectorSettings.FILTER_SLOTS) {
            if (!site.getItem(action - STOCK_MORE).isEmpty()) {
                settings.stepStock(action - STOCK_MORE, 1);
            }
        } else {
            int foodCap = AntConfig.get(AntConfig.CONNECTOR_FOOD_PACKAGE);
            int itemCap = AntConfig.get(AntConfig.CONNECTOR_ITEM_PACKAGE);
            switch (action) {
                case RESERVE_LESS -> settings.reserve = Math.max(0, settings.reserve - 5);
                case RESERVE_MORE -> settings.reserve = Math.min(100, settings.reserve + 5);
                case TARGET_LESS -> settings.target = Math.max(0, settings.target - 5);
                case TARGET_MORE -> settings.target = Math.min(100, settings.target + 5);
                case CYCLE_ORDER -> settings.order = (settings.order + 1) % 3;
                case FOOD_PACKAGE_LESS -> settings.foodPackage = Math.max(0, settings.foodPackage - 1);
                case FOOD_PACKAGE_MORE -> settings.foodPackage = larger(ConnectorSettings.FOOD_PACKAGES, settings.foodPackage, foodCap);
                case ITEM_PACKAGE_LESS -> settings.itemPackage = Math.max(0, settings.itemPackage - 1);
                case ITEM_PACKAGE_MORE -> settings.itemPackage = larger(ConnectorSettings.ITEM_PACKAGES, settings.itemPackage, itemCap);
                case SLOWER -> settings.interval = Math.max(0, settings.interval - 1);
                case FASTER -> settings.interval = Math.min(ConnectorSettings.INTERVALS.length - 1, settings.interval + 1);
                case FILTER_MODE -> settings.filterMode = 1 - settings.filterMode;
                default -> {
                    return;
                }
            }
        }
        site.connectorChanged();
    }

    /** The next package size up, unless the config's cap makes it no larger. */
    private static int larger(int[] sizes, int index, int cap) {
        return index + 1 < sizes.length && sizes[index] < cap ? index + 1 : index;
    }

    /** Names an item in the first free filter slot of a Colony Connector, unless the filter already names it. */
    private void markFilter(ItemStack stack) {
        int free = -1;
        for (int slot = ConnectorSettings.FILTER_SLOTS - 1; slot >= 0; slot--) {
            ItemStack named = structure.getItem(slot);
            if (ItemStack.isSameItemSameComponents(named, stack)) {
                return;
            }
            if (named.isEmpty()) {
                free = slot;
            }
        }
        if (free >= 0 && !stack.isEmpty()) {
            structure.setFilter(free, stack);
        }
    }

    /** A player's new name for the Colony Connector whose screen they have open. */
    public void rename(Player player, String name) {
        ConnectorSettings settings = structure.connector();
        if (settings != null && stillValid(player)) {
            settings.name = ConnectorSettings.clean(name);
            structure.connectorChanged();
        }
    }

    /** Dragging a stack over a connector's filter leaves it alone. */
    @Override
    public boolean canDragTo(Slot slot) {
        return !(structure.kind() == StructureKind.COLONY_CONNECTOR && slot.container == structure) && super.canDragTo(slot);
    }

    /** Swapping a hotbar slot with a Storage slot that holds more than a stack would put it all in the hotbar: not allowed. */
    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (structure.kind() == StructureKind.COLONY_CONNECTOR && slotId >= 0 && slotId < ConnectorSettings.FILTER_SLOTS) {
            // A filter slot: a click with an item names it, a click with an empty hand (or a shift-click) clears the slot.
            if (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE) {
                structure.setFilter(slotId, clickType == ContainerInput.QUICK_MOVE ? ItemStack.EMPTY : getCarried());
            }
            return;
        }
        if (clickType == ContainerInput.SWAP && slotId >= 0 && slotId < slots.size()
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
