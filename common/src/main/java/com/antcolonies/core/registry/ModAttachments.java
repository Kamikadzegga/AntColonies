package com.antcolonies.core.registry;

import java.util.List;
import java.util.function.Supplier;

import com.antcolonies.AntColonies;

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

    private ModAttachments() {}
}
