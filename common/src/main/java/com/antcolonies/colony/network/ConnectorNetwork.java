package com.antcolonies.colony.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

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
import org.jspecify.annotations.Nullable;

/**
 * Colony Connectors: connectors on the same channel in one dimension move food and items between their colonies.
 * Each sender serves each receiver by that receiver's own settings. Only loaded colonies take part.
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
        final BlockPos pos;
        final double capacity;
        final boolean paused;
        /** The connectors of other colonies within range. */
        final List<Member> peers = new ArrayList<>();
        double foodIn;
        double foodOut;
        int itemsIn;
        int itemsOut;
        /** A sender of items: its Storage held something to send, a receiver takes that kind, and some of it found no room. */
        boolean itemsMatched;
        boolean itemsWanted;
        boolean itemsBlocked;

        Member(StructureBlockEntity connector, TerrariumBlockEntity terrarium, boolean paused) {
            this.connector = connector;
            this.settings = connector.connector();
            this.terrarium = terrarium;
            this.colony = terrarium.colony();
            this.pos = connector.getBlockPos();
            this.capacity = Math.max(1,
                    colony.stats().foodCapacity() > 0 ? colony.stats().foodCapacity() : AntConfig.get(AntConfig.TERRARIUM_FOOD_CAPACITY));
            this.paused = paused;
        }

        double share() {
            return colony.food() / capacity;
        }

        double spare() {
            return Math.max(0, colony.food() - capacity * settings.reserve / 100.0);
        }

        /** Food still missing up to its fill level, counting what waits in the Formicarium's food slot. */
        double need() {
            ItemStack waiting = terrarium.getItem(TerrariumBlockEntity.SLOT_FOOD);
            return Math.max(0, capacity * settings.target / 100.0 - colony.food() - waiting.getCount() * ColonyStorage.foodValue(waiting));
        }

        boolean sendsFood() {
            return !paused && settings.foodMode == ConnectorSettings.SUPPLY;
        }

        boolean takesFood() {
            return !paused && settings.foodMode == ConnectorSettings.REQUEST;
        }

        boolean sendsItems() {
            return !paused && settings.itemMode == ConnectorSettings.SEND;
        }

        boolean takesItems() {
            return !paused && settings.itemMode == ConnectorSettings.RECEIVE;
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
            tick(level, level.getGameTime());
        }
    }

    /** One pass in which every sender's package is due. Game tests call it directly. */
    public static void tick(ServerLevel level) {
        run(level, level.getGameTime(), true);
    }

    /** One pass at a given game time: a sender sends only when its next package is due. */
    public static void tick(ServerLevel level, long now) {
        run(level, now, false);
    }

    private static void run(ServerLevel level, long now, boolean allDue) {
        Registry registry = registry(level);
        forgetQuietChannels(level);
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
            pass(level, channel.getKey(), channel.getValue(), minutes, now, allDue);
        }
    }

    private static void pass(ServerLevel level, int channel, List<BlockPos> positions, double minutes, long now, boolean allDue) {
        List<Member> members = new ArrayList<>();
        List<BlockPos> offline = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof StructureBlockEntity connector) || connector.connector() == null) {
                offline.add(pos);
                continue;
            }
            BlockPos terrariumPos = connector.terrarium();
            if (terrariumPos == null || !level.isLoaded(terrariumPos)
                    || !(level.getBlockEntity(terrariumPos) instanceof TerrariumBlockEntity terrarium)) {
                standAlone(connector);
                continue;
            }
            members.add(new Member(connector, terrarium, level.hasNeighborSignal(pos)));
        }
        for (Member member : members) {
            for (Member other : members) {
                if (other.terrarium != member.terrarium && inRange(other.pos, member.pos)) {
                    member.peers.add(other);
                }
            }
        }
        sendFood(members, now, allDue);
        sendItems(level, members, now, allDue);
        for (Member member : members) {
            ConnectorSettings settings = member.settings;
            ConnectorNote lonely = lonely(member, members, offline);
            settings.foodNote = foodNote(member, lonely, now);
            settings.itemNote = itemNote(level, member, lonely, now);
            setTargets(member.connector, targets(member));
            // Per minute, smoothed over about ten passes.
            settings.foodIn = settings.foodIn * 0.9 + member.foodIn / minutes * 0.1;
            settings.foodOut = settings.foodOut * 0.9 + member.foodOut / minutes * 0.1;
            settings.itemsIn = settings.itemsIn * 0.9 + member.itemsIn / minutes * 0.1;
            settings.itemsOut = settings.itemsOut * 0.9 + member.itemsOut / minutes * 0.1;
            settings.members = (int) member.peers.stream().map(peer -> peer.terrarium).distinct().count();
            if (member.foodIn > 0 || member.foodOut > 0) {
                member.terrarium.setChanged();
            }
            member.connector.setAssignment(0, 0, member.paused ? ColonyStatus.PAUSED.bit() : 0, member.foodIn + member.foodOut);
        }
        report(channel, members, offline, now);
    }

    /** A connector with no colony (or whose Formicarium isn't loaded) takes no part. */
    private static void standAlone(StructureBlockEntity connector) {
        ConnectorSettings settings = connector.connector();
        settings.members = 0;
        settings.foodIn *= 0.5;
        settings.foodOut *= 0.5;
        settings.itemsIn *= 0.5;
        settings.itemsOut *= 0.5;
        settings.foodNote = ConnectorNote.NO_COLONY;
        settings.itemNote = ConnectorNote.NO_COLONY;
        setTargets(connector, List.of());
    }

    // --- Food ---

    /** Each sender serves the receivers in range in its own order: a package each, or one package in turns or to the hungriest. */
    private static void sendFood(List<Member> members, long now, boolean allDue) {
        // A colony that takes food on this channel gives none on it in the same pass.
        List<TerrariumBlockEntity> hungry = members.stream().filter(m -> m.takesFood() && m.need() > 0).map(m -> m.terrarium).toList();
        for (Member sender : members) {
            if (!sender.sendsFood() || hungry.contains(sender.terrarium) || !allDue && !sender.settings.foodDue(now)) {
                continue;
            }
            double spare = sender.spare();
            int packageSize = sender.settings.foodPerPackage();
            List<Member> takers = new ArrayList<>();
            double wanted = 0;
            for (Member peer : sender.peers) {
                if (peer.takesFood() && peer.need() > 0.0001) {
                    takers.add(peer);
                    wanted += Math.min(packageSize, peer.need());
                }
            }
            if (takers.isEmpty() || spare <= 0.0001) {
                continue;
            }
            boolean everyone = sender.settings.order == ConnectorSettings.EVERYONE;
            inOrder(takers, sender.settings.order, sender.settings.foodTurn++);
            // A package each, shared out by need when the sender is short; or one package, handed down the line.
            double scale = Math.min(1, spare / wanted);
            double left = Math.min(packageSize, spare);
            for (Member taker : takers) {
                double amount = everyone ? Math.min(packageSize, taker.need()) * scale : Math.min(left, taker.need());
                if (amount <= 0.0001) {
                    continue;
                }
                left -= amount;
                moveFood(sender.colony, taker.colony, amount);
                sender.foodOut += amount;
                taker.foodIn += amount;
                taker.settings.foodInUntil = Math.max(taker.settings.foodInUntil, now + sender.settings.intervalTicks());
            }
            sender.settings.lastFood = now;
        }
    }

    /** Puts receivers in the order a sender serves them: taking turns, or the colony lowest on food first. */
    private static void inOrder(List<Member> takers, int order, int turn) {
        if (order == ConnectorSettings.HUNGRIEST) {
            takers.sort(Comparator.comparingDouble(Member::share));
        } else {
            Collections.rotate(takers, -(turn % takers.size()));
        }
    }

    private static void moveFood(Colony from, Colony to, double amount) {
        ColonyAccess.setFood(from, ColonyAccess.food(from) - amount, ColonyAccess.starvedMinutes(from));
        ColonyAccess.setFood(to, ColonyAccess.food(to) + amount, ColonyAccess.starvedMinutes(to));
    }

    private static ConnectorNote foodNote(Member member, @Nullable ConnectorNote lonely, long now) {
        ConnectorSettings settings = member.settings;
        if (settings.foodMode == ConnectorSettings.OFF) {
            return ConnectorNote.OFF;
        }
        if (member.paused) {
            return ConnectorNote.PAUSED;
        }
        if (lonely != null) {
            return lonely;
        }
        if (settings.foodMode == ConnectorSettings.SUPPLY) {
            List<Member> takers = member.peers.stream().filter(peer -> peer.settings.foodMode == ConnectorSettings.REQUEST).toList();
            if (takers.isEmpty()) {
                return ConnectorNote.NO_RECEIVER;
            }
            if (member.foodOut > 0 || !settings.foodDue(now)) {
                return ConnectorNote.SENDING;
            }
            if (takers.stream().allMatch(taker -> taker.paused)) {
                return ConnectorNote.RECEIVERS_PAUSED;
            }
            if (member.spare() <= 0.0001) {
                return ConnectorNote.AT_KEEP;
            }
            return takers.stream().allMatch(taker -> taker.paused || taker.need() <= 0.0001) ? ConnectorNote.RECEIVERS_FULL : ConnectorNote.READY;
        }
        List<Member> senders = member.peers.stream().filter(peer -> peer.settings.foodMode == ConnectorSettings.SUPPLY).toList();
        if (senders.isEmpty()) {
            return ConnectorNote.NO_SENDER;
        }
        if (member.need() <= 0.0001) {
            return ConnectorNote.FILLED;
        }
        if (member.foodIn > 0 || now < settings.foodInUntil) {
            return ConnectorNote.RECEIVING;
        }
        return senders.stream().allMatch(sender -> sender.paused || sender.spare() <= 0.0001) ? ConnectorNote.SENDERS_EMPTY : ConnectorNote.READY;
    }

    // --- Items ---

    /** Each sender hands the receivers in range the items its filter lets go: a package each, or one package in its order. */
    private static void sendItems(ServerLevel level, List<Member> members, long now, boolean allDue) {
        for (Member sender : members) {
            ConnectorSettings settings = sender.settings;
            if (!sender.sendsItems() || !allDue && !settings.itemsDue(now)) {
                continue;
            }
            List<Member> takers = new ArrayList<>(sender.peers.stream().filter(peer -> peer.takesItems() || peer.takesFood()).toList());
            List<StructureBlockEntity> storages = ColonyStorage.storages(level, sender.colony);
            boolean only = settings.filterMode == ConnectorSettings.ONLY;
            if (takers.isEmpty() || storages.isEmpty() || only && filterEmpty(sender.connector)) {
                continue;
            }
            // Even with a package each they take turns, so a sender short of items doesn't always serve the same one first.
            boolean everyone = settings.order == ConnectorSettings.EVERYONE;
            inOrder(takers, settings.order, settings.itemTurn++);
            int[] budget = new int[everyone ? takers.size() : 1];
            Arrays.fill(budget, settings.itemsPerPackage());
            int[] allowance = only ? allowance(sender.connector, storages) : null;
            for (StructureBlockEntity storage : storages) {
                boolean changed = false;
                for (int slot = 0; slot < storage.unlockedSlots(); slot++) {
                    ItemStack stack = storage.getItem(slot);
                    int entry = stack.isEmpty() ? -1 : filterSlot(sender.connector, stack);
                    if (stack.isEmpty() || only == entry < 0 || only && allowance[entry] <= 0) {
                        continue;
                    }
                    sender.itemsMatched = true;
                    for (int i = 0; i < takers.size() && !stack.isEmpty(); i++) {
                        int purse = everyone ? i : 0;
                        int amount = Math.min(Math.min(budget[purse], stack.getCount()), only ? allowance[entry] : Integer.MAX_VALUE);
                        if (amount <= 0) {
                            continue;
                        }
                        Member taker = takers.get(i);
                        if (!accepts(taker, stack)) {
                            continue;
                        }
                        sender.itemsWanted = true;
                        int moved = give(level, taker, stack, amount);
                        if (moved < amount) {
                            sender.itemsBlocked = true;
                        }
                        if (moved <= 0) {
                            continue;
                        }
                        stack.shrink(moved);
                        budget[purse] -= moved;
                        if (only) {
                            allowance[entry] -= moved;
                        }
                        sender.itemsOut += moved;
                        taker.itemsIn += moved;
                        taker.settings.itemsInUntil = Math.max(taker.settings.itemsInUntil, now + settings.intervalTicks());
                        changed = true;
                    }
                }
                if (changed) {
                    storage.setChanged();
                    storage.contentsChanged();
                }
            }
            if (sender.itemsOut > 0) {
                settings.lastItems = now;
            }
        }
    }

    /** Whether a receiver takes this kind of item at all: food items by Food: Receive, the rest by Items: Receive. */
    private static boolean accepts(Member taker, ItemStack stack) {
        return ColonyStorage.foodValue(stack) > 0 ? taker.takesFood() : taker.takesItems();
    }

    /** Hands a receiver up to {@code amount} of a stack it accepts: food into its Formicarium's food slot, anything else into its Storage. */
    private static int give(ServerLevel level, Member taker, ItemStack stack, int amount) {
        double food = ColonyStorage.foodValue(stack);
        if (food > 0) {
            return feed(taker, stack, amount, food);
        }
        return amount - ColonyStorage.insertIntoStorage(level, taker.colony, stack.copyWithCount(amount)).getCount();
    }

    /** Puts food items into the receiver's food slot: as many as bring its colony up to its fill level, never into its Storage. */
    private static int feed(Member taker, ItemStack stack, int amount, double foodEach) {
        ItemStack waiting = taker.terrarium.getItem(TerrariumBlockEntity.SLOT_FOOD);
        if (!waiting.isEmpty() && !ItemStack.isSameItemSameComponents(waiting, stack)) {
            return 0;
        }
        int count = Math.min(Math.min(amount, stack.getMaxStackSize() - waiting.getCount()), (int) Math.floor(taker.need() / foodEach + 0.0001));
        if (count <= 0) {
            return 0;
        }
        taker.terrarium.setItem(TerrariumBlockEntity.SLOT_FOOD, stack.copyWithCount(waiting.getCount() + count));
        return count;
    }

    private static boolean filterEmpty(StructureBlockEntity connector) {
        for (int slot = 0; slot < connector.getContainerSize(); slot++) {
            if (!connector.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The filter slot naming this item, or -1. */
    private static int filterSlot(StructureBlockEntity connector, ItemStack stack) {
        for (int slot = 0; slot < connector.getContainerSize(); slot++) {
            if (ItemStack.isSameItemSameComponents(connector.getItem(slot), stack)) {
                return slot;
            }
        }
        return -1;
    }

    /** Per filter slot: how many of its item may leave, which is what the Storage holds over the stock to keep. */
    private static int[] allowance(StructureBlockEntity connector, List<StructureBlockEntity> storages) {
        int[] held = new int[connector.getContainerSize()];
        for (StructureBlockEntity storage : storages) {
            for (int slot = 0; slot < storage.unlockedSlots(); slot++) {
                ItemStack stack = storage.getItem(slot);
                int entry = stack.isEmpty() ? -1 : filterSlot(connector, stack);
                if (entry >= 0) {
                    held[entry] += stack.getCount();
                }
            }
        }
        for (int slot = 0; slot < held.length; slot++) {
            held[slot] = Math.max(0, held[slot] - connector.connector().stock[slot]);
        }
        return held;
    }

    private static ConnectorNote itemNote(ServerLevel level, Member member, @Nullable ConnectorNote lonely, long now) {
        ConnectorSettings settings = member.settings;
        if (settings.itemMode == ConnectorSettings.OFF) {
            return ConnectorNote.OFF;
        }
        if (member.paused) {
            return ConnectorNote.PAUSED;
        }
        if (ColonyStorage.storages(level, member.colony).isEmpty()) {
            return ConnectorNote.NO_STORAGE;
        }
        if (settings.itemMode == ConnectorSettings.SEND) {
            if (settings.filterMode == ConnectorSettings.ONLY && filterEmpty(member.connector)) {
                return ConnectorNote.NO_FILTER;
            }
            if (lonely != null) {
                return lonely;
            }
            List<Member> takers = member.peers.stream().filter(peer -> peer.settings.itemMode == ConnectorSettings.RECEIVE
                    || peer.settings.foodMode == ConnectorSettings.REQUEST).toList();
            if (takers.isEmpty()) {
                return ConnectorNote.NO_RECEIVER;
            }
            if (member.itemsOut > 0 || !settings.itemsDue(now)) {
                return ConnectorNote.SENDING;
            }
            if (takers.stream().allMatch(taker -> taker.paused)) {
                return ConnectorNote.RECEIVERS_PAUSED;
            }
            if (!member.itemsMatched) {
                return ConnectorNote.NOTHING_TO_SEND;
            }
            // Something to send, but nobody here takes that kind of item.
            if (!member.itemsWanted) {
                return ConnectorNote.NO_RECEIVER;
            }
            return member.itemsBlocked ? ConnectorNote.NO_ROOM : ConnectorNote.READY;
        }
        if (lonely != null) {
            return lonely;
        }
        if (member.peers.stream().noneMatch(peer -> peer.settings.itemMode == ConnectorSettings.SEND)) {
            return ConnectorNote.NO_SENDER;
        }
        return member.itemsIn > 0 || now < settings.itemsInUntil ? ConnectorNote.RECEIVING : ConnectorNote.READY;
    }

    // --- What it shows ---

    /** Why a connector has nobody to trade with, or null when it has. */
    private static @Nullable ConnectorNote lonely(Member member, List<Member> members, List<BlockPos> offline) {
        if (!member.peers.isEmpty()) {
            return null;
        }
        if (offline.stream().anyMatch(pos -> inRange(pos, member.pos))) {
            return ConnectorNote.UNLOADED;
        }
        return !offline.isEmpty() || members.stream().anyMatch(other -> other.terrarium != member.terrarium) ? ConnectorNote.FAR : ConnectorNote.ALONE;
    }

    /** The connectors this one sends food or items to. */
    private static List<BlockPos> targets(Member member) {
        List<BlockPos> targets = new ArrayList<>();
        for (Member peer : member.peers) {
            if (member.sendsFood() && peer.takesFood() || member.sendsItems() && (peer.takesItems() || peer.takesFood())) {
                targets.add(peer.pos);
            }
        }
        return targets;
    }

    private static void setTargets(StructureBlockEntity connector, List<BlockPos> targets) {
        ConnectorSettings settings = connector.connector();
        if (!settings.targets.equals(targets)) {
            settings.targets = List.copyOf(targets);
            connector.setChanged();
            connector.contentsChanged();
        }
    }

    /** Each colony on the channel learns about the network (its own entry marked), for its Network page. */
    private static void report(int channel, List<Member> members, List<BlockPos> offline, long now) {
        List<Colony.NetworkLink> links = new ArrayList<>();
        // Where each entry's connector is, so each colony lists only the colonies within reach of its own connector.
        List<BlockPos> linkConnectors = new ArrayList<>();
        for (Member member : members) {
            ConnectorSettings settings = member.settings;
            links.add(new Colony.NetworkLink(member.terrarium.getBlockPos(), member.colony.species(), settings.name, channel,
                    (float) member.colony.food(), (float) member.capacity, settings.foodMode, settings.itemMode, settings.reserve / 100F,
                    settings.target / 100F, new Colony.Flow((float) settings.foodIn, (float) settings.foodOut, (float) settings.itemsIn,
                            (float) settings.itemsOut), true, false, member.paused));
            linkConnectors.add(member.pos);
        }
        for (BlockPos pos : offline) {
            links.add(new Colony.NetworkLink(pos, AntColonies.id("common"), "", channel, 0, 0, 0, 0, 0, 0, Colony.Flow.NONE, false, false, false));
            linkConnectors.add(pos);
        }
        Map<TerrariumBlockEntity, List<Colony.NetworkLink>> perColony = new HashMap<>();
        for (Member member : members) {
            List<Colony.NetworkLink> view = perColony.computeIfAbsent(member.terrarium, t -> new ArrayList<>());
            for (int l = 0; l < links.size(); l++) {
                Colony.NetworkLink link = links.get(l);
                boolean self = link.online() && link.terrarium().equals(member.terrarium.getBlockPos());
                if (!self && !inRange(linkConnectors.get(l), member.pos)) {
                    continue; // out of this connector's reach
                }
                if (self && view.stream().anyMatch(e -> e.self() && e.channel() == channel)) {
                    continue; // one entry for itself per channel, even with two connectors on it
                }
                view.add(self ? link.asSelf() : link);
            }
        }
        for (Map.Entry<TerrariumBlockEntity, List<Colony.NetworkLink>> entry : perColony.entrySet()) {
            Colony colony = entry.getKey().colony();
            List<Colony.NetworkLink> merged = new ArrayList<>();
            // Keep what the colony knows of its other channels (they pass on their own).
            Map<Integer, Long> seen = NETWORK_SEEN.computeIfAbsent(entry.getKey(), t -> new HashMap<>());
            colony.network().stream().filter(link -> link.channel() != channel
                            && seen.getOrDefault(link.channel(), Long.MIN_VALUE / 2) >= now - quietTicks())
                    .forEach(merged::add);
            merged.addAll(entry.getValue());
            ColonyAccess.setNetwork(colony, merged);
            seen.put(channel, now);
        }
    }

    /** Whether two connectors are close enough to link, on x and on z (height doesn't count). */
    public static boolean inRange(BlockPos a, BlockPos b) {
        int range = AntConfig.get(AntConfig.CONNECTOR_RANGE);
        return Math.abs(a.getX() - b.getX()) <= range && Math.abs(a.getZ() - b.getZ()) <= range;
    }

    /** When each colony last heard from each channel (a channel's entries expire when it stops hearing from it). */
    private static final WeakHashMap<TerrariumBlockEntity, Map<Integer, Long>> NETWORK_SEEN = new WeakHashMap<>();

    /** How long a colony keeps a channel's entries on its Network page after it last heard from it. */
    private static long quietTicks() {
        return Math.max(200, 2L * AntConfig.get(AntConfig.CONNECTOR_INTERVAL));
    }

    /** Drops the channels a colony no longer hears from; else one whose last connector is gone would show its old network for ever. */
    private static void forgetQuietChannels(ServerLevel level) {
        long now = level.getGameTime();
        var it = NETWORK_SEEN.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            TerrariumBlockEntity terrarium = entry.getKey();
            if (terrarium.getLevel() != level) {
                continue;
            }
            Map<Integer, Long> seen = entry.getValue();
            if (!seen.values().removeIf(time -> time < now - quietTicks())) {
                continue;
            }
            Colony colony = terrarium.colony();
            ColonyAccess.setNetwork(colony, colony.network().stream().filter(link -> seen.containsKey(link.channel())).toList());
            if (seen.isEmpty()) {
                it.remove();
            }
        }
    }

    public static boolean isConnector(StructureBlockEntity be) {
        return be.kind() == StructureKind.COLONY_CONNECTOR;
    }
}
