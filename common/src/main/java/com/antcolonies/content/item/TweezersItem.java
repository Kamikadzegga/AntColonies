package com.antcolonies.content.item;

import java.util.function.Consumer;

import com.antcolonies.content.block.WildNestBlock;
import com.antcolonies.content.blockentity.TerrariumBlockEntity;
import com.antcolonies.colony.model.Colony;
import com.antcolonies.core.config.AntConfig;
import com.antcolonies.content.entity.WildAnt;
import com.antcolonies.colony.genetics.Genetics;
import com.antcolonies.colony.genetics.QueenData;
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

/**
 * Tweezers catch ants into empty Test Tubes from the player's inventory: a wild ant (right-click it), a few workers
 * from a wild nest (sometimes its Queen), or workers from a Terrarium's colony (sneak for eggs instead), to start or
 * strengthen another colony of the same species.
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
        if (!hasEmptyTube(player)) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_tube").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        ItemStack caught;
        if (ant.caste() == Caste.QUEEN) {
            caught = queenTube(Genetics.wildQueen(ant.species(), player.getRandom()));
        } else {
            // A caught soldier becomes a worker: colonies have no soldiers.
            caught = antTube(new AntSample(ant.species(), Caste.WORKER, 1));
        }
        takeEmptyTube(player);
        give(player, caught);
        ant.discard();
        captured(player, stack, hand, caught);
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
        if (!hasEmptyTube(player)) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_tube").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        if (nest) {
            WildNestBlock block = (WildNestBlock) state.getBlock();
            if (state.getValue(WildNestBlock.RECOVERING)) {
                player.sendOverlayMessage(Component.translatable("message.antcolonies.nest.recovering").withStyle(ChatFormatting.GOLD));
                return InteractionResult.FAIL;
            }
            Identifier species = block.species();
            ItemStack caught = level.getRandom().nextDouble() < AntConfig.get(AntConfig.NEST_QUEEN_CHANCE)
                    ? queenTube(Genetics.wildQueen(species, level.getRandom()))
                    : antTube(new AntSample(species, Caste.WORKER, 2 + level.getRandom().nextInt(4)));
            takeEmptyTube(player);
            give(player, caught);
            block.raid(serverLevel, pos, state, player);
            captured(player, context.getItemInHand(), context.getHand(), caught);
            return InteractionResult.SUCCESS;
        }
        TerrariumBlockEntity be = (TerrariumBlockEntity) level.getBlockEntity(pos);
        Colony colony = be.colony();
        ItemStack caught;
        if (player.isShiftKeyDown()) {
            int items = (int) Math.min(16, colony.eggs() / 4);
            if (items <= 0) {
                player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_eggs").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            colony.addEggs(-items * 4.0);
            caught = new ItemStack(ModItems.ANT_EGG.get(), items);
            caught.set(ModDataComponents.SPECIES.get(), colony.species());
            give(player, caught);
        } else {
            int count = (int) Math.min(AntSample.PINCH, colony.workers() - colony.maintenanceWorkers());
            if (count <= 0) {
                player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_workers").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            colony.addWorkers(-count);
            caught = antTube(new AntSample(colony.species(), Caste.WORKER, count));
            takeEmptyTube(player);
            give(player, caught);
        }
        be.setChanged();
        be.syncNow();
        captured(player, context.getItemInHand(), context.getHand(), caught);
        return InteractionResult.SUCCESS;
    }

    private static void captured(Player player, ItemStack tweezers, InteractionHand hand, ItemStack caught) {
        player.level().playSound(null, player.blockPosition(), ModSounds.CAPTURE.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
        player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.caught", caught.getHoverName()).withStyle(ChatFormatting.GREEN));
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

    /**
     * The Terrarium's take button: up to a tube's worth ({@link AntSample#MAX}) of the colony's workers, into an empty Test Tube from the player's inventory. Using the tube on another Terrarium
     * adds them to that colony.
     */
    public static boolean takeFromColony(Player player, TerrariumBlockEntity terrarium) {
        if (!hasEmptyTube(player)) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_tube").withStyle(ChatFormatting.RED));
            return false;
        }
        Colony colony = terrarium.colony();
        int count = (int) Math.min(AntSample.MAX, colony.workers());
        if (count <= 0) {
            player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.no_workers").withStyle(ChatFormatting.RED));
            return false;
        }
        colony.addWorkers(-count);
        ItemStack caught = antTube(new AntSample(colony.species(), Caste.WORKER, count));
        takeEmptyTube(player);
        give(player, caught);
        player.level().playSound(null, player.blockPosition(), ModSounds.CAPTURE.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
        player.sendOverlayMessage(Component.translatable("message.antcolonies.tweezers.caught", caught.getHoverName()).withStyle(ChatFormatting.GREEN));
        terrarium.setChanged();
        terrarium.syncNow();
        return true;
    }

    private static boolean hasEmptyTube(Player player) {
        return player.isCreative() || player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.TEST_TUBE.get())) >= 0;
    }

    private static void takeEmptyTube(Player player) {
        if (player.isCreative()) {
            return;
        }
        Inventory inventory = player.getInventory();
        int slot = inventory.findSlotMatchingItem(new ItemStack(ModItems.TEST_TUBE.get()));
        if (slot >= 0) {
            inventory.removeItem(slot, 1);
        }
    }

    /** Caught workers top up a tube of the same ants first. */
    private static void give(Player player, ItemStack caught) {
        AntSample sample = caught.get(ModDataComponents.ANTS.get());
        if (sample != null) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack other = inventory.getItem(slot);
                AntSample there = other.get(ModDataComponents.ANTS.get());
                if (there != null && there.species().equals(sample.species()) && there.caste() == sample.caste()
                        && there.count() + sample.count() <= AntSample.MAX) {
                    other.set(ModDataComponents.ANTS.get(), there.withCount(there.count() + sample.count()));
                    // The tube taken for the catch isn't needed after all.
                    if (!player.isCreative()) {
                        inventory.placeItemBackInInventory(new ItemStack(ModItems.TEST_TUBE.get()), net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                    return;
                }
            }
        }
        player.getInventory().placeItemBackInInventory(caught, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.antcolonies.tweezers").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.antcolonies.tweezers.terrarium").withStyle(ChatFormatting.DARK_GRAY));
    }
}
