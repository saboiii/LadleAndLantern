package com.ladleandlantern.client.integration;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;

final class EmiBridge {
    private EmiBridge() {}

    static void show(ItemStack stack) {
        EmiApi.displayRecipes(EmiStack.of(stack));
    }
}
