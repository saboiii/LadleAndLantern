package com.ladleandlantern.client;

import com.ladleandlantern.client.overlay.CorkboardHud;
import com.ladleandlantern.client.renderer.CorkboardRenderer;
import com.ladleandlantern.client.renderer.DigitalClockRenderer;
import com.ladleandlantern.client.renderer.FoodOnBlockRenderer;
import com.ladleandlantern.client.renderer.NpcRenderer;
import com.ladleandlantern.client.renderer.RibbonRenderer;
import com.ladleandlantern.client.renderer.SpecialsBoardRenderer;
import com.ladleandlantern.core.util.ClientAccess;

import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import com.ladleandlantern.client.screen.StorageScreen;
import com.ladleandlantern.core.registry.EntityTypeRegistry;
import com.ladleandlantern.core.registry.MenuTypeRegistry;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class LadleAndLanternClient {
    private LadleAndLanternClient() {}

    public static void init(IEventBus modBus) {
        com.ladleandlantern.core.util.ClientAccess.set(new ClientHooks());
        modBus.addListener(LadleAndLanternClient::registerRenderers);
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(MenuTypeRegistry.STORAGE.get(), StorageScreen::new));
        modBus.addListener(CorkboardHud::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Ambience::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientPayloads::tick);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityTypeRegistry.CUSTOMER.get(), NpcRenderer::new);
        event.registerEntityRenderer(EntityTypeRegistry.STAFF.get(), NpcRenderer::new);
        event.registerEntityRenderer(EntityTypeRegistry.SEAT.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityTypeRegistry.SERVED_PLATE.get(), FoodOnBlockRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityTypeRegistry.TRAY.get(), FoodOnBlockRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityTypeRegistry.DIGITAL_CLOCK.get(), DigitalClockRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityTypeRegistry.RIBBON.get(), RibbonRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityTypeRegistry.SPECIALS_BOARD.get(), SpecialsBoardRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityTypeRegistry.CORKBOARD.get(), CorkboardRenderer::new);
        event.registerEntityRenderer(EntityTypeRegistry.CAT.get(), net.minecraft.client.renderer.entity.CatRenderer::new);
    }
}
