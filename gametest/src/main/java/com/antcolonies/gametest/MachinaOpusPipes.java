package com.antcolonies.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * MachinaOpus's pipes and cables, for the tests that check they work with Ant Colonies' blocks. Reached by reflection:
 * Ant Colonies doesn't depend on MachinaOpus, and CI builds without it, so nothing here may name its classes. Only
 * called when MachinaOpus is loaded. Mirrors MachinaOpus's own test helpers ({@code placePipe}, {@code setMode}).
 */
final class MachinaOpusPipes {
    private static final String PACKAGE = "com.machinaopus.pipe.";

    private MachinaOpusPipes() {}

    /** Puts a basic pipe of {@code type} ("ITEM", "ENERGY") at {@code pos}, placing the pipe bundle block first. */
    static void place(GameTestHelper helper, BlockPos pos, String type) {
        try {
            if (!isBundle(helper, pos)) {
                helper.setBlock(pos, BuiltInRegistries.BLOCK.getValue(Identifier.parse("machinaopus:pipes_bundle")));
            }
            Object bundle = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            Class<?> types = Class.forName(PACKAGE + "PipeType");
            Class<?> tiers = Class.forName(PACKAGE + "PipeTier");
            bundle.getClass().getMethod("add", types, tiers).invoke(bundle, constant(types, type), constant(tiers, "BASIC"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MachinaOpus's pipe API changed", e);
        }
    }

    /** Sets how the pipe of {@code type} at {@code pos} treats the block on {@code side}: "NEUTRAL", "INPUT", "OUTPUT" (pull). */
    static void setMode(GameTestHelper helper, BlockPos pos, String type, Direction side, String mode) {
        try {
            Object bundle = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            Class<?> types = Class.forName(PACKAGE + "PipeType");
            Object pipe = bundle.getClass().getMethod("getPipe", types).invoke(bundle, constant(types, type));
            Class<?> modes = Class.forName(PACKAGE + "ConnectionMode");
            Class.forName(PACKAGE + "Pipe").getMethod("setMode", Direction.class, modes).invoke(pipe, side, constant(modes, mode));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MachinaOpus's pipe API changed", e);
        }
    }

    private static boolean isBundle(GameTestHelper helper, BlockPos pos) {
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        return entity != null && entity.getClass().getSimpleName().equals("PipesBundleBlockEntity");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object constant(Class<?> enumClass, String name) {
        return Enum.valueOf((Class) enumClass, name);
    }
}
