package com.antcolonies.colony.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyAccess;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.resource.ColonyStorage;
import com.antcolonies.content.blockentity.ConnectorSettings;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.core.config.AntConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Colony Connectors: biological logistics between colonies. A connector is a chamber of its colony; connectors on the
 * same channel (a dye colour) in one dimension form a network. Every {@code connectors.transferInterval} ticks:
 *
 * <ol>
 *     <li><b>Food.</b> Supplying connectors offer what their colony has above its reserve; requesting connectors ask for
 *     what their colony lacks below its target. The hungriest colony (least food for its room) is served first, from the
 *     colony with the most to spare. Each connector moves at most its rate limit. Food is a number in each colony, so a
 *     transfer takes from one and adds to the other: it never makes or loses any. A colony never sends to itself, and a
 *     colony below its target is never a supplier in the same pass, so food can't loop.</li>
 *     <li><b>Items.</b> A sending connector moves the items its filter names out of its colony's Storage into the Storage
 *     of colonies with a receiving connector on the channel, only what fits (checked before anything is taken).</li>
 * </ol>
 *
 * Two connectors link only within {@code connectorRange} blocks of each other on x and on z (30 by default). Only
 * colonies whose Terrarium and connector are loaded take part: nothing is force-loaded, and a colony in an
 * unloaded chunk is shown as offline. Connectors use no power. The network remembers its connectors (position and
 * channel) in the level's saved data, so the Network page also lists the ones that are unloaded.
 */
