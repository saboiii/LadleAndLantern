package com.ladleandlantern.core.event;

import com.ladleandlantern.core.restaurant.GameCalendar;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.ladleandlantern.core.shift.StaffDinner;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.LadleAndLantern;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class LadleCommands {
    private LadleCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("ladle")
                .then(Commands.literal("currency")
                        .executes(LadleCommands::show)
                        .then(Commands.literal("cash").requires(s -> s.hasPermission(2))
                                .executes(ctx -> set(ctx, LadleAndLantern.MODID + ":dollar_bill")))
                        .then(Commands.literal("diamonds").requires(s -> s.hasPermission(2))
                                .executes(ctx -> set(ctx, "minecraft:diamond")))
                        .then(Commands.argument("item", ItemArgument.item(context)).requires(s -> s.hasPermission(2))
                                .executes(ctx -> set(ctx, BuiltInRegistries.ITEM.getKey(ItemArgument.getItem(ctx, "item").getItem()).toString()))))
                .then(Commands.literal("reply")
                        .then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 31))
                                .executes(ctx -> {
                                    com.ladleandlantern.core.shift.StaffDinner.reply(ctx.getSource().getPlayerOrException(),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n"));
                                    return 1;
                                })))
                .then(seasonCommand())
                .then(Commands.literal("dinner")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("Dinner conversations are " + (Config.DINNER_TALK.get() ? "on" : "off") + "."), false);
                            return 1;
                        })
                        .then(Commands.literal("on").requires(s -> s.hasPermission(2)).executes(ctx -> dinner(ctx, true)))
                        .then(Commands.literal("off").requires(s -> s.hasPermission(2)).executes(ctx -> dinner(ctx, false)))));
    }

    private static int dinner(CommandContext<CommandSourceStack> ctx, boolean on) {
        Config.DINNER_TALK.set(on);
        Config.DINNER_TALK.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Dinner conversations are now " + (on ? "on" : "off") + "."), true);
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> seasonCommand() {
        var root = Commands.literal("season").executes(ctx -> {
            var store = com.ladleandlantern.core.restaurant.RestaurantStore.get(ctx.getSource().getServer());
            int length = Config.SEASON_LENGTH.get();
            long day = store.day();
            ctx.getSource().sendSuccess(() -> Component.literal("It's " + com.ladleandlantern.core.restaurant.GameCalendar.season(day, length).label()
                    + ", day " + com.ladleandlantern.core.restaurant.GameCalendar.dayOfSeason(day, length) + " of " + length + "."), false);
            return 1;
        });
        for (var season : com.ladleandlantern.core.restaurant.GameCalendar.Season.values()) {
            root.then(Commands.literal(season.name().toLowerCase(java.util.Locale.ROOT)).requires(s -> s.hasPermission(2)).executes(ctx -> {
                var store = com.ladleandlantern.core.restaurant.RestaurantStore.get(ctx.getSource().getServer());
                int length = Config.SEASON_LENGTH.get();
                long year = store.day() / (length * 4L);
                store.setDay(year * length * 4L + (long) season.ordinal() * length);
                GameEvents.syncCalendar(ctx.getSource().getLevel(), ctx.getSource().getServer().getPlayerList().getPlayers());
                ctx.getSource().sendSuccess(() -> Component.literal("It's now the first day of " + season.label() + "."), true);
                return 1;
            }));
        }
        return root;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("Customers pay with " + name(Config.currency()) + "."), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, String id) {
        Config.CURRENCY_ITEM.set(id);
        Config.CURRENCY_ITEM.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Customers now pay with " + name(Config.currency()) + "."), true);
        return 1;
    }

    private static String name(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }
}
