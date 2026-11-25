package com.antcolonies.content.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.QueenData;
import com.antcolonies.colony.research.Discovery;
import com.antcolonies.core.registry.ModAttachments;
import com.antcolonies.core.registry.ModDataComponents;
import com.antcolonies.core.registry.ModItems;
import com.antcolonies.core.registry.ModSounds;
import com.antcolonies.colony.species.Caste;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Tweezers catch ants into Test Tubes: a wild ant, workers from a wild nest, or workers or eggs from a Terrarium. Workers
 * go into a tube of the same ants that has room; only without one is an empty Test Tube needed.
 */
public class TweezersItem extends Item {
    public TweezersItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof WildAnt ant)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack caught;
        if (ant.caste() == Caste.QUEEN) {
            caught = queenTube(Genetics.wildQueen(ant.species(), player.getRandom()));
        } else {
            // A caught soldier becomes a worker: colonies have no soldiers.
            caught = antTube(new AntSample(ant.species(), Caste.WORKER, 1));
        }
        Component name = caught.getHoverName();
        if (!store(player, caught)) {
            return InteractionResult.FAIL;
        }
        ant.caught();
        captured(player, stack, hand, name);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState state = level.getBlockState(pos);
        if (player == null) {
            return InteractionResult.PASS;
        }
        boolean nest = state.getBlock() instanceof WildNestBlock;
        boolean terrarium = level.getBlockEntity(pos) instanceof TerrariumBlockEntity;
        if (!nest && !terrarium) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        if (nest) {
            WildNestBlock block = (WildNestBlock) state.getBlock();
            if (state.getValue(WildNestBlock.RECOVERING)) {
                int seconds = WildNestBlock.secondsToRecover(level, pos, state);
                player.sendOverlayMessage((seconds > 0
                        ? Component.translatable("message.antcolonies.nest.recovering_in", WildNestBlock.timer(seconds))
                        : Component.translatable("message.antcolonies.nest.recovering")).withStyle(ChatFormatting.GOLD));
                return InteractionResult.FAIL;
            }
            Identifier species = block.speciesAt(serverLevel, pos);
            boolean queen = firstNestCatch(player, species) || level.getRandom().nextDouble() < AntConfig.get(AntConfig.NEST_QUEEN_CHANCE);
            ItemStack caught = queen
                    ? queenTube(Genetics.wildQueen(species, level.getRandom()))
                    : antTube(new AntSample(species, Caste.WORKER, 2 + level.getRandom().nextInt(4)));
            Component name = caught.getHoverName();
            if (!store(player, caught)) {
                return InteractionResult.FAIL;
            }
            rememberNest(player, species);
            block.raid(serverLevel, pos, state, player);
            captured(player, context.getItemInHand(), context.getHand(), name);
            return InteractionResult.SUCCESS;
        }
        TerrariumBlockEntity be = (TerrariumBlockEntity) level.getBlockEntity(pos);
        Colony colony = be.colony();
        Component name;
        if (player.isShiftKeyDown()) {
            int items = (int) Math.min(16, colony.eggs() / 4);
            if (items <= 0) {
                player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_eggs").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            colony.addEggs(-items * 4.0);
            // Eggs are carried as they are: no Test Tube.
            ItemStack eggs = new ItemStack(ModItems.ANT_EGG.get(), items);
            eggs.set(ModDataComponents.SPECIES.get(), colony.species());
            name = eggs.getHoverName();
            Discovery.species(player, colony.species());
            player.getInventory().placeItemBackInInventory(eggs);
        } else {
            int count = (int) Math.min(AntSample.pinch(), colony.workers() - colony.maintenanceWorkers());
            if (count <= 0) {
                player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_workers").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            ItemStack caught = antTube(new AntSample(colony.species(), Caste.WORKER, count));
            name = caught.getHoverName();
            if (!store(player, caught)) {
                return InteractionResult.FAIL;
            }
            colony.addWorkers(-count);
        }
        be.setChanged();
        be.syncNow();
        captured(player, context.getItemInHand(), context.getHand(), name);
        return InteractionResult.SUCCESS;
    }

    /** A player's first catch from each kind of wild nest is always a Queen. A machine posing as a player never gets one this way. */
    private static boolean firstNestCatch(Player player, Identifier species) {
        return AntConfig.get(AntConfig.FIRST_NEST_CATCH_QUEEN) && !(player instanceof FakePlayer)
                && !player.getData(ModAttachments.NESTS_CAUGHT).contains(species);
    }

    private static void rememberNest(Player player, Identifier species) {
        List<Identifier> caught = player.getData(ModAttachments.NESTS_CAUGHT);
        if (!caught.contains(species)) {
            List<Identifier> more = new ArrayList<>(caught);
            more.add(species);
            player.setData(ModAttachments.NESTS_CAUGHT, List.copyOf(more));
        }
    }

    private static void captured(Player player, ItemStack tweezers, InteractionHand hand, Component caught) {
        player.level().playSound(null, player.blockPosition(), ModSounds.CAPTURE.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
        player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.caught", caught).withStyle(ChatFormatting.GREEN));
        tweezers.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    public static ItemStack queenTube(QueenData queen) {
        ItemStack tube = new ItemStack(ModItems.QUEEN_TEST_TUBE.get());
        tube.set(ModDataComponents.QUEEN.get(), queen);
        return tube;
    }

    public static ItemStack antTube(AntSample sample) {
        ItemStack tube = new ItemStack(ModItems.ANT_TEST_TUBE.get());
        tube.set(ModDataComponents.ANTS.get(), sample);
        return tube;
    }

    /** The Terrarium's take button: up to a tube's worth ({@link AntSample#MAX}) of the colony's workers, into a Test Tube. */
    public static boolean takeFromColony(Player player, TerrariumBlockEntity terrarium) {
        Colony colony = terrarium.colony();
        int count = (int) Math.min(AntSample.MAX, colony.workers());
        if (count <= 0) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_workers").withStyle(ChatFormatting.RED));
            return false;
        }
        ItemStack caught = antTube(new AntSample(colony.species(), Caste.WORKER, count));
        Component name = caught.getHoverName();
        if (!store(player, caught)) {
            return false;
        }
        colony.addWorkers(-count);
        player.level().playSound(null, player.blockPosition(), ModSounds.CAPTURE.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
        player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.caught", name).withStyle(ChatFormatting.GREEN));
        terrarium.setChanged();
        terrarium.syncNow();
        return true;
    }

    /**
     * Puts a caught tube in the player's inventory. Workers top up a tube of the same ants that has room for all of them;
     * anything else takes an empty Test Tube. Without either, the player is told and nothing is caught.
     */
    private static boolean store(Player player, ItemStack caught) {
        Inventory inventory = player.getInventory();
        AntSample sample = caught.get(ModDataComponents.ANTS.get());
        int tube = sample == null ? -1 : tubeWithRoom(inventory, sample);
        if (tube < 0 && !takeEmptyTube(player)) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_tube").withStyle(ChatFormatting.RED));
            return false;
        }
        // Whoever catches an ant has met its species.
        QueenData queen = caught.get(ModDataComponents.QUEEN.get());
        if (queen != null || sample != null) {
            Discovery.species(player, queen != null ? queen.species() : sample.species());
        }
        if (tube >= 0) {
            ItemStack held = inventory.getItem(tube);
            AntSample there = held.get(ModDataComponents.ANTS.get());
            held.set(ModDataComponents.ANTS.get(), there.withCount(there.count() + sample.count()));
        } else {
            inventory.placeItemBackInInventory(caught);
        }
        return true;
    }

    /** The slot of a single tube of the same ants that can take all of the sample, or -1. */
    private static int tubeWithRoom(Inventory inventory, AntSample sample) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack other = inventory.getItem(slot);
            AntSample there = other.get(ModDataComponents.ANTS.get());
            if (there != null && other.getCount() == 1 && there.species().equals(sample.species()) && there.caste() == sample.caste()
                    && there.count() + sample.count() <= AntSample.MAX) {
                return slot;
            }
        }
        return -1;
    }

    /** Takes one empty Test Tube from the player (a creative player needs none); false if they have none. */
    private static boolean takeEmptyTube(Player player) {
        if (player.isCreative()) {
            return true;
        }
        Inventory inventory = player.getInventory();
        int slot = inventory.findSlotMatchingItem(new ItemStack(ModItems.TEST_TUBE.get()));
        if (slot < 0) {
            return false;
        }
        inventory.removeItem(slot, 1);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.tweezers").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.tweezers.terrarium").withStyle(ChatFormatting.DARK_GRAY));
    }
}
