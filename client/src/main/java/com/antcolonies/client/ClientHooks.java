package com.antcolonies.client;

import com.antcolonies.content.block.ColonyEntityBlock;
import com.antcolonies.content.blockentity.GeneticLabBlockEntity;
import com.antcolonies.content.blockentity.StructureBlockEntity;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.client.screen.IdentificationBookScreen;
import com.antcolonies.client.terrarium.TerrariumVisuals;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Calls from common code that only ever run on the client (block entity client ticks, block events, animateTick,
 * item use). Only this class touches client classes, and nothing on a dedicated server calls it.
 */
public final class ClientHooks {
    public static void terrariumTick(TerrariumBlockEntity terrarium) {
        TerrariumVisuals visuals = visuals(terrarium);
        // The ants only move while someone is close enough to see them (they aren't drawn beyond the visual distance).
        visuals.tick(terrarium, near(terrarium.getBlockPos(), AntConfig.client(AntConfig.VISUAL_DISTANCE) + 8));
        Level level = terrarium.getLevel();
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
        if (!(terrarium.clientVisuals instanceof TerrariumVisuals visuals) || visuals.frontFace != front) {
            TerrariumVisuals created = new TerrariumVisuals(terrarium.getBlockPos().asLong(), front);
            terrarium.clientVisuals = created;
            return created;
        }
        return visuals;
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
                visuals(terrarium).evolved();
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
        int color = switch (kind) {
            case HARVESTING_SITE -> 0xFF5DAA3C;
            case HUNTING_GROUND -> 0xFF8A1F1F;
            default -> 0xFF6B4A2E;
        };
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