public final class ConnectorNetwork {
    /** Each connector's last known channel, by position (per level, saved). */
    public static final class Registry extends SavedData {
        public static final Codec<Registry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().optionalFieldOf("connectors", List.of()).forGetter(Registry::entries)
        ).apply(i, Registry::new));
        public static final SavedDataType<Registry> TYPE = new SavedDataType<>(AntColonies.id("colony_connectors"), Registry::new, CODEC);

        record Entry(BlockPos pos, int channel) {
            static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                    Codec.INT.fieldOf("channel").forGetter(Entry::channel)
            ).apply(i, Entry::new));
        }

        private final Map<BlockPos, Integer> channels = new LinkedHashMap<>();

        Registry() {
        }

        private Registry(List<Entry> entries) {
            for (Entry entry : entries) {
                channels.put(entry.pos().immutable(), entry.channel());
            }
        }

        private List<Entry> entries() {
            List<Entry> list = new ArrayList<>();
            channels.forEach((pos, channel) -> list.add(new Entry(pos, channel)));
            return list;
        }

        public Map<BlockPos, Integer> channels() {
            return channels;
        }
    }

    /** One connector taking part in a pass. */
    private static final class Member {
        final StructureBlockEntity connector;
        final ConnectorSettings settings;
        final TerrariumBlockEntity terrarium;
        final Colony colony;
        final double capacity;
        double budget;

        Member(StructureBlockEntity connector, TerrariumBlockEntity terrarium, double minutes) {
            this.connector = connector;
            this.settings = connector.connector();
            this.terrarium = terrarium;
            this.colony = terrarium.colony();
            this.capacity = Math.max(1, colony.stats().foodCapacity() > 0 ? colony.stats().foodCapacity() : AntConfig.get(AntConfig.TERRARIUM_FOOD_CAPACITY));
            this.budget = settings.foodPerMinute() * minutes;
        }

        double share() {
            return colony.food() / capacity;
        }

        /** Food it can give: what its colony has above the reserve. */
        double spare() {
            return Math.max(0, colony.food() - capacity * settings.reserve / 100.0);
        }

        /** Food it wants: what its colony lacks below the target. */
        double need() {
            return Math.max(0, capacity * settings.target / 100.0 - colony.food());
        }
    }

    private ConnectorNetwork() {}

    public static Registry registry(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Registry.TYPE);
    }

    public static void register(ServerLevel level, BlockPos pos, int channel) {
        Registry registry = registry(level);
        Integer old = registry.channels.put(pos.immutable(), channel);
        if (old == null || old != channel) {
            registry.setDirty();
        }
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        Registry registry = registry(level);
        if (registry.channels.remove(pos) != null) {
            registry.setDirty();
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % AntConfig.get(AntConfig.CONNECTOR_INTERVAL) == 0) {
            tick(level);
        }
    }

    /** One pass over every channel of this level. Game tests call it directly. */
    public static void tick(ServerLevel level) {
        Registry registry = registry(level);
        if (registry.channels.isEmpty()) {
            return;
        }
        double minutes = AntConfig.get(AntConfig.CONNECTOR_INTERVAL) / 1200.0;
        Map<Integer, List<BlockPos>> byChannel = new HashMap<>();
        List<BlockPos> gone = new ArrayList<>();
        for (Map.Entry<BlockPos, Integer> entry : registry.channels.entrySet()) {
            BlockPos pos = entry.getKey();
            if (level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof StructureBlockEntity be && be.connector() != null)) {
                gone.add(pos); // broken while we weren't looking (or replaced)
                continue;
            }
            byChannel.computeIfAbsent(entry.getValue(), c -> new ArrayList<>()).add(pos);
        }
        if (!gone.isEmpty()) {
            gone.forEach(registry.channels::remove);
            registry.setDirty();
        }
        for (Map.Entry<Integer, List<BlockPos>> channel : byChannel.entrySet()) {
            pass(level, channel.getKey(), channel.getValue(), minutes);
        }
    }

    private static void pass(ServerLevel level, int channel, List<BlockPos> positions, double minutes) {
        List<Member> members = new ArrayList<>();
        List<BlockPos> offline = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof StructureBlockEntity connector) || connector.connector() == null) {
                offline.add(pos);
                continue;
            }
            BlockPos terrariumPos = connector.terrarium();
            if (terrariumPos == null || !level.isLoaded(terrariumPos) || !(level.getBlockEntity(terrariumPos) instanceof TerrariumBlockEntity terrarium)) {
                // A connector not joined to a colony (or whose Terrarium is unloaded) takes no part.
                connector.connector().members = 0;
                connector.connector().foodIn *= 0.5;
                connector.connector().foodOut *= 0.5;
                connector.connector().itemsMoved *= 0.5;
                continue;
            }
            members.add(new Member(connector, terrarium, minutes));
        }
        Map<StructureBlockEntity, double[]> moved = new HashMap<>();
        for (Member member : members) {
            moved.put(member.connector, new double[3]);
        }
        boolean[] paused = new boolean[members.size()];
        for (int i = 0; i < members.size(); i++) {
            // A redstone signal or the connector's own switch stops it.
            paused[i] = level.hasNeighborSignal(members.get(i).connector.getBlockPos());
        }

        // --- Food ---
        List<Member> receivers = new ArrayList<>();
        List<Member> suppliers = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            if (paused[i]) {
                continue;
            }
            if (member.settings.foodMode == ConnectorSettings.REQUEST && member.need() > 0) {
                receivers.add(member);
            } else if (member.settings.foodMode == ConnectorSettings.SUPPLY && member.spare() > 0) {
                suppliers.add(member);
            }
        }
        // Colonies that asked for food this pass never give any in it.
        suppliers.removeIf(supplier -> receivers.stream().anyMatch(receiver -> receiver.terrarium == supplier.terrarium));
        receivers.sort(Comparator.comparingDouble(Member::share));
        for (Member receiver : receivers) {
            suppliers.sort(Comparator.comparingDouble(Member::spare).reversed());
            for (Member supplier : suppliers) {
                if (supplier.terrarium == receiver.terrarium || !inRange(supplier.connector.getBlockPos(), receiver.connector.getBlockPos())) {
                    continue;
                }
                double amount = Math.min(Math.min(receiver.need(), receiver.budget), Math.min(supplier.spare(), supplier.budget));
                if (amount <= 0.0001) {
                    continue;
                }
                moveFood(supplier.colony, receiver.colony, amount);
                supplier.budget -= amount;
                receiver.budget -= amount;
                moved.get(supplier.connector)[1] += amount;
                moved.get(receiver.connector)[0] += amount;
                if (receiver.need() <= 0.0001 || receiver.budget <= 0.0001) {
                    break;
                }
            }
        }

        // --- Items ---
        int itemBudget = (int) Math.floor(AntConfig.get(AntConfig.CONNECTOR_ITEM_RATE) * minutes + level.getRandom().nextDouble());
        List<Member> sinks = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            if (!paused[i] && members.get(i).settings.itemMode == ConnectorSettings.RECEIVE) {
                sinks.add(members.get(i));
            }
        }
        for (int i = 0; i < members.size() && itemBudget > 0 && !sinks.isEmpty(); i++) {
            Member source = members.get(i);
            if (paused[i] || source.settings.itemMode != ConnectorSettings.SEND) {
                continue;
            }
            int sent = sendItems(level, source, sinks, itemBudget);
            moved.get(source.connector)[2] += sent;
        }

        // --- What it shows ---
        List<Colony.NetworkLink> links = new ArrayList<>();
        // Where each entry's connector is, so each colony lists only the colonies within reach of its own connector.
        List<BlockPos> linkConnectors = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            double[] m = moved.get(member.connector);
            ConnectorSettings settings = member.settings;
            // Per minute, smoothed over about ten passes.
            settings.foodIn = settings.foodIn * 0.9 + m[0] / minutes * 0.1;
            settings.foodOut = settings.foodOut * 0.9 + m[1] / minutes * 0.1;
            settings.itemsMoved = settings.itemsMoved * 0.9 + m[2] / minutes * 0.1;
            settings.members = (int) members.stream().filter(other -> inRange(other.connector.getBlockPos(), member.connector.getBlockPos()))
                    .map(other -> other.terrarium).filter(t -> t != member.terrarium).distinct().count();
            if (m[0] > 0 || m[1] > 0) {
                member.terrarium.setChanged();
            }
            member.connector.setAssignment(0, 0, paused[i] ? ColonyStatus.PAUSED.bit() : 0, m[0] + m[1]);
            links.add(new Colony.NetworkLink(member.terrarium.getBlockPos(), member.colony.species(), channel, (float) member.colony.food(),
                    (float) member.capacity, settings.foodMode, settings.itemMode, settings.reserve / 100F, settings.target / 100F,
                    (float) settings.foodIn, (float) settings.foodOut, true, false, paused[i]));
            linkConnectors.add(member.connector.getBlockPos());
        }
        for (BlockPos pos : offline) {
            links.add(new Colony.NetworkLink(pos, AntColonies.id("common"), channel, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false));
            linkConnectors.add(pos);
        }
        // Each colony on the channel learns about the network (its own entry marked), for its Network page.
        Map<TerrariumBlockEntity, List<Colony.NetworkLink>> perColony = new HashMap<>();
        for (Member member : members) {
            List<Colony.NetworkLink> view = perColony.computeIfAbsent(member.terrarium, t -> new ArrayList<>());
            for (int l = 0; l < links.size(); l++) {
                Colony.NetworkLink link = links.get(l);
                boolean self = link.online() && link.terrarium().equals(member.terrarium.getBlockPos());
                if (!self && !inRange(linkConnectors.get(l), member.connector.getBlockPos())) {
                    continue; // out of this connector's reach
                }
                if (self && view.stream().anyMatch(e -> e.self() && e.channel() == channel)) {
                    continue; // one entry for itself per channel, even with two connectors on it
                }
                view.add(self ? new Colony.NetworkLink(link.terrarium(), link.species(), link.channel(), link.food(), link.capacity(), link.mode(),
                        link.itemMode(), link.reserve(), link.target(), link.inPerMinute(), link.outPerMinute(), true, true, link.paused()) : link);
            }
        }
        long now = level.getGameTime();
        for (Map.Entry<TerrariumBlockEntity, List<Colony.NetworkLink>> entry : perColony.entrySet()) {
            Colony colony = entry.getKey().colony();
            List<Colony.NetworkLink> merged = new ArrayList<>();
            // Keep what the colony knows of its other channels (they pass on their own).
            colony.network().stream().filter(link -> link.channel() != channel && NETWORK_SEEN.getOrDefault(entry.getKey(), 0L) >= now - 200).forEach(merged::add);
            merged.addAll(entry.getValue());
            ColonyAccess.setNetwork(colony, merged);
            NETWORK_SEEN.put(entry.getKey(), now);
        }
    }

    /**
     * Whether two connectors are close enough to link: at most {@code connectorRange} blocks apart on x and on z (height
     * doesn't count).
     */
    public static boolean inRange(BlockPos a, BlockPos b) {
        int range = AntConfig.get(AntConfig.CONNECTOR_RANGE);
        return Math.abs(a.getX() - b.getX()) <= range && Math.abs(a.getZ() - b.getZ()) <= range;
    }

    /** When each colony last heard from a network (other channels' entries expire when it stops hearing). */
    private static final java.util.WeakHashMap<TerrariumBlockEntity, Long> NETWORK_SEEN = new java.util.WeakHashMap<>();

    private static void moveFood(Colony from, Colony to, double amount) {
        ColonyAccess.setFood(from, ColonyAccess.food(from) - amount, ColonyAccess.starvedMinutes(from));
        ColonyAccess.setFood(to, ColonyAccess.food(to) + amount, ColonyAccess.starvedMinutes(to));
    }

    /**
     * Moves up to {@code budget} items named by the source connector's filter out of its colony's Storage into the sinks'
     * Storage. Each stack is checked to fit before it is taken, so nothing is lost or made.
     */
    private static int sendItems(ServerLevel level, Member source, List<Member> sinks, int budget) {
        List<ItemStack> filter = new ArrayList<>();
        for (int slot = 0; slot < source.connector.getContainerSize(); slot++) {
            ItemStack stack = source.connector.getItem(slot);
            if (!stack.isEmpty()) {
                filter.add(stack);
            }
        }
        if (filter.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (StructureBlockEntity storage : ColonyStorage.storages(level, source.colony)) {
            for (int slot = 0; slot < storage.unlockedSlots() && sent < budget; slot++) {
                ItemStack stack = storage.getItem(slot);
                if (stack.isEmpty() || filter.stream().noneMatch(f -> ItemStack.isSameItemSameComponents(f, stack))) {
                    continue;
                }
                for (Member sink : sinks) {
                    if (sink.terrarium == source.terrarium || sent >= budget || stack.isEmpty()
                            || !inRange(sink.connector.getBlockPos(), source.connector.getBlockPos())) {
                        continue;
                    }
                    int amount = Math.min(budget - sent, Math.min(stack.getCount(), stack.getMaxStackSize()));
                    ItemStack moving = stack.copyWithCount(amount);
                    if (ColonyStorage.storages(level, sink.colony).isEmpty()
                            || !ColonyStorage.canFitStorage(level, sink.colony, moving)) {
                        continue;
                    }
                    stack.shrink(amount);
                    ItemStack left = ColonyStorage.insertIntoStorage(level, sink.colony, moving);
                    if (!left.isEmpty()) {
                        // canFit said it would fit; should it not, it goes back where it came from.
                        stack.grow(left.getCount());
                        amount -= left.getCount();
                    }
                    sent += amount;
                    storage.setChanged();
                    storage.contentsChanged();
                }
            }
        }
        return sent;
    }

    /** Whether a block is a Colony Connector (for the colony scan's kinds). */
    public static boolean isConnector(StructureBlockEntity be) {
        return be.kind() == StructureKind.COLONY_CONNECTOR;
    }
}
