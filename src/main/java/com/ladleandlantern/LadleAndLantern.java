package com.ladleandlantern;

import com.ladleandlantern.core.config.Config;

import com.ladleandlantern.client.LadleAndLanternClient;
import com.ladleandlantern.core.event.GameEvents;
import com.ladleandlantern.core.network.ModNetwork;
import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import com.ladleandlantern.core.registry.BlockRegistry;
import com.ladleandlantern.core.registry.TabRegistry;
import com.ladleandlantern.core.registry.EntityTypeRegistry;
import com.ladleandlantern.core.registry.ItemRegistry;
import com.ladleandlantern.core.registry.MenuTypeRegistry;
import com.ladleandlantern.core.registry.SoundEventRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LadleAndLantern.MODID)
public final class LadleAndLantern {
    public static final String MODID = "ladle_and_lantern";
    public static final Logger LOGGER = LoggerFactory.getLogger("Ladle & Lantern");

    public LadleAndLantern(IEventBus modBus, ModContainer container) {
        BlockRegistry.BLOCKS.register(modBus);
        ItemRegistry.ITEMS.register(modBus);
        BlockEntityTypeRegistry.TYPES.register(modBus);
        EntityTypeRegistry.TYPES.register(modBus);
        TabRegistry.TABS.register(modBus);
        SoundEventRegistry.SOUNDS.register(modBus);
        MenuTypeRegistry.MENUS.register(modBus);

        modBus.addListener(EntityTypeRegistry::registerAttributes);
        modBus.addListener(ModNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        GameEvents.register(NeoForge.EVENT_BUS);
        if (FMLEnvironment.dist.isClient()) {
            LadleAndLanternClient.init(modBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
