package com.antcolonies.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.antcolonies.AntColonies;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.colony.species.Caste;
import com.antcolonies.content.block.ColonyEntityBlock;
import com.antcolonies.content.blockentity.ConnectorSettings;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.content.worldgen.AnthillLayout;
import com.antcolonies.content.worldgen.AnthillPiece;
import com.antcolonies.content.worldgen.AnthillTheme;
import com.antcolonies.core.registry.ModBlocks;
import com.antcolonies.core.registry.ModEntities;
import com.antcolonies.core.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The stress test's world, shared by the singleplayer stress test ({@code IngameShowcase}, marker {@code stress-test})
 * and the multiplayer one ({@code MultiplayerStressServer}, marker {@code mp-stress}), all relative to an origin on a
 * flat world:
 *
 * <ul>
 *   <li>100 big colonies in a 10 x 10 grid (2000 workers each, a full ring of eight chambers: Foraging or Harvesting Sites,
 *   Storage, Nest and Queen Chambers and a Colony Connector, 4 channels, every one in range of many others);</li>
 *   <li>100 wild ants among them;</li>
 *   <li>four large grown anthills (savanna, taiga, jungle, snowy), each on a platform of its own ground, with its great
 *   Queen, royal guard, soldiers and workers (some 50 ants each).</li>
 * </ul>
 */
final class StressWorld {
    static final AnthillTheme[] THEMES = {AnthillTheme.SAVANNA, AnthillTheme.TAIGA, AnthillTheme.JUNGLE, AnthillTheme.SNOWY};
    /** Where the anthills stand, round the grid (which spans 6..51 on x and z), their ground 42 blocks up. */
    private static final int[][] ANTHILLS = {{-45, 28}, {100, 28}, {28, -45}, {28, 100}};
    private static final int ANTHILL_HEIGHT = 42;

    private StressWorld() {}

    static BlockPos colonyPos(BlockPos origin, int gx, int gz) {
        return origin.offset(6 + gx * 5, 0, 6 + gz * 5);
    }

    static BlockPos anthillCenter(BlockPos origin, int index) {
        return origin.offset(ANTHILLS[index][0], ANTHILL_HEIGHT, ANTHILLS[index][1]);
    }

    /** The 100 colonies and 100 wild ants. */
    static void buildColonies(ServerLevel level, BlockPos origin) {
        StructureKind[] mine = {StructureKind.FORAGING_SITE, StructureKind.FORAGING_SITE, StructureKind.STORAGE, StructureKind.NEST_CHAMBER,
                StructureKind.FORAGING_SITE, StructureKind.NEST_CHAMBER, StructureKind.QUEEN_CHAMBER};
        StructureKind[] farm = {StructureKind.HARVESTING_SITE, StructureKind.FORAGING_SITE, StructureKind.STORAGE, StructureKind.NEST_CHAMBER,
                StructureKind.HARVESTING_SITE, StructureKind.NEST_CHAMBER, StructureKind.QUEEN_CHAMBER};
        int i = 0;
        for (int gx = 0; gx < 10; gx++) {
            for (int gz = 0; gz < 10; gz++) {
                BlockPos pos = colonyPos(origin, gx, gz);
                boolean farmer = i % 2 == 0;
                colony(level, pos, farmer ? "farmer" : "stone", 2000, 20000);
                List<BlockPos> places = places(pos);
                StructureKind[] kinds = farmer ? farm : mine;
                for (int p = 0; p < kinds.length; p++) {
                    structure(level, places.get(p), kinds[p]);
                }
                connector(level, places.get(7), i % 4, farmer ? ConnectorSettings.SUPPLY : ConnectorSettings.REQUEST, 40, 75);
                i++;
            }
        }
        RandomSource random = RandomSource.create(99);
        for (int n = 0; n < 100; n++) {
            var ant = ModEntities.WILD_ANT.get().create(level, EntitySpawnReason.COMMAND);
            BlockPos at = origin.offset(4 + random.nextInt(50), 0, 4 + random.nextInt(50));
            ant.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
            level.addFreshEntity(ant);
        }
    }

    /** A large grown layout reaching at most 30 blocks out and 40 down (it fits its platform). */
    private static long largeSeed(int index) {
        for (long seed = 1 + index * 15485863L; ; seed++) {
            AnthillLayout layout = AnthillLayout.create(AnthillLayout.Style.GROWN, seed);
            if (layout.size() == AnthillLayout.Size.LARGE && layout.minX() >= -30 && layout.maxX() <= 30 && layout.minZ() >= -30
                    && layout.maxZ() <= 30 && layout.minY() >= -40) {
                return seed;
            }
        }
    }

