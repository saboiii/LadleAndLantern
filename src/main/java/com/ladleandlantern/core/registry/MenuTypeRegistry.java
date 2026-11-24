package com.ladleandlantern.core.registry;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.menu.StorageMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MenuTypeRegistry {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LadleAndLantern.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<StorageMenu>> STORAGE =
            MENUS.register("storage", () -> IMenuTypeExtension.create(StorageMenu::client));

    private MenuTypeRegistry() {}
}
