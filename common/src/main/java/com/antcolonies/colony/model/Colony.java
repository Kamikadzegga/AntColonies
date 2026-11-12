package com.antcolonies.colony.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.core.registry.ModRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One colony: its Queen, its real population, its food, and what its last simulation step worked out. The numbers are
 * doubles so small steps add up exactly; GUIs show them rounded down. This is the only source of a colony's population:
 * nothing is counted from entities or from the ants drawn in the Terrarium.
 *
 * <p>Saved with its Terrarium. The client copy gets the same data plus the last step's results (rates, status,
 * assignments), so the GUI and the Terrarium's animation show exactly what the server works with.
 */
public final class Colony {
    /** One working chamber's ants, as the GUI lists them. */
    public record Assignment(BlockPos pos, StructureKind kind, int assigned, int requested, int capacity, int status, double rate, int mode,
                             int priority, boolean enabled) {
        public static final Codec<Assignment> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Assignment::pos),
                StructureKind.CODEC.fieldOf("kind").forGetter(Assignment::kind),
                Codec.INT.fieldOf("assigned").forGetter(Assignment::assigned),
                Codec.INT.fieldOf("requested").forGetter(Assignment::requested),
                Codec.INT.fieldOf("capacity").forGetter(Assignment::capacity),
                Codec.INT.fieldOf("status").forGetter(Assignment::status),
                Codec.DOUBLE.fieldOf("rate").forGetter(Assignment::rate),
                Codec.INT.optionalFieldOf("mode", 0).forGetter(Assignment::mode),
                Codec.INT.optionalFieldOf("priority", 1).forGetter(Assignment::priority),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Assignment::enabled)
        ).apply(i, Assignment::new));
    }

    /** What the colony's ants were doing in the last step, as shares of its adults: drives the Terrarium's animation. */
    public record Activity(float scavenging, float fighting, float farming, float processing, float maintenance, float idle, boolean feeding,
                           boolean starving, boolean laying) {
        public static final Activity NONE = new Activity(0, 0, 0, 0, 0, 1, false, false, false);
        public static final Codec<Activity> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("scavenging").forGetter(Activity::scavenging),
                Codec.FLOAT.fieldOf("fighting").forGetter(Activity::fighting),
                Codec.FLOAT.fieldOf("farming").forGetter(Activity::farming),
                Codec.FLOAT.fieldOf("processing").forGetter(Activity::processing),
                Codec.FLOAT.fieldOf("maintenance").forGetter(Activity::maintenance),
                Codec.FLOAT.fieldOf("idle").forGetter(Activity::idle),
                Codec.BOOL.fieldOf("feeding").forGetter(Activity::feeding),
                Codec.BOOL.fieldOf("starving").forGetter(Activity::starving),
                Codec.BOOL.fieldOf("laying").forGetter(Activity::laying)
        ).apply(i, Activity::new));
    }

    /**
     * One colony on a Colony Connector channel, as this colony's Network page shows it: where its Terrarium is, its
     * species and food, the connector's settings and what moved over the last minute. {@code self} marks this colony's
     * own connector; {@code online} is false while the other colony's chunk is unloaded (it then takes no part).
     */
    public record NetworkLink(BlockPos terrarium, Identifier species, int channel, float food, float capacity, int mode, int itemMode,
                              float reserve, float target, float inPerMinute, float outPerMinute, boolean online, boolean self, boolean paused) {
        public static final Codec<NetworkLink> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(NetworkLink::terrarium),
                Identifier.CODEC.fieldOf("species").forGetter(NetworkLink::species),
                Codec.INT.fieldOf("channel").forGetter(NetworkLink::channel),
                Codec.FLOAT.fieldOf("food").forGetter(NetworkLink::food),
                Codec.FLOAT.fieldOf("capacity").forGetter(NetworkLink::capacity),
                Codec.INT.fieldOf("mode").forGetter(NetworkLink::mode),
                Codec.INT.optionalFieldOf("item_mode", 0).forGetter(NetworkLink::itemMode),
                Codec.FLOAT.fieldOf("reserve").forGetter(NetworkLink::reserve),
                Codec.FLOAT.fieldOf("target").forGetter(NetworkLink::target),
                Codec.FLOAT.fieldOf("in").forGetter(NetworkLink::inPerMinute),
                Codec.FLOAT.fieldOf("out").forGetter(NetworkLink::outPerMinute),
                Codec.BOOL.fieldOf("online").forGetter(NetworkLink::online),
                Codec.BOOL.fieldOf("self").forGetter(NetworkLink::self),
                Codec.BOOL.optionalFieldOf("paused", false).forGetter(NetworkLink::paused)
        ).apply(i, NetworkLink::new));
    }

    /** One kind of item in the colony's Storage, for the Production page: the item and how many there are in all. */
    public record StoredItem(ItemStack item, long count) {
        public static final Codec<StoredItem> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("item").forGetter(StoredItem::item),
                Codec.LONG.fieldOf("count").forGetter(StoredItem::count)
        ).apply(i, StoredItem::new));
    }

    /** The last step's results, for GUIs (per minute). */
    public record Stats(double eggsLaid, double adultsRaised, double foodIn, double foodOut, double finds, double hunts, double deaths,
                        long adultCapacity, long larvaeCapacity, double foodCapacity, int structures, double foodFromStorage) {
        public static final Stats NONE = new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        public static final Codec<Stats> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("eggs").forGetter(Stats::eggsLaid),
                Codec.DOUBLE.fieldOf("raised").forGetter(Stats::adultsRaised),
                Codec.DOUBLE.fieldOf("food_in").forGetter(Stats::foodIn),
                Codec.DOUBLE.fieldOf("food_out").forGetter(Stats::foodOut),
                Codec.DOUBLE.fieldOf("finds").forGetter(Stats::finds),
                Codec.DOUBLE.fieldOf("hunts").forGetter(Stats::hunts),
                Codec.DOUBLE.fieldOf("deaths").forGetter(Stats::deaths),
                Codec.LONG.fieldOf("adult_capacity").forGetter(Stats::adultCapacity),
                Codec.LONG.fieldOf("larvae_capacity").forGetter(Stats::larvaeCapacity),
                Codec.DOUBLE.fieldOf("food_capacity").forGetter(Stats::foodCapacity),
                Codec.INT.fieldOf("structures").forGetter(Stats::structures),
                // Food per minute the colony ate from its own stores (part of food_in).
                Codec.DOUBLE.optionalFieldOf("food_from_storage", 0.0).forGetter(Stats::foodFromStorage)
        ).apply(i, Stats::new));
    }

    // --- Saved ---
    UUID id = UUID.randomUUID();
    @Nullable QueenData queen;
    Identifier species = ModRegistries.COMMON;
    /** The species the colony was before its Queen last evolved, while some of its adults are still of it. */
    @Nullable Identifier formerSpecies;
    /** Share of the adults already of the current species (the rest are still of the former one). */
    double conversion = 1;
    double eggs;
    double larvae;
    double workers;
    double food;
    double specialFood;
    double health = 1;
    double starvedMinutes;
    int evolutionAttempts;
    int catalystCharges;
    long lastSimTime = Long.MIN_VALUE;
    long totalFound;
    long totalLaid;
    /** Queen-only founding (see {@link Founding}); NONE for every colony started or moved with ants. */
    Founding founding = Founding.NONE;
    /** Adults raised since the founding Queen began her first brood. */
    double foundingRaised;
    /** Whether this colony grew from a single Queen through founding (for the advancement). */
    boolean queenFounded;
    final ColonyHistory history = new ColonyHistory();

    // --- Results of the last step (synced, not saved) ---
    int stage = 1;
    int status;
    Stats stats = Stats.NONE;
    Activity activity = Activity.NONE;
    List<Assignment> assignments = List.of();
    int maintenanceWorkers;
    /** How the colony's food holds up its work, egg laying and growth (1 = fully; less when food runs low or out). */
    double foodFactor = 1;
    /** Colony Connector channels this colony is on, and the colonies on them (sent with the screen open). */
    List<NetworkLink> network = List.of();
    /** What its Storage holds, most first (sent with the screen open), and how many of its Storage slots are taken. */
    List<StoredItem> stored = List.of();
    int storageUsed;
    int storageSlots;

    // --- Server only ---
    ColonyLayout layout = ColonyLayout.EMPTY;

    public UUID id() {
        return id;
    }

    public @Nullable QueenData queen() {
        return queen;
    }

    public Identifier species() {
        return species;
    }

    public @Nullable Identifier formerSpecies() {
        return formerSpecies;
    }

    public double conversion() {
        return conversion;
    }

    public long eggs() {
        return (long) eggs;
    }

    public long larvae() {
        return (long) larvae;
    }

    public long workers() {
        return (long) workers;
    }

    public long adults() {
        return (long) workers;
    }

    public double exactAdults() {
        return workers;
    }

    public double food() {
        return food;
    }

    public double specialFood() {
        return specialFood;
    }

    public double health() {
        return health;
    }

    public int stage() {
        return stage;
    }

    public int status() {
        return status;
    }

    public Stats stats() {
        return stats;
    }

    public Activity activity() {
        return activity;
    }

    public List<Assignment> assignments() {
        return assignments;
    }

    public int maintenanceWorkers() {
        return maintenanceWorkers;
    }

    public int evolutionAttempts() {
        return evolutionAttempts;
    }

    public int catalystCharges() {
        return catalystCharges;
    }

    public long totalLaid() {
        return totalLaid;
    }

    public long totalFound() {
        return totalFound;
    }

    public ColonyLayout layout() {
        return layout;
    }

    public Founding founding() {
        return founding;
    }

    public double foundingRaised() {
        return foundingRaised;
    }

    public boolean queenFounded() {
        return queenFounded;
    }

    public ColonyHistory history() {
        return history;
    }

    /** How well its food holds up its work, egg laying and growth this step: 1 when fed, less when food runs low. */
    public double foodFactor() {
        return foodFactor;
    }

    public List<NetworkLink> network() {
        return network;
    }

    public List<StoredItem> stored() {
        return stored;
    }

    public int storageUsed() {
        return storageUsed;
    }

    public int storageSlots() {
        return storageSlots;
    }

    /** Whether it has no ants at all: no adults, eggs or larvae (a Queen alone counts as empty). */
    public boolean isEmptyOfAnts() {
        return workers < 1 && eggs < 1 && larvae < 1;
    }

    public boolean hasQueen() {
        return queen != null;
    }

    // --- Changes from outside the simulation (items, GUI buttons, the lab) ---

    /** A Queen moves in; the colony becomes her species (an evolved line keeps its former adults until they die out). */
    public void setQueen(@Nullable QueenData queen) {
        if (queen == null) {
            endFounding();
        }
        this.queen = queen;
        if (queen != null && !queen.species().equals(species)) {
            boolean empty = exactAdults() < 1;
            formerSpecies = empty ? null : species;
            conversion = empty ? 1 : 0;
            species = queen.species();
            evolutionAttempts = 0;
        }
    }

    /** The Queen evolved: her new eggs and larvae are the new species at once, adults change as they are replaced. */
    public void evolve(QueenData evolved) {
        Identifier old = species;
        queen = evolved;
        species = evolved.species();
        if (exactAdults() >= 1) {
            formerSpecies = old;
            conversion = 0;
        }
        evolutionAttempts = 0;
    }

    /** An empty colony without a Queen takes on a species (the first ants put into a new Terrarium). */
    public void adoptSpecies(Identifier species) {
        if (queen == null && exactAdults() < 1 && !species.equals(this.species)) {
            this.species = species;
            formerSpecies = null;
            conversion = 1;
            eggs = 0;
            larvae = 0;
        }
    }

    /**
     * A Queen alone was put into an empty Terrarium: Queen-only founding starts (she waits for her food, then lays her first
     * brood). Only the Terrarium's Queen slot calls this, and only for a colony with no ants at all.
     */
    public void startFounding() {
        if (queen != null && isEmptyOfAnts()) {
            founding = Founding.FEEDING;
            foundingRaised = 0;
        }
    }

    /** Founding is over (or never began): the colony runs like any other. */
    public void endFounding() {
        founding = Founding.NONE;
        foundingRaised = 0;
    }

    public void addWorkers(double count) {
        if (count > 0) {
            // Ants put in from outside: the colony is no longer one Queen alone.
            endFounding();
        }
        workers += count;
        if (formerSpecies != null) {
            // New arrivals are the current species.
            double adults = exactAdults();
            conversion = adults <= 0 ? 1 : Math.min(1, (conversion * (adults - count) + count) / adults);
        }
    }

    public void addEggs(double count) {
        if (count > 0) {
            endFounding();
        }
        eggs = Math.max(0, eggs + count);
    }

    public void addLarvae(double count) {
        if (count > 0) {
            endFounding();
        }
        larvae = Math.max(0, larvae + count);
    }

    public void addFood(double amount, double special) {
        food += amount;
        specialFood += special;
    }

    public void countEvolutionAttempt() {
        evolutionAttempts++;
    }

    public void addCatalyst(int charges) {
        catalystCharges += charges;
    }

    public boolean useCatalyst() {
        if (catalystCharges > 0) {
            catalystCharges--;
            return true;
        }
        return false;
    }

    /** For tests and commands: sets the population outright. */
    /** Takes workers away (a Guard Ant leaving); false, and nothing taken, when the colony has fewer. */
    public boolean takeWorkers(double count) {
        if (workers < count) {
            return false;
        }
        workers -= count;
        return true;
    }

    public void setPopulation(double eggs, double larvae, double workers) {
        this.eggs = eggs;
        this.larvae = larvae;
        this.workers = workers;
    }

    public void setFood(double food) {
        this.food = food;
    }

    public void setLayout(ColonyLayout layout) {
        this.layout = layout;
    }

    // --- Saving ---

    public void save(ValueOutput output, boolean sync) {
        output.store("id", UUIDUtil.CODEC, id);
        output.storeNullable("queen", QueenData.CODEC, queen);
        output.store("species", Identifier.CODEC, species);
        output.storeNullable("former_species", Identifier.CODEC, formerSpecies);
        output.putDouble("conversion", conversion);
        output.putDouble("eggs", eggs);
        output.putDouble("larvae", larvae);
        output.putDouble("workers", workers);
        output.putDouble("food", food);
        output.putDouble("special_food", specialFood);
        output.putDouble("health", health);
        output.putDouble("starved", starvedMinutes);
        output.putInt("evolution_attempts", evolutionAttempts);
        output.putInt("catalyst", catalystCharges);
        output.putLong("last_sim", lastSimTime);
        output.putLong("total_found", totalFound);
        output.putLong("total_laid", totalLaid);
        if (founding.active()) {
            output.putString("founding", founding.getSerializedName());
            output.putDouble("founding_raised", foundingRaised);
        }
        if (queenFounded) {
            output.putBoolean("queen_founded", true);
        }
        if (!sync) {
            history.save(output);
        }
        if (sync) {
            output.putDouble("food_factor", foodFactor);
            output.putInt("stage", stage);
            output.putInt("status", status);
            output.store("stats", Stats.CODEC, stats);
            output.store("activity", Activity.CODEC, activity);
            output.putInt("maintenance", maintenanceWorkers);
            ValueOutput.TypedOutputList<Assignment> list = output.list("assignments", Assignment.CODEC);
            // The GUI lists at most 64 sites; the rest still work, they just aren't listed.
            assignments.stream().limit(64).forEach(list::add);
        }
    }

    /** What only a player with the Terrarium's screen open needs: the history, the Storage's contents and the network. */
    public void saveDetails(ValueOutput output) {
        history.save(output);
        ValueOutput.TypedOutputList<NetworkLink> links = output.list("network", NetworkLink.CODEC);
        network.stream().limit(48).forEach(links::add);
        ValueOutput.TypedOutputList<StoredItem> items = output.list("stored", StoredItem.CODEC);
        stored.stream().limit(54).forEach(items::add);
        output.putInt("storage_used", storageUsed);
        output.putInt("storage_slots", storageSlots);
        output.putBoolean("details", true);
    }

    public void load(ValueInput input) {
        id = input.read("id", UUIDUtil.CODEC).orElse(id);
        queen = input.read("queen", QueenData.CODEC).orElse(null);
        species = input.read("species", Identifier.CODEC).orElse(ModRegistries.COMMON);
        formerSpecies = input.read("former_species", Identifier.CODEC).orElse(null);
        conversion = input.getDoubleOr("conversion", 1);
        eggs = Math.max(0, input.getDoubleOr("eggs", 0));
        larvae = Math.max(0, input.getDoubleOr("larvae", 0));
        workers = Math.max(0, input.getDoubleOr("workers", 0));
        // Colonies had soldiers once: they stay on as workers.
        workers += Math.max(0, input.getDoubleOr("soldiers", 0));
        food = Math.max(0, input.getDoubleOr("food", 0));
        specialFood = Math.max(0, input.getDoubleOr("special_food", 0));
        health = Math.clamp(input.getDoubleOr("health", 1), 0, 1);
        starvedMinutes = input.getDoubleOr("starved", 0);
        evolutionAttempts = input.getIntOr("evolution_attempts", 0);
        catalystCharges = input.getIntOr("catalyst", 0);
        lastSimTime = input.getLongOr("last_sim", Long.MIN_VALUE);
        totalFound = input.getLongOr("total_found", 0);
        totalLaid = input.getLongOr("total_laid", 0);
        // Colonies saved before founding existed, and every colony started with ants, load as NONE.
        founding = Founding.byName(input.getStringOr("founding", "none"));
        foundingRaised = founding.active() ? input.getDoubleOr("founding_raised", 0) : 0;
        queenFounded = input.getBooleanOr("queen_founded", false);
        // Saved, or synced with the screen open; a sync without it keeps the client's copy.
        history.load(input);
        foodFactor = input.getDoubleOr("food_factor", foodFactor);
        if (input.getBooleanOr("details", false)) {
            List<NetworkLink> links = new ArrayList<>();
            input.listOrEmpty("network", NetworkLink.CODEC).forEach(links::add);
            network = List.copyOf(links);
            List<StoredItem> items = new ArrayList<>();
            input.listOrEmpty("stored", StoredItem.CODEC).forEach(items::add);
            stored = List.copyOf(items);
            storageUsed = input.getIntOr("storage_used", 0);
            storageSlots = input.getIntOr("storage_slots", 0);
        }
        stage = input.getIntOr("stage", stage);
        status = input.getIntOr("status", status);
        stats = input.read("stats", Stats.CODEC).orElse(stats);
        activity = input.read("activity", Activity.CODEC).orElse(activity);
        maintenanceWorkers = input.getIntOr("maintenance", maintenanceWorkers);
        List<Assignment> list = new ArrayList<>();
        input.listOrEmpty("assignments", Assignment.CODEC).forEach(list::add);
        if (!list.isEmpty() || input.getInt("stage").isPresent()) {
            assignments = List.copyOf(list);
        }
    }
}
