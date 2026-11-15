package com.antcolonies.client;

import com.antcolonies.content.block.ColonyEntityBlock;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.client.screen.IdentificationBookScreen;
import com.antcolonies.client.nest.NestLayout;
import com.antcolonies.client.nest.NestVisuals;
import com.antcolonies.client.terrarium.TerrariumVisuals;
import com.antcolonies.colony.species.AntSpecies;
import com.antcolonies.content.block.ColonyPartBlock;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.colony.model.StructureKind;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.core.registry.ModParticles;
import com.antcolonies.core.registry.ModSounds;
import com.antcolonies.colony.species.AntLook;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Calls from common code that only ever run on the client (block entity client ticks, block events, animateTick,
 * item use). Only this class touches client classes, and nothing on a dedicated server calls it.
 */
public final class ClientHooks {
    /**
     * A clock for animations: game time plus the partial tick, wrapped every 2^18 ticks (about 3.6 hours). A float of the
     * whole game time loses the partial tick after 58 hours of world age and steps by 2 ticks after 233, so animations
     * would stutter on old worlds; the wrap is one small jump every few hours instead.
     */
    public static float animationTime(long gameTime, float partialTicks) {
        return (gameTime & ((1 << 18) - 1)) + partialTicks;
    }

    public static void terrariumTick(TerrariumBlockEntity terrarium) {
        Level level = terrarium.getLevel();
        if (level != null && ColonyPartBlock.upright(terrarium.getBlockState())) {
            // In a stack: an upright nest.
            nestTick(terrarium, level);
        } else {
            TerrariumVisuals visuals = visuals(terrarium);
            // The ants only move while someone is close enough to see them (they aren't drawn beyond the visual distance).
            visuals.tick(terrarium, near(terrarium.getBlockPos(), AntConfig.client(AntConfig.VISUAL_DISTANCE) + 8));
        }
        if (level != null && AntConfig.client(AntConfig.AMBIENT_SOUNDS) && terrarium.colony().adults() > 0
                && level.getRandom().nextInt(240) == 0 && near(terrarium.getBlockPos(), 12)) {
            level.playLocalSound(terrarium.getBlockPos(), ModSounds.COLONY_AMBIENCE.get(), SoundSource.BLOCKS, 0.25F,
                    0.9F + level.getRandom().nextFloat() * 0.2F, false);
        }
    }

    public static TerrariumVisuals visuals(TerrariumBlockEntity terrarium) {
        BlockState state = terrarium.getBlockState();
        int front = switch (state.hasProperty(ColonyEntityBlock.FACING) ? state.getValue(ColonyEntityBlock.FACING) : Direction.NORTH) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        // The Queen's chamber goes on the side the Formicarium faces, unless a chamber stands there (no glass, the soil runs
        // on): then on the next open side, so she can still be seen.
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        if (state.getBlock() instanceof ColonyPartBlock && state.getValue(ColonyPartBlock.CONNECTED.get(sides[front]))) {
            for (int k = 1; k < 4; k++) {
                int side = (front + k) % 4;
                if (!state.getValue(ColonyPartBlock.CONNECTED.get(sides[side]))) {
                    front = side;
                    break;
                }
            }
        }
        if (!(terrarium.clientVisuals instanceof TerrariumVisuals visuals) || visuals.frontFace != front) {
            TerrariumVisuals created = new TerrariumVisuals(terrarium.getBlockPos().asLong(), front);
            terrarium.clientVisuals = created;
            return created;
        }
        return visuals;
    }

    // ------------------------------------------------------------------ The upright nest (colony blocks in a stack)

