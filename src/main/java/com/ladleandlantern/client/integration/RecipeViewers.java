package com.ladleandlantern.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class RecipeViewers {
    private RecipeViewers() {}

    public static boolean available() {
        return ModList.get().isLoaded("emi") || ModList.get().isLoaded("jei");
    }

    public static void showRecipes(ItemStack stack) {
        if (ModList.get().isLoaded("emi")) {
            EmiBridge.show(stack);
        } else if (ModList.get().isLoaded("jei") && JeiBridge.show(stack)) {
            return;
        } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("Install EMI or JEI to see how " + stack.getHoverName().getString() + " is made."), false);
        }
    }
}
