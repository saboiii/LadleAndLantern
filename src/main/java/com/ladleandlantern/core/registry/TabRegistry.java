package com.ladleandlantern.core.registry;

import com.ladleandlantern.LadleAndLantern;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class TabRegistry {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LadleAndLantern.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ladle_and_lantern"))
            .icon(() -> ItemRegistry.CASHIER.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(ItemRegistry.CASHIER.get());
                out.accept(ItemRegistry.TRAY.get());
                out.accept(ItemRegistry.PLANNER.get());
                out.accept(ItemRegistry.ORDER_NOTEBOOK.get());
                out.accept(ItemRegistry.TO_GO_BAG.get());
                out.accept(ItemRegistry.BAG_RACK.get());
                out.accept(ItemRegistry.SPECIALS_BOARD.get());
                out.accept(ItemRegistry.DIGITAL_CLOCK.get());
                out.accept(ItemRegistry.DOLLAR_BILL.get());
                out.accept(ItemRegistry.BLUE_RIBBON.get());
                out.accept(ItemRegistry.MENU_CARD.get());
                out.accept(ItemRegistry.MENU_STAND.get());
                out.accept(ItemRegistry.TIP_JAR.get());
                out.accept(ItemRegistry.UMBRELLA_STAND.get());
                out.accept(ItemRegistry.COAT_RACK.get());
                out.accept(ItemRegistry.WATER_STATION.get());
                out.accept(ItemRegistry.CORKBOARD.get());
                out.accept(ItemRegistry.THANK_YOU_NOTE.get());
                out.accept(ItemRegistry.CAT_BED.get());
                out.accept(ItemRegistry.GRAMOPHONE.get());
                out.accept(ItemRegistry.RECORD_LANTERN_GLOW.get());
                out.accept(ItemRegistry.RECORD_MORNING_BREAD.get());
                out.accept(ItemRegistry.RECORD_SNOWY_WINDOWSILL.get());
            })
            .build());

    private TabRegistry() {}
}
