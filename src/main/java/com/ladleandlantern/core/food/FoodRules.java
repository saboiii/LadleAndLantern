package com.ladleandlantern.core.food;

import com.ladleandlantern.core.restaurant.Course;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

public final class FoodRules {
    private static final List<String> SWEET = List.of("cake", "pie", "cookie", "tart", "pudding", "ice_cream", "chocolate",
            "muffin", "donut", "doughnut", "jelly", "candy", "sweet", "honey", "brownie", "cupcake", "macaron", "waffle",
            "pancake", "crepe", "custard", "sundae", "popsicle", "fudge", "mochi", "eclair", "croissant", "strudel");
    private static final List<String> HEARTY = List.of("stew", "soup", "sandwich", "burger", "pasta", "roast", "curry",
            "rice", "steak", "pizza", "noodle", "wrap", "platter", "feast", "dinner", "meal", "lasagna", "risotto",
            "taco", "burrito", "kebab", "skewer", "dumpling", "ramen", "biryani", "omelette", "chop", "fillet");

    private FoodRules() {}

    public static boolean isFood(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.FOOD) || stack.getUseAnimation() == UseAnim.DRINK
                || stack.is(Tags.Items.FOODS) || stack.is(Tags.Items.DRINKS) || stack.is(Tags.Items.FOODS_EDIBLE_WHEN_PLACED);
    }

    public static boolean isFood(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item != Items.AIR && isFood(item.getDefaultInstance());
    }

    public static Course classify(ResourceLocation id) {
        ItemStack stack = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
        String path = id.getPath();
        if (stack.getUseAnimation() == UseAnim.DRINK || stack.is(Tags.Items.DRINKS)) return Course.DRINK;
        if (stack.is(Tags.Items.FOODS_CANDY) || stack.is(Tags.Items.FOODS_COOKIE) || stack.is(Tags.Items.FOODS_PIE)
                || SWEET.stream().anyMatch(path::contains)) return Course.DESSERT;
        FoodProperties food = stack.get(DataComponents.FOOD);
        if ((food != null && food.nutrition() >= 6) || stack.is(Tags.Items.FOODS_COOKED_MEAT)
                || stack.is(Tags.Items.FOODS_COOKED_FISH) || stack.is(Tags.Items.FOODS_SOUP)
                || HEARTY.stream().anyMatch(path::contains)) return Course.MAIN;
        return Course.SIDE;
    }

    private static final List<String> WARMING = List.of("soup", "stew", "broth", "chowder", "ramen", "noodle", "porridge",
            "tea", "coffee", "cocoa", "hot_chocolate", "latte", "cider", "mulled", "congee", "curry", "chili", "pho", "gumbo");

    public static boolean isWarming(ResourceLocation id) {
        String path = id.getPath();
        return WARMING.stream().anyMatch(path::contains) || stackOf(id).is(Tags.Items.FOODS_SOUP);
    }

    private static final java.util.Set<String> ALCOHOL = java.util.Set.of("beer", "ale", "lager", "stout", "wine", "mead", "cider",
            "rum", "vodka", "whisky", "whiskey", "sake", "soju", "gin", "brandy", "cocktail", "liquor", "liqueur", "champagne",
            "spirit", "spirits", "tequila", "baijiu", "grog", "punch", "sangria", "mojito", "margarita", "alcohol", "alcoholic");
    private static final java.util.Set<String> NOT_ALCOHOL = java.util.Set.of("root_beer", "ginger_ale", "ginger_beer", "butterbeer");

    public static boolean isAlcoholic(ResourceLocation id) {
        String path = id.getPath();
        for (String safe : NOT_ALCOHOL) if (path.contains(safe)) return false;
        for (String word : path.split("[_/]")) if (ALCOHOL.contains(word)) return true;
        return false;
    }

    public static List<ResourceLocation> cakes() {
        if (cakes != null) return cakes;
        List<ResourceLocation> out = new java.util.ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String path = id.getPath();
            if (path.contains("cake") && !path.contains("pancake") && !path.contains("fishcake") && !path.contains("rice_cake")
                    && !path.contains("slice") && !path.contains("batter") && !path.contains("stand")
                    && (isFood(id) || item instanceof net.minecraft.world.item.BlockItem block && block.getBlock() instanceof net.minecraft.world.level.block.CakeBlock)) out.add(id);
        }
        if (out.isEmpty()) out.add(ResourceLocation.withDefaultNamespace("cake"));
        cakes = List.copyOf(out);
        return cakes;
    }

    private static List<ResourceLocation> cakes;

    public static ItemStack stackOf(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    public static String nameOf(ResourceLocation id) {
        return stackOf(id).getHoverName().getString();
    }
}
