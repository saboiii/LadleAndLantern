package com.ladleandlantern.core.item;

import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.ladleandlantern.core.util.ClientAccess;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.restaurant.Course;
import com.ladleandlantern.core.restaurant.Restaurant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MenuCardItem extends Item {
    private static final int LINES_PER_PAGE = 12;

    public MenuCardItem(Properties props) {
        super(props);
    }

    public static boolean isWritten(ItemStack stack) {
        return stack.getItem() instanceof MenuCardItem && stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
    }

    public static void write(ItemStack stack, Restaurant r, long day) {
        Map<ResourceLocation, Course> dishes = r.available(day, Config.SEASON_LENGTH.get());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(r.name).withStyle(ChatFormatting.BOLD));
        lines.add(Component.literal(r.cuisine).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        for (Course course : Course.values()) {
            List<ResourceLocation> ids = dishes.entrySet().stream().filter(e -> e.getValue() == course).map(Map.Entry::getKey).toList();
            if (ids.isEmpty()) continue;
            lines.add(Component.empty());
            lines.add(Component.literal(course.label).withStyle(ChatFormatting.DARK_RED, ChatFormatting.UNDERLINE));
            boolean special = false;
            for (ResourceLocation id : ids) {
                MutableComponent line = Component.literal(" " + FoodRules.nameOf(id));
                if (id.equals(r.special)) {
                    line.append(Component.literal(" *").withStyle(ChatFormatting.GOLD));
                    special = true;
                }
                lines.add(line);
            }
            if (special) lines.add(Component.literal(" * today's special").withStyle(ChatFormatting.GOLD));
        }
        List<Filterable<Component>> pages = new ArrayList<>();
        MutableComponent page = Component.empty();
        int count = 0;
        for (Component line : lines) {
            if (count == LINES_PER_PAGE) {
                pages.add(Filterable.passThrough(page));
                page = Component.empty();
                count = 0;
            }
            if (count > 0) page.append("\n");
            page.append(line);
            count++;
        }
        pages.add(Filterable.passThrough(page));
        String title = (r.name + " Menu").length() > 32 ? "Menu" : r.name + " Menu";
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), r.cuisine, 0, pages, true));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isWritten(stack)) {
            if (!level.isClientSide) player.displayClientMessage(Component.literal("A blank menu card. Right-click a cash register with it to write today's menu."), true);
            return InteractionResultHolder.pass(stack);
        }

        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            var store = com.ladleandlantern.core.restaurant.RestaurantStore.get(server.getServer());
            store.at(level.dimension(), player.blockPosition()).ifPresent(r -> write(stack, r, store.day()));
        }
        if (level.isClientSide) com.ladleandlantern.core.util.ClientAccess.get().openBook(stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content == null ? super.getName(stack) : Component.literal(content.title().raw());
    }
}
