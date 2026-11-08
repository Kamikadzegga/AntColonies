package com.antcolonies.compat.jade;

import java.util.Locale;

import com.antcolonies.AntColonies;
import com.antcolonies.content.blockentity.GeneratorBlockEntity;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.ColonyStatus;
import com.antcolonies.colony.genetics.Trait;
import com.antcolonies.content.item.Tooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.entity.WildAnt;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** The Jade providers: for each block a server half (the numbers) and a client half (the lines), sharing one id. */
final class ColonyProviders {
    /** At most this many status lines, so a struggling colony doesn't fill the screen. */
    private static final int MAX_STATUS_LINES = 3;

    static final Server TERRARIUM_DATA = new Server("terrarium") {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof TerrariumBlockEntity terrarium)) {
                return;
            }
            Colony colony = terrarium.colony();
            data.putBoolean("queen", colony.hasQueen());
            data.putString("species", colony.species().toString());
            data.putInt("stage", colony.stage());
            data.putLong("adults", colony.adults());
            data.putLong("housing", colony.stats().adultCapacity());
            double capacity = colony.stats().foodCapacity();
            data.putInt("food", capacity <= 0 ? 0 : (int) Math.round(Math.min(1, colony.food() / capacity) * 100));
            data.putInt("status", colony.status());
        }

    };

    static final Client TERRARIUM = new Client("terrarium") {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("stage")) {
                return;
            }
            if (!data.getBooleanOr("queen", false)) {
                tooltip.add(Component.translatable("status.antcolonies.no_queen").withStyle(ChatFormatting.RED));
                return;
            }
            Identifier species = Identifier.tryParse(data.getStringOr("species", ""));
            if (species != null) {
                tooltip.add(Tooltips.species(species).append(" · ")
                        .append(Component.translatable("gui.antcolonies.stage", data.getIntOr("stage", 1))));
            }
            tooltip.add(Component.translatable("gui.antcolonies.housing", number(data.getLongOr("adults", 0)), number(data.getLongOr("housing", 0)))
                    .withStyle(ChatFormatting.GRAY));
            int food = data.getIntOr("food", 0);
            tooltip.add(Component.translatable("jade.antcolonies.food", food)
                    .withStyle(food < 10 ? ChatFormatting.RED : food < 30 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            statuses(tooltip, data.getIntOr("status", 0), true);
        }
    };

    static final Server STRUCTURE_DATA = new Server("structure") {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof StructureBlockEntity structure)) {
                return;
            }
            data.putBoolean("linked", structure.isLinked());
            data.putBoolean("job", structure.kind().job().any());
            data.putInt("assigned", structure.assigned());
            data.putInt("soldiers", structure.soldiers());
            data.putBoolean("forage", structure.kind().job().soldiers());
            data.putBoolean("contested", structure.isContested());
            data.putInt("capacity", structure.capacity());
            data.putDouble("rate", structure.rate());
            data.putInt("status", structure.siteStatus() | structure.rearingStatus());
        }

    };

    static final Client STRUCTURE = new Client("structure") {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("linked")) {
                return;
            }
            if (!data.getBooleanOr("linked", false)) {
                tooltip.add(Component.translatable(data.getBooleanOr("contested", false) ? "status.antcolonies.two_colonies"
                        : "status.antcolonies.missing_connection").withStyle(ChatFormatting.RED));
                return;
            }
            if (data.getBooleanOr("job", false)) {
                tooltip.add((data.getBooleanOr("forage", false)
                        ? Component.translatable("gui.antcolonies.job_foraging", data.getIntOr("assigned", 0), data.getIntOr("soldiers", 0), data.getIntOr("capacity", 0))
                        : Component.translatable("gui.antcolonies.job_workers", data.getIntOr("assigned", 0), data.getIntOr("capacity", 0)))
                        .withStyle(ChatFormatting.GRAY));
                double rate = data.getDoubleOr("rate", 0);
                if (rate > 0) {
                    tooltip.add(Component.translatable("gui.antcolonies.site.rate", rate(rate)).withStyle(ChatFormatting.GRAY));
                }
            }
            statuses(tooltip, data.getIntOr("status", 0), false);
        }
    };

    static final Server MACHINE_DATA = new Server("machine") {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof GeneratorBlockEntity generator) {
                data.putInt("energy", generator.energy());
                data.putInt("capacity", GeneratorBlockEntity.CAPACITY);
                data.putInt("output", generator.data().get(GeneratorBlockEntity.DATA_OUTPUT));
            } else if (accessor.getBlockEntity() instanceof GeneticLabBlockEntity lab) {
                var labData = lab.data();
                data.putInt("energy", labData.get(GeneticLabBlockEntity.DATA_ENERGY));
                data.putInt("capacity", GeneticLabBlockEntity.ENERGY_CAPACITY);
                GeneticLabBlockEntity.Operation operation = GeneticLabBlockEntity.Operation.byId(labData.get(GeneticLabBlockEntity.DATA_OPERATION));
                if (operation != GeneticLabBlockEntity.Operation.NONE) {
                    data.putString("operation", operation.name().toLowerCase(Locale.ROOT));
                    data.putInt("trait", labData.get(GeneticLabBlockEntity.DATA_TRAIT));
                    int time = Math.max(1, labData.get(GeneticLabBlockEntity.DATA_TIME));
                    data.putInt("progress", (int) Math.round(Math.clamp(labData.get(GeneticLabBlockEntity.DATA_PROGRESS) / (double) time, 0, 1) * 100));
                }
            }
        }

    };

    static final Client MACHINE = new Client("machine") {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("energy")) {
                return;
            }
            // Jade draws the energy bar itself (from the energy capability).
            int output = data.getIntOr("output", 0);
            if (output > 0) {
                tooltip.add(Component.translatable("gui.antcolonies.generator.making", output).withStyle(ChatFormatting.GREEN));
            }
            String operation = data.getStringOr("operation", "");
            if (!operation.isEmpty()) {
                Trait[] traits = Trait.values();
                int trait = Math.clamp(data.getIntOr("trait", 0), 0, traits.length - 1);
                tooltip.add(Component.translatable("gui.antcolonies.lab.working." + operation, Component.translatable(traits[trait].translationKey()),
                        data.getIntOr("progress", 0)).withStyle(ChatFormatting.AQUA));
            }
        }
    };

    /** A wild ant: its species, caste and what the species is like (all known on the client: no server half). */
    static final IEntityComponentProvider WILD_ANT = new IEntityComponentProvider() {
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof WildAnt ant)) {
                return;
            }
            tooltip.add(Tooltips.species(ant.species()).append(" · ")
                    .append(Component.translatable("caste.antcolonies." + ant.caste().getSerializedName())));
            Tooltips.speciesFacts(accessor.getLevel().registryAccess(), ant.species(), tooltip::add, false);
            tooltip.add(Component.translatable("jade.antcolonies.catch").withStyle(ChatFormatting.DARK_GRAY));
        }

        @Override
        public Identifier getUid() {
            return AntColonies.id("wild_ant");
        }
    };

    /** A wild nest: whose it is and how to take ants from it. */
    static final IBlockComponentProvider WILD_NEST = new IBlockComponentProvider() {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlock() instanceof WildNestBlock nest)) {
                return;
            }
            tooltip.add(Tooltips.species(nest.species()));
            Tooltips.speciesFacts(accessor.getLevel().registryAccess(), nest.species(), tooltip::add, false);
            tooltip.add(Component.translatable("jade.antcolonies.nest").withStyle(ChatFormatting.DARK_GRAY));
        }

        @Override
        public Identifier getUid() {
            return AntColonies.id("wild_nest");
        }
    };

    private ColonyProviders() {}

    /** The server half: writes the numbers Jade sends to the looking player. */
    abstract static class Server implements IServerDataProvider<BlockAccessor> {
        private final Identifier uid;

        Server(String name) {
            this.uid = AntColonies.id(name);
        }

        @Override
        public Identifier getUid() {
            return uid;
        }
    }

    /** The client half, under the same id: turns those numbers into tooltip lines. */
    abstract static class Client implements IBlockComponentProvider {
        private final Identifier uid;

        Client(String name) {
            this.uid = AntColonies.id(name);
        }

        @Override
        public Identifier getUid() {
            return uid;
        }
    }

    /** Problems in red, notes in gold, "Working" in green (not shown for a Terrarium: its sites say it). */
    private static void statuses(ITooltip tooltip, int flags, boolean hideWorking) {
        int lines = 0;
        for (ColonyStatus status : ColonyStatus.of(flags)) {
            if (lines >= MAX_STATUS_LINES) {
                break;
            }
            if (hideWorking && status == ColonyStatus.WORKING) {
                continue;
            }
            MutableComponent line = Component.translatable(status.translationKey()).withStyle(status.color());
            tooltip.add(line);
            lines++;
        }
    }

    private static String number(long value) {
        if (value >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        }
        return String.valueOf(value);
    }

    private static String rate(double perMinute) {
        return perMinute >= 100 ? String.format(Locale.ROOT, "%.0f", perMinute)
                : String.format(Locale.ROOT, perMinute >= 10 ? "%.1f" : perMinute >= 0.1 ? "%.2f" : "%.3f", perMinute);
    }
}
