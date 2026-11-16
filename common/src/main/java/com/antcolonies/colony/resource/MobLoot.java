package com.antcolonies.colony.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Simulated hunts: one roll of a mob's own loot table, for a stand-in kept off-world and never spawned. No player
 * killed it, so player-only drops don't come.
 */
public final class MobLoot {
    private static final Map<ServerLevel, Map<EntityType<?>, Optional<LivingEntity>>> STAND_INS = new java.util.concurrent.ConcurrentHashMap<>();

    public static List<ItemStack> roll(ServerLevel level, EntityType<?> type, BlockPos pos) {
        Optional<LivingEntity> standIn = STAND_INS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(type, t -> create(level, t));
        if (standIn.isEmpty()) {
            return List.of();
        }
        LivingEntity mob = standIn.get();
        Optional<ResourceKey<LootTable>> key = mob.getLootTable();
        if (key.isEmpty()) {
            return List.of();
        }
        mob.setPos(Vec3.atCenterOf(pos));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key.get());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        List<ItemStack> drops = new ArrayList<>();
        table.getRandomItems(params, drops::add);
        drops.removeIf(ItemStack::isEmpty);
        return drops;
    }

    private static Optional<LivingEntity> create(ServerLevel level, EntityType<?> type) {
        try {
            Entity entity = type.create(level, EntitySpawnReason.LOAD);
            return entity instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Forgets a level's stand-ins when it unloads: they hold the level, so the map would keep it alive. */
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            STAND_INS.remove(level);
        }
    }

    private MobLoot() {}
}
