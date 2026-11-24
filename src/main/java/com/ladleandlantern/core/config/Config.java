package com.ladleandlantern.core.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> CURRENCY_ITEM = B
            .comment("Item customers pay with. Any item or block id works.")
            .define("payment.currencyItem", "minecraft:diamond");
    public static final ModConfigSpec.IntValue BASE_PAY = B
            .comment("Currency per satisfied customer.")
            .defineInRange("payment.basePay", 1, 0, 64);
    public static final ModConfigSpec.IntValue TIP = B
            .comment("Extra currency for a score of 90 or more.")
            .defineInRange("payment.tip", 1, 0, 64);
    public static final ModConfigSpec.IntValue ANGRY_THRESHOLD = B
            .comment("Scores below this make the customer storm out without paying.")
            .defineInRange("scoring.angryThreshold", 40, 0, 100);

    public static final ModConfigSpec.IntValue REGULAR_PATIENCE = B
            .comment("Seconds a regular customer waits before the time score starts dropping. They give up at twice this.")
            .defineInRange("patience.regularSeconds", 300, 20, 3600);
    public static final ModConfigSpec.IntValue RUSH_PATIENCE = B
            .comment("Same for critics and closers.")
            .defineInRange("patience.criticSeconds", 180, 10, 3600);
    public static final ModConfigSpec.IntValue PER_ITEM_SECONDS = B
            .comment("Extra patience per item in the order, to allow for cooking time.")
            .defineInRange("patience.perItemSeconds", 60, 0, 1200);
    public static final ModConfigSpec.IntValue BASE_PER_DAY = B
            .comment("Customers per shift at rank 1. Each rank adds three more, up to 8 per seat. Lunch and dinner rushes bring groups.")
            .defineInRange("customers.basePerDay", 6, 1, 300);
    public static final ModConfigSpec.IntValue MAX_CUSTOMERS = B
            .comment("Most customers in the restaurant at once. The real cap is 3 + twice your rank, up to this.")
            .defineInRange("customers.maxInside", 16, 1, 60);
    public static final ModConfigSpec.IntValue MAX_QUEUE = B
            .comment("Longest takeout queue per cash register.")
            .defineInRange("customers.maxQueue", 5, 1, 12);
    public static final ModConfigSpec.IntValue HIRE_COST = B
            .comment("Currency to hire a waiter or cashier. Each upgrade costs this times the new level.")
            .defineInRange("staff.hireCost", 64, 0, 10000);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SKINS = B
            .comment("Minecraft usernames whose public skins customers and staff can wear. Empty means vanilla default skins only.")
            .defineListAllowEmpty("customers.skins",
                    List.of("Notch", "jeb_", "Dinnerbone", "Grumm", "LadyAgnes", "kingbdogz", "Marc_IRL", "slicedlime"),
                    () -> "Steve", o -> o instanceof String s && s.matches("[A-Za-z0-9_]{3,16}"));

    public static final ModConfigSpec.BooleanValue DINNER_TALK = B
            .comment("Whether staff chat over dinner after closing. They still eat and go to bed when this is off.")
            .define("staff.dinnerConversations", true);

    public static final ModConfigSpec.IntValue SEASON_LENGTH = B
            .comment("In-game days per season.")
            .defineInRange("calendar.seasonLength", 14, 10, 20);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}

    public static Item currency() {
        ResourceLocation id = ResourceLocation.tryParse(CURRENCY_ITEM.get());
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? Items.DIAMOND : item;
    }
}