    /** One of the four anthills, on its platform; returns a line saying what it holds. */
    static String buildAnthill(ServerLevel level, BlockPos origin, int index) {
        AnthillTheme theme = THEMES[index];
        BlockPos center = anthillCenter(origin, index);
        AnthillPiece piece = new AnthillPiece(center, AnthillPiece.Kind.OVERWORLD, AnthillLayout.Style.GROWN, largeSeed(index), theme);
        var box = piece.getBoundingBox();
        Block ground = theme == AnthillTheme.SNOWY ? Blocks.SNOW_BLOCK : Blocks.GRASS_BLOCK;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), origin.getY(), box.minZ(), box.maxX(), center.getY(), box.maxZ())) {
            int ry = pos.getY() - center.getY();
            level.setBlock(pos, (ry == 0 ? ground : ry > -4 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), box, ChunkPos.containing(center), center);
        int ants = level.getEntitiesOfClass(WildAnt.class, AABB.of(box).inflate(2)).size();
        return String.format(Locale.ROOT, "anthill %d (%s): %s, %d rooms, %d ants", index, theme.getSerializedName(), piece.layout().size(),
                piece.layout().rooms().size(), ants);
    }

    /** The great Queen of anthill {@code index}, or null. */
    static WildAnt queen(ServerLevel level, BlockPos origin, int index) {
        return level.getEntitiesOfClass(WildAnt.class, new AABB(anthillCenter(origin, index)).inflate(40, 50, 40),
                ant -> ant.rank() == WildAnt.Rank.GREAT_QUEEN).stream().findFirst().orElse(null);
    }

    /**
     * The worst case: a player in a full Chitin set, made unkillable, steps into anthill {@code index}'s Queen's chamber
     * (difficulty Normal), so its alarm goes up and every one of its ants comes after them.
     */
    static String raiseAlarm(ServerLevel level, BlockPos origin, ServerPlayer player, int index) {
        WildAnt queen = queen(level, origin, index);
        if (queen == null) {
            return "alarm: no Queen in anthill " + index;
        }
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.CHITIN_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.CHITIN_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.CHITIN_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.CHITIN_BOOTS.get()));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 600, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 600, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 600, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 600, 0, false, false));
        player.teleportTo(level, queen.getX() - 2.5, queen.getY(), queen.getZ() - 2.5, java.util.Set.of(), -45, 20, true);
        return "alarm: " + player.getGameProfile().name() + " stepped into anthill " + index + "'s Queen's chamber";
    }

    /** Colonies, workers and finds, wild ants (and how many of them are anthill ants, and alarmed or fighting). */
    static String count(ServerLevel level, BlockPos origin) {
        int colonies = 0;
        double workers = 0;
        long found = 0;
        for (int gx = 0; gx < 10; gx++) {
            for (int gz = 0; gz < 10; gz++) {
                if (level.getBlockEntity(colonyPos(origin, gx, gz)) instanceof TerrariumBlockEntity t) {
                    colonies++;
                    workers += t.colony().workers();
                    found += t.colony().totalFound();
                }
            }
        }
        List<WildAnt> ants = level.getEntitiesOfClass(WildAnt.class, new AABB(origin).inflate(160, 120, 160));
        long anthill = ants.stream().filter(a -> a.anthill() != null).count();
        long alarmed = ants.stream().filter(WildAnt::alarmed).count();
        long fighting = ants.stream().filter(a -> a.getTarget() != null).count();
        long queens = ants.stream().filter(a -> a.caste() == Caste.QUEEN && a.anthill() != null).count();
        return String.format(Locale.ROOT, "%d colonies, %.0f workers, %d finds in all; %d wild ants (%d of anthills, %d great Queens; %d alarmed, %d with a target)",
                colonies, workers, found, ants.size(), anthill, queens, alarmed, fighting);
    }

    // --- Building blocks ---

    private static void colony(ServerLevel level, BlockPos pos, String species, double workers, double food) {
        level.setBlock(pos, ModBlocks.TERRARIUM.get().defaultBlockState().setValue(ColonyEntityBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        TerrariumBlockEntity terrarium = (TerrariumBlockEntity) level.getBlockEntity(pos);
        terrarium.colony().setQueen(Genetics.wildQueen(AntColonies.id(species), RandomSource.create(species.hashCode())));
        terrarium.colony().setPopulation(20, 30, workers);
        terrarium.colony().setFood(food);
    }

    private static StructureBlockEntity structure(ServerLevel level, BlockPos pos, StructureKind kind) {
        level.setBlock(pos, ModBlocks.STRUCTURES.get(kind).get().defaultBlockState(), Block.UPDATE_ALL);
        return (StructureBlockEntity) level.getBlockEntity(pos);
    }

    private static List<BlockPos> places(BlockPos center) {
        return new ArrayList<>(List.of(center.offset(-1, 0, -1), center.north(), center.offset(1, 0, -1), center.east(), center.offset(1, 0, 1),
                center.south(), center.offset(-1, 0, 1), center.west()));
    }

    private static void connector(ServerLevel level, BlockPos pos, int channel, int foodMode, int reserve, int target) {
        StructureBlockEntity connector = structure(level, pos, StructureKind.COLONY_CONNECTOR);
        var settings = connector.connector();
        settings.channel = channel;
        settings.foodMode = foodMode;
        settings.reserve = reserve;
        settings.target = target;
        settings.itemMode = 0;
        connector.connectorChanged();
    }
}
