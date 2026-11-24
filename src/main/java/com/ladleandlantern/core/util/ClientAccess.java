package com.ladleandlantern.core.util;

import com.ladleandlantern.core.block.GramophoneBlock;
import com.ladleandlantern.core.item.PlannerMode;
import net.minecraft.world.item.ItemStack;

public interface ClientAccess {
    default void openBook(ItemStack stack) {}

    default void openPlanner(PlannerMode current, boolean linked) {}

    default void tickGramophone(GramophoneBlock.GramophoneEntity gramophone) {}

    static ClientAccess get() {
        return Holder.instance;
    }

    static void set(ClientAccess access) {
        Holder.instance = access;
    }

    final class Holder {
        private static ClientAccess instance = new ClientAccess() {};

        private Holder() {}
    }
}
