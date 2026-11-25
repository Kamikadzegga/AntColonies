package com.antcolonies.core.registry;

import java.util.List;
import java.util.function.Supplier;

import com.antcolonies.AntColonies;
import com.antcolonies.companion.CompanionCart;
import com.antcolonies.companion.CompanionData;

import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AntColonies.MODID);

    /** On a player: the species whose wild nests they have caught from. The first catch from each kind of nest is always a Queen. */
    public static final Supplier<AttachmentType<List<Identifier>>> NESTS_CAUGHT = ATTACHMENT_TYPES.register("nests_caught",
            () -> AttachmentType.<List<Identifier>>builder(() -> List.of()).serialize(Identifier.CODEC.listOf().fieldOf("species")).copyOnDeath()
                    .build());

    public static final Identifier COMPANION_ID = AntColonies.id("companion");
    /** On an ant that was recruited: all that makes it this one companion. Every client that sees it knows it too. */
    public static final Supplier<AttachmentType<CompanionData>> COMPANION = ATTACHMENT_TYPES.register(COMPANION_ID.getPath(),
            () -> AttachmentType.<CompanionData>builder(() -> {
                throw new IllegalStateException("A mob only becomes a companion by being recruited");
            }).serialize(CompanionData.MAP_CODEC).sync(CompanionData.STREAM_CODEC).build());

    /** On a Cart Ant: what its cart holds. */
    public static final Supplier<AttachmentType<CompanionCart>> COMPANION_CART = ATTACHMENT_TYPES.register("companion_cart",
            () -> AttachmentType.serializable(CompanionCart::new).build());

    private ModAttachments() {}
}
