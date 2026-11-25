package com.antcolonies.companion.item;

import com.antcolonies.companion.CompanionRoster;
import com.antcolonies.content.item.DescribedItem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** The scent ants rally to: on an ant or a Formicarium it recruits; used in the air it shows a player their Guard Ants. */
public class RallyPheromoneItem extends DescribedItem {
    public RallyPheromoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            CompanionRoster.of(serverPlayer.level().getServer()).show(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
