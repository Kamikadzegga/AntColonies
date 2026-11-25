package com.antcolonies.companion.menu;

import com.antcolonies.companion.CompanionData;
import com.antcolonies.companion.Companions;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;

/** Opens a companion's two screens. The entity's id goes along, so the client knows which ant it looks at. */
public final class CompanionMenus {
    public static void open(ServerPlayer player, Mob mob) {
        player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new CompanionMenu(id, inventory, mob), title(mob)),
                buffer -> buffer.writeVarInt(mob.getId()));
    }

    public static void openCart(ServerPlayer player, Mob mob) {
        player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new CompanionCartMenu(id, inventory, mob), title(mob)),
                buffer -> buffer.writeVarInt(mob.getId()));
    }

    private static Component title(Mob mob) {
        CompanionData data = Companions.get(mob);
        return mob.hasCustomName() || data == null ? mob.getDisplayName() : Companions.guardName(data);
    }

    private CompanionMenus() {}
}
