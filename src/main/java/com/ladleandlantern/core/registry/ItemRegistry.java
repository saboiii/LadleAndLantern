package com.ladleandlantern.core.registry;

import com.ladleandlantern.core.item.MenuCardItem;
import com.ladleandlantern.core.item.RecordItem;
import com.ladleandlantern.core.item.ThankYouNoteItem;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.item.DigitalClockItem;
import com.ladleandlantern.core.item.NotebookItem;
import com.ladleandlantern.core.item.PlannerItem;
import com.ladleandlantern.core.item.ToGoBagItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ItemRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LadleAndLantern.MODID);

    public static final DeferredItem<BlockItem> CASHIER = ITEMS.registerSimpleBlockItem(BlockRegistry.CASHIER);
    public static final DeferredItem<BlockItem> TRAY = ITEMS.registerSimpleBlockItem(BlockRegistry.TRAY);
    public static final DeferredItem<PlannerItem> PLANNER = ITEMS.register("planner",
            () -> new PlannerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<NotebookItem> ORDER_NOTEBOOK = ITEMS.register("order_notebook",
            () -> new NotebookItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ToGoBagItem> TO_GO_BAG = ITEMS.register("to_go_bag",
            () -> new ToGoBagItem(new Item.Properties().stacksTo(16).component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)));
    public static final DeferredItem<BlockItem> BLUE_RIBBON = ITEMS.register("blue_ribbon",
            () -> new BlockItem(BlockRegistry.BLUE_RIBBON.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<BlockItem> SPECIALS_BOARD = ITEMS.registerSimpleBlockItem(BlockRegistry.SPECIALS_BOARD);
    public static final DeferredItem<BlockItem> BAG_RACK = ITEMS.registerSimpleBlockItem(BlockRegistry.BAG_RACK);
    public static final DeferredItem<Item> DOLLAR_BILL = ITEMS.register("dollar_bill", () -> new Item(new Item.Properties()));

    public static final DeferredItem<DigitalClockItem> DIGITAL_CLOCK = ITEMS.register("digital_clock",
            () -> new DigitalClockItem(BlockRegistry.DIGITAL_CLOCK.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> TIP_JAR = ITEMS.registerSimpleBlockItem(BlockRegistry.TIP_JAR);
    public static final DeferredItem<BlockItem> UMBRELLA_STAND = ITEMS.registerSimpleBlockItem(BlockRegistry.UMBRELLA_STAND);
    public static final DeferredItem<BlockItem> COAT_RACK = ITEMS.registerSimpleBlockItem(BlockRegistry.COAT_RACK);
    public static final DeferredItem<BlockItem> WATER_STATION = ITEMS.registerSimpleBlockItem(BlockRegistry.WATER_STATION);
    public static final DeferredItem<BlockItem> CORKBOARD = ITEMS.registerSimpleBlockItem(BlockRegistry.CORKBOARD);
    public static final DeferredItem<BlockItem> CAT_BED = ITEMS.registerSimpleBlockItem(BlockRegistry.CAT_BED);
    public static final DeferredItem<BlockItem> GRAMOPHONE = ITEMS.registerSimpleBlockItem(BlockRegistry.GRAMOPHONE);
    public static final DeferredItem<BlockItem> MENU_STAND = ITEMS.registerSimpleBlockItem(BlockRegistry.MENU_STAND);
    public static final DeferredItem<com.ladleandlantern.core.item.MenuCardItem> MENU_CARD = ITEMS.register("menu_card",
            () -> new com.ladleandlantern.core.item.MenuCardItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<com.ladleandlantern.core.item.ThankYouNoteItem> THANK_YOU_NOTE = ITEMS.register("thank_you_note",
            () -> new com.ladleandlantern.core.item.ThankYouNoteItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.ladleandlantern.core.item.RecordItem> RECORD_LANTERN_GLOW = ITEMS.register("record_lantern_glow",
            () -> new com.ladleandlantern.core.item.RecordItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), 0, "Lantern Glow"));
    public static final DeferredItem<com.ladleandlantern.core.item.RecordItem> RECORD_MORNING_BREAD = ITEMS.register("record_morning_bread",
            () -> new com.ladleandlantern.core.item.RecordItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), 1, "Fresh Bread Morning"));
    public static final DeferredItem<com.ladleandlantern.core.item.RecordItem> RECORD_SNOWY_WINDOWSILL = ITEMS.register("record_snowy_windowsill",
            () -> new com.ladleandlantern.core.item.RecordItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), 2, "Snow on the Windowsill"));

    private ItemRegistry() {}
}