    /** A stacked Formicarium's tick: its nest follows its colony. */
    private static void nestTick(TerrariumBlockEntity terrarium, Level level) {
        BlockPos pos = terrarium.getBlockPos();
        Direction facing = facing(terrarium.getBlockState());
        Colony colony = terrarium.colony();
        AntSpecies species = ClientSpecies.get(colony.species());
        AntLook look = species == null ? AntLook.UNKNOWN : species.look();
        NestVisuals.Brief brief = new NestVisuals.Brief(NestLayout.Kind.FORMICARIUM, pos.asLong(), gates(level, pos, facing, pos), colony, 0,
                colony.adults() > 0, species != null && species.canHunt(), look.crystal().map(c -> 0xFF000000 | c).orElse(0xFFD9B26A),
                AntConfig.client(AntConfig.MAX_VISUAL_ANTS));
        NestVisuals visuals;
        if (terrarium.clientVisuals instanceof NestVisuals nest) {
            visuals = nest;
        } else {
            visuals = new NestVisuals(pos.asLong());
            terrarium.clientVisuals = visuals;
        }
        visuals.tick(brief, host(level, pos, facing, pos), near(pos, AntConfig.client(AntConfig.VISUAL_DISTANCE) + 8));
    }

    /** A chamber's client tick. Only a chamber in a stack has anything to do: its nest follows the colony it is joined to. */
    public static void structureTick(StructureBlockEntity structure) {
        Level level = structure.getLevel();
        if (level == null) {
            return;
        }
        if (!ColonyPartBlock.upright(structure.getBlockState())) {
            structure.clientVisuals = null;
            return;
        }
        BlockPos pos = structure.getBlockPos();
        Direction facing = facing(structure.getBlockState());
        BlockPos home = structure.terrarium();
        Colony colony = colonyOf(structure);
        AntSpecies species = colony == null ? null : ClientSpecies.get(colony.species());
        AntLook look = species == null ? AntLook.UNKNOWN : species.look();
        // What ants carry out of this chamber.
        int carry = switch (structure.kind()) {
            case LIQUID_CHAMBER -> 0xFF000000 | look.crop().orElse(0x9AD8FF);
            case HARVESTING_SITE -> 0xFF5DAA3C;
            case HUNTING_GROUND -> 0xFF9C3A32;
            case NETHER_FORAGING_SITE -> 0xFFE6DCD0;
            case END_FORAGING_SITE -> 0xFFB48CD9;
            default -> look.crystal().map(c -> 0xFF000000 | c).orElse(0xFFD9B26A);
        };
        boolean ants = colony != null && structure.clientLinked() && AntConfig.client(AntConfig.STRUCTURE_ANTS);
        NestVisuals.Brief brief = new NestVisuals.Brief(NestLayout.Kind.valueOf(structure.kind().name()), pos.asLong(), gates(level, pos, facing, home),
                colony, structure.assigned(), ants, species != null && species.canHunt(), carry, 8);
        NestVisuals visuals;
        if (structure.clientVisuals instanceof NestVisuals nest) {
            visuals = nest;
        } else {
            visuals = new NestVisuals(pos.asLong());
            structure.clientVisuals = visuals;
        }
        visuals.tick(brief, host(level, pos, facing, home), near(pos, AntConfig.client(AntConfig.VISUAL_DISTANCE) + 8));
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(ColonyEntityBlock.FACING) ? state.getValue(ColonyEntityBlock.FACING) : Direction.NORTH;
    }

    /** The colony a chamber is joined to, as this client knows it. */
    public static @Nullable Colony colonyOf(StructureBlockEntity structure) {
        BlockPos home = structure.terrarium();
        return home != null && structure.getLevel() != null && structure.getLevel().getBlockEntity(home) instanceof TerrariumBlockEntity terrarium
                ? terrarium.colony() : null;
    }

    /** The Formicarium of the colony a block is part of: its own position for a Formicarium, null for a chamber joined to none. */
    private static @Nullable BlockPos home(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof TerrariumBlockEntity ? blockEntity.getBlockPos()
                : blockEntity instanceof StructureBlockEntity structure ? structure.terrarium() : null;
    }

