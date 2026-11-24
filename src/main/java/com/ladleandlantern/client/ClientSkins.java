package com.ladleandlantern.client;

import com.ladleandlantern.LadleAndLantern;

import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientSkins {
    private static final Map<String, PlayerSkin> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();

    private ClientSkins() {}

    public static PlayerSkin get(String key) {
        PlayerSkin cached = CACHE.get(key);
        if (cached != null) return cached;
        if (key.startsWith("staff:")) {
            String name = key.substring(6);
            boolean slim = name.startsWith("waitress") || name.equals("waiter_1");
            PlayerSkin skin = new PlayerSkin(com.ladleandlantern.LadleAndLantern.id("textures/entity/staff/" + name + ".png"), null, null, null,
                    slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, true);
            CACHE.put(key, skin);
            return skin;
        }
        PlayerSkin fallback = DefaultPlayerSkin.get(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        if (!key.startsWith("user:")) {
            CACHE.put(key, fallback);
        } else if (REQUESTED.add(key)) {
            try {
                new ResolvableProfile(Optional.of(key.substring(5)), Optional.empty(), new PropertyMap()).resolve()
                        .thenCompose(p -> Minecraft.getInstance().getSkinManager().getOrLoad(p.gameProfile()))
                        .whenComplete((skin, error) -> CACHE.put(key, skin != null && error == null ? skin : fallback));
            } catch (RuntimeException e) {
                CACHE.put(key, fallback);
            }
        }
        return fallback;
    }
}
