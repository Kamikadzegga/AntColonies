package com.antcolonies.content.block;

import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.client.ClientHooks;
import com.antcolonies.core.registry.ModBlockEntities;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The Terrarium: a glass formicarium with a living colony inside. Right-click with a Queen Test Tube to put a Queen in,
 * with food, evolution materials, caught ants, eggs or larvae to feed them; empty-handed to open the colony's overview.
 */
public class TerrariumBlock extends ColonyEntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public TerrariumBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerrariumBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, ModBlockEntities.TERRARIUM.get(), (l, pos, s, be) -> ClientHooks.terrariumTick(be));
        }
        return createTickerHelper(type, ModBlockEntities.TERRARIUM.get(), TerrariumBlockEntity::serverTick);
    }

    /** Things for the colony go straight into the right slot. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof TerrariumBlockEntity terrarium) || stack.isEmpty() || player.isShiftKeyDown()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (stack.is(ModItems.TWEEZERS.get()) || stack.is(ModItems.COLONY_SCANNER.get()) || stack.is(ModItems.GENETIC_ANALYZER.get())) {
            return InteractionResult.PASS;
        }
        if (stack.is(ModItems.RALLY_PHEROMONE.get())) {
            if (level instanceof net.minecraft.server.level.ServerLevel) {
                recruitGuard(terrarium, player, stack);
            }
            return InteractionResult.SUCCESS;
        }
        int slot;
        if (stack.is(ModItems.QUEEN_TEST_TUBE.get())) {
            slot = TerrariumBlockEntity.SLOT_QUEEN;
            ItemStack inSlot = terrarium.getItem(slot);
            if (!inSlot.isEmpty() && !inSlot.is(ModItems.TEST_TUBE.get()) || terrarium.colony().queen() != null) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            if (!level.isClientSide()) {
                if (!inSlot.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(inSlot, net.minecraft.util.Prediction.SERVER_ONLY);
                }
                terrarium.setItem(slot, stack.split(1));
            }
            return InteractionResult.SUCCESS;
        }
        slot = TerrariumBlockEntity.SLOT_FEED;
        ItemStack inSlot = terrarium.getItem(slot);
        boolean feedable = stack.has(ModDataComponents.ANTS.get()) || stack.is(ModItems.ANT_EGG.get()) || stack.is(ModItems.ANT_LARVA.get())
                || stack.is(ModItems.EVOLUTION_CATALYST.get()) || com.antcolonies.core.registry.ModDataMaps.food(stack) != null
                || com.antcolonies.colony.evolution.EvolutionLogic.find(level.registryAccess(), terrarium.colony(), stack) != null;
        if (!feedable) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!inSlot.isEmpty() && !ItemStack.isSameItemSameComponents(inSlot, stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            int room = stack.getMaxStackSize() - inSlot.getCount();
            int moved = Math.min(room, player.isCreative() ? stack.getCount() : stack.getCount());
            if (moved > 0) {
                ItemStack put = stack.copyWithCount(inSlot.getCount() + moved);
                if (!player.isCreative()) {
                    stack.shrink(moved);
                }
                terrarium.setItem(slot, put);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A Rally Pheromone used on a Terrarium: the colony gives up {@link com.antcolonies.core.config.AntConfig#GUARD_SOLDIER_COST}
     * soldiers for one Guard Ant, as strong as its Queen's Soldier Strength.
     */
    public static boolean recruitGuard(TerrariumBlockEntity terrarium, Player player, ItemStack pheromone) {
        com.antcolonies.colony.model.Colony colony = terrarium.colony();
        int cost = com.antcolonies.core.config.AntConfig.get(com.antcolonies.core.config.AntConfig.GUARD_SOLDIER_COST);
        if (colony.queen() == null) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.antcolonies.guard.no_queen")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        if (!colony.takeSoldiers(cost)) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.antcolonies.guard.soldiers", cost, colony.soldiers())
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        int strength = colony.queen().genome().level(com.antcolonies.colony.genetics.Trait.SOLDIER_STRENGTH);
        ItemStack tube = new ItemStack(ModItems.GUARD_ANT.get());
        tube.set(ModDataComponents.GUARD.get(), new com.antcolonies.content.item.GuardData(colony.species(), Math.clamp(strength, 1, 10), -1));
        pheromone.consume(1, player);
        player.getInventory().placeItemBackInInventory(tube, net.minecraft.util.Prediction.SERVER_ONLY);
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.antcolonies.guard.recruited", cost)
                .withStyle(net.minecraft.ChatFormatting.GREEN));
        terrarium.setChanged();
        terrarium.syncNow();
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        ClientHooks.terrariumParticles(level, pos, random);
    }

    /** The tooltip of a Terrarium item that carries a colony. */
    public static void describeColony(ItemStack stack, Consumer<net.minecraft.network.chat.Component> tooltip) {
        var data = stack.get(ModDataComponents.COLONY.get());
        if (data == null) {
            return;
        }
        var tag = data.copyTag();
        String species = tag.getStringOr("species", "");
        long workers = (long) tag.getDoubleOr("workers", 0);
        long soldiers = (long) tag.getDoubleOr("soldiers", 0);
        boolean queen = tag.contains("queen");
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(species);
        List<net.minecraft.network.chat.Component> lines = List.of(
                net.minecraft.network.chat.Component.translatable("tooltip.antcolonies.terrarium.colony",
                        id == null ? species : net.minecraft.network.chat.Component.translatable(com.antcolonies.colony.species.AntSpecies.translationKey(id)))
                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                net.minecraft.network.chat.Component.translatable("tooltip.antcolonies.terrarium.population", workers, soldiers)
                        .withStyle(net.minecraft.ChatFormatting.GRAY),
                net.minecraft.network.chat.Component.translatable(queen ? "tooltip.antcolonies.terrarium.queen" : "tooltip.antcolonies.terrarium.no_queen")
                        .withStyle(queen ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
        lines.forEach(tooltip);
    }
}
