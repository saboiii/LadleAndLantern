package com.ladleandlantern.core.item;

import com.ladleandlantern.core.registry.ItemRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public class ThankYouNoteItem extends Item {
    private static final List<String> NOTES = List.of(
            "Thank you for the lovely %s. It made my day.",
            "Best %s in town. I'll be back.",
            "The %s tasted just like my gran's. Thank you.",
            "Came in tired, left happy. Thank you for the %s.",
            "Your %s is the reason I walked the long way round today.",
            "I had a rotten week and the %s fixed it. Thank you.",
            "Please never take the %s off the menu.",
            "Thank you for being so kind. And for the %s.");

    public ThankYouNoteItem(Properties props) {
        super(props);
    }

    public static ItemStack goodbye(String name) {
        ItemStack note = new ItemStack(com.ladleandlantern.core.registry.ItemRegistry.THANK_YOU_NOTE.get());
        note.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("\"I'm sorry, but I can't keep working without pay.").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY),
                Component.literal("Thank you for everything. I'll miss the regulars.\"").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY),
                Component.literal("  from " + name).withStyle(ChatFormatting.DARK_AQUA))));
        note.set(DataComponents.CUSTOM_NAME, Component.literal("Goodbye note from " + name).withStyle(s -> s.withItalic(false)));
        return note;
    }

    public static ItemStack write(String customer, String dish, RandomSource random) {
        ItemStack note = new ItemStack(com.ladleandlantern.core.registry.ItemRegistry.THANK_YOU_NOTE.get());
        String text = String.format(NOTES.get(random.nextInt(NOTES.size())), dish);
        note.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("\"" + text + "\"").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY),
                Component.literal("  from " + customer).withStyle(ChatFormatting.DARK_AQUA))));
        note.set(DataComponents.CUSTOM_NAME, Component.literal("Note from " + customer).withStyle(s -> s.withItalic(false)));
        return note;
    }
}
