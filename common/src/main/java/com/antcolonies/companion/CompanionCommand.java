package com.antcolonies.companion;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** What its owner told a companion to do. */
public enum CompanionCommand implements StringRepresentable {
    FOLLOW("follow"),
    STAY("stay"),
    GUARD("guard");

    public static final Codec<CompanionCommand> CODEC = StringRepresentable.fromEnum(CompanionCommand::values);

    private final String name;

    CompanionCommand(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public CompanionCommand next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Component label() {
        return Component.translatable("companion.antcolonies.command." + name);
    }
}