    /** The direction of a side of a block's face (NestLayout.LEFT..DOWN), as seen from in front of it. */
    private static Direction direction(Direction facing, int side) {
        return switch (side) {
            case NestLayout.LEFT -> facing.getClockWise();
            case NestLayout.RIGHT -> facing.getCounterClockWise();
            case NestLayout.UP -> Direction.UP;
            default -> Direction.DOWN;
        };
    }

    /** Whether the block at a position is an upright nest whose glasses look the same two ways as a block facing {@code facing}. */
    private static boolean nestBeside(Level level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return ColonyPartBlock.upright(state) && facing(state).getAxis() == facing.getAxis();
    }

    /**
     * Where a stacked block's tunnels cross each side of its face: on every side where another upright nest stands the
     * same way round (but not a part of a different colony), at a spot worked out from the two blocks' positions, so
     * both dig to the same one.
     */
    private static int[] gates(Level level, BlockPos pos, Direction facing, @Nullable BlockPos home) {
        int[] gates = {-1, -1, -1, -1};
        for (int side = 0; side < 4; side++) {
            Direction direction = direction(facing, side);
            BlockPos other = pos.relative(direction);
            if (!nestBeside(level, other, facing)) {
                continue;
            }
            BlockPos theirs = home(level.getBlockEntity(other));
            if (home != null && theirs != null && !home.equals(theirs)) {
                continue;
            }
            BlockPos lower = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? pos : other;
            boolean vertical = direction.getAxis().isVertical();
            int at = NestLayout.GATE_MIN + NestLayout.crossing(lower.asLong(), direction.getAxis().ordinal() + (vertical ? 3 + facing.getAxis().ordinal() : 0));
            // Across a side wall the spot is a height, the same for both blocks. Across the top or bottom it is a place
            // along the world axis the face runs along, which u runs with (facing south or west) or against.
            gates[side] = !vertical || facing == Direction.SOUTH || facing == Direction.WEST ? at : 15 - at;
        }
        return gates;
    }

    private static NestVisuals.Host host(Level level, BlockPos pos, Direction facing, @Nullable BlockPos home) {
        return new NestVisuals.Host() {
            @Override
            public @Nullable NestVisuals neighbour(int side) {
                BlockPos at = pos.relative(direction(facing, side));
                BlockEntity other = level.getBlockEntity(at);
                if (other == null || home == null || !home.equals(home(other)) || !nestBeside(level, at, facing)) {
                    return null;
                }
                Object visuals = other instanceof TerrariumBlockEntity terrarium ? terrarium.clientVisuals
                        : other instanceof StructureBlockEntity structure ? structure.clientVisuals : null;
                return visuals instanceof NestVisuals nest ? nest : null;
            }

            @Override
            public int homeward() {
                if (home == null || home.equals(pos)) {
                    return -1;
                }
                int best = -1;
                int nearest = pos.distManhattan(home);
                for (int side = 0; side < 4; side++) {
                    int distance = pos.relative(direction(facing, side)).distManhattan(home);
                    if (distance < nearest && neighbour(side) != null) {
                        nearest = distance;
                        best = side;
                    }
                }
                return best;
            }

            @Override
            public int entry(int side) {
                // The neighbour may face the other way: its own side that looks back at this block.
                Direction back = direction(facing, side).getOpposite();
                Direction theirs = facing(level.getBlockState(pos.relative(direction(facing, side))));
                for (int s = 0; s < 4; s++) {
                    if (direction(theirs, s) == back) {
                        return s;
                    }
                }
                return side ^ 1;
            }
        };
    }

    /** A point just outside one of a stacked block's two glasses (either, by chance): across it by {@code u} and up it by {@code v} (0 to 1). */
    private static double[] glass(Level level, BlockPos pos, RandomSource random, double u, double v) {
        Direction facing = facing(level.getBlockState(pos));
        Direction across = facing.getClockWise();
        double out = random.nextBoolean() ? 0.53 : -0.53;
        return new double[] {pos.getX() + 0.5 + facing.getStepX() * out + across.getStepX() * (u - 0.5), pos.getY() + v,
                pos.getZ() + 0.5 + facing.getStepZ() * out + across.getStepZ() * (u - 0.5)};
    }

