package com.ladleandlantern.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class RecordItem extends Item {
    public final int track;
    private final String title;

    public RecordItem(Properties props, int track, String title) {
        super(props);
        this.track = track;
        this.title = title;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(title).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Plays on a gramophone").withStyle(ChatFormatting.DARK_GRAY));
    }
}