    private static boolean near(BlockPos pos, double distance) {
        var player = Minecraft.getInstance().player;
        return player != null && player.blockPosition().distSqr(pos) < distance * distance;
    }

    public static void terrariumEvent(TerrariumBlockEntity terrarium, int id) {
        Level level = terrarium.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = terrarium.getBlockPos();
        AntLook look = ClientSpecies.look(terrarium.colony().species());
        int color = look.crystal().orElse(look.gaster());
        RandomSource random = level.getRandom();
        switch (id) {
            case TerrariumBlockEntity.EVENT_EVOLVED -> {
                if (terrarium.clientVisuals instanceof NestVisuals nest) {
                    nest.evolved();
                } else {
                    visuals(terrarium).evolved();
                }
                for (int i = 0; i < 40; i++) {
                    burst(level, ModParticles.EVOLUTION.get(), color, pos, random, 0.08);
                }
                for (int i = 0; i < 20; i++) {
                    burst(level, ModParticles.SPARKLE.get(), 0xFFFFFF, pos, random, 0.05);
                }
            }
            case TerrariumBlockEntity.EVENT_ABSORBED -> {
                for (int i = 0; i < 8; i++) {
                    burst(level, ModParticles.GENE.get(), color, pos, random, 0.02);
                }
            }
            case TerrariumBlockEntity.EVENT_QUEEN -> {
                for (int i = 0; i < 12; i++) {
                    burst(level, ModParticles.SPARKLE.get(), 0xFFE070, pos, random, 0.03);
                }
            }
            case TerrariumBlockEntity.EVENT_QUEEN_GREW -> {
                // The Queen grew: genes in her colours, and gold sparkles.
                for (int i = 0; i < 16; i++) {
                    burst(level, ModParticles.GENE.get(), color, pos, random, 0.04);
                }
                for (int i = 0; i < 16; i++) {
                    burst(level, ModParticles.SPARKLE.get(), 0xFFE070, pos, random, 0.04);
                }
            }
            default -> {
            }
        }
    }

    public static void labEvent(GeneticLabBlockEntity lab, int id) {
        Level level = lab.getLevel();
        if (level == null) {
            return;
        }
        for (int i = 0; i < 24; i++) {
            burst(level, ModParticles.GENE.get(), i % 2 == 0 ? 0x4FE3FF : 0xE04FFF, lab.getBlockPos().above(), level.getRandom(), 0.05);
        }
    }

    private static void burst(Level level, ParticleType<ColorParticleOption> type, int rgb, BlockPos pos, RandomSource random, double speed) {
        if (!AntConfig.client(AntConfig.PARTICLES)) {
            return;
        }
        level.addParticle(ColorParticleOption.create(type, ARGB.opaque(rgb)), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                pos.getY() + 0.5 + random.nextDouble() * 0.6, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                (random.nextDouble() - 0.5) * speed, random.nextDouble() * speed, (random.nextDouble() - 0.5) * speed);
    }

    /** A colony at work kicks up soil on its surface; a stage 4 colony of a crystal species sparkles. */
    public static void terrariumParticles(Level level, BlockPos pos, RandomSource random) {
        if (!AntConfig.client(AntConfig.PARTICLES) || !(level.getBlockEntity(pos) instanceof TerrariumBlockEntity terrarium)) {
            return;
        }
        Colony colony = terrarium.colony();
        if (colony.adults() <= 0) {
            return;
        }
        if (ColonyPartBlock.upright(terrarium.getBlockState())) {
            // In a stack the soil is behind the glasses: a little falls before them.
            if (random.nextInt(4) == 0) {
                double[] at = glass(level, pos, random, 0.2 + random.nextDouble() * 0.6, 0.2 + random.nextDouble() * 0.5);
                level.addParticle(ColorParticleOption.create(ModParticles.SOIL.get(), 0xFF6B4A2E), at[0], at[1], at[2], 0, -0.01, 0);
            }
            AntLook nestLook = ClientSpecies.look(colony.species());
            if (colony.stage() >= 4 && nestLook.crystal().isPresent() && random.nextInt(2) == 0) {
                double[] at = glass(level, pos, random, 0.2 + random.nextDouble() * 0.6, 0.15 + random.nextDouble() * 0.3);
                level.addParticle(ColorParticleOption.create(ModParticles.SPARKLE.get(), ARGB.opaque(nestLook.crystal().get())), at[0], at[1], at[2], 0, 0.005, 0);
            }
            return;
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ColorParticleOption.create(ModParticles.SOIL.get(), 0xFF6B4A2E), pos.getX() + 0.2 + random.nextDouble() * 0.6,
                    pos.getY() + 0.72, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
        }
        AntLook look = ClientSpecies.look(colony.species());
        if (colony.stage() >= 4 && look.crystal().isPresent() && random.nextInt(2) == 0) {
            level.addParticle(ColorParticleOption.create(ModParticles.SPARKLE.get(), ARGB.opaque(look.crystal().get())),
                    pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 0.75 + random.nextDouble() * 0.2,
                    pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0, 0.005, 0);
        }
        if (colony.activity().laying() && random.nextInt(6) == 0) {
            level.addParticle(ColorParticleOption.create(ModParticles.CRUMB.get(), 0xFFF6F2E6), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    0, 0.01, 0);
        }
    }

    public static void structureParticles(Level level, BlockPos pos, StructureKind kind, RandomSource random) {
        if (!AntConfig.client(AntConfig.PARTICLES) || !(level.getBlockEntity(pos) instanceof StructureBlockEntity structure)
                || !structure.clientLinked() || structure.assigned() <= 0) {
            return;
        }
        if (kind == StructureKind.LIQUID_CHAMBER) {
            return; // nothing is dug or cut there
        }
        int color = switch (kind) {
            case HARVESTING_SITE -> 0xFF5DAA3C;
            case HUNTING_GROUND -> 0xFF8A1F1F;
            default -> 0xFF6B4A2E;
        };
        if (ColonyPartBlock.upright(structure.getBlockState())) {
            if (random.nextInt(4) == 0) {
                double[] at = glass(level, pos, random, 0.2 + random.nextDouble() * 0.6, 0.25 + random.nextDouble() * 0.4);
                level.addParticle(ColorParticleOption.create(kind == StructureKind.HARVESTING_SITE ? ModParticles.CRUMB.get() : ModParticles.SOIL.get(), color),
                        at[0], at[1], at[2], 0, -0.01, 0);
            }
            return;
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ColorParticleOption.create(kind == StructureKind.HARVESTING_SITE ? ModParticles.CRUMB.get() : ModParticles.SOIL.get(), color),
                    pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 0.3, pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0, 0.03, 0);
        }
    }

    /** Gene particles over a working Genetic Laboratory, in its four colors. */
    public static void labParticles(Level level, BlockPos pos, RandomSource random) {
        if (!AntConfig.client(AntConfig.PARTICLES) || random.nextInt(3) != 0) {
            return;
        }
        int[] colors = {0xFF3FB8E0, 0xFF7FE04F, 0xFFE0B040, 0xFFE04FFF};
        int color = colors[random.nextInt(colors.length)];
        level.addParticle(ColorParticleOption.create(ModParticles.GENE.get(), color), pos.getX() + 0.2 + random.nextDouble() * 0.6,
                pos.getY() + 1.0, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.01, 0);
    }

    public static void nestParticles(Level level, BlockPos pos, RandomSource random) {
        if (AntConfig.client(AntConfig.PARTICLES) && random.nextInt(4) == 0) {
            level.addParticle(ColorParticleOption.create(ModParticles.SOIL.get(), 0xFF6B4A2E), pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.6, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }

    public static void openBook() {
        Minecraft.getInstance().setScreen(new IdentificationBookScreen());
    }

    private ClientHooks() {}
}
