package com.ladleandlantern.client;

import com.ladleandlantern.LadleAndLantern;

import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
        if (key.startsWith("local:")) {
            List<PlayerSkin> local = localSkins();
            if (!local.isEmpty()) {
                int n = Math.floorMod(key.substring(6).hashCode(), local.size());
                PlayerSkin skin = local.get(n);
                CACHE.put(key, skin);
                return skin;
            }
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

    private static List<PlayerSkin> local;

    private static synchronized List<PlayerSkin> localSkins() {
        if (local != null) return local;
        local = new java.util.ArrayList<>();
        java.nio.file.Path folder = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ladle_and_lantern").resolve("skins");
        try {
            java.nio.file.Files.createDirectories(folder);
            java.nio.file.Path readme = folder.resolve("README.txt");
            if (!java.nio.file.Files.exists(readme)) {
                java.nio.file.Files.writeString(readme, """
                        Drop Minecraft skin files (.png, 64x64) in this folder and customers will wear them.
                        Good places to find free skins: The Skindex (minecraftskins.com), NameMC and Planet Minecraft.
                        Slim (Alex-style) skins are spotted automatically. Restart the game after adding skins.
                        """);
            }
            try (var files = java.nio.file.Files.list(folder)) {
                for (java.nio.file.Path file : files.filter(f -> f.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")).sorted().toList()) {
                    try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
                        com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read(in);
                        if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                            image.close();
                            continue;
                        }

                        boolean slim = image.getHeight() == 64 && ((image.getPixelRGBA(54, 20) >>> 24) & 0xFF) == 0;
                        net.minecraft.resources.ResourceLocation id = com.ladleandlantern.LadleAndLantern.id("local_skin/" + local.size());
                        Minecraft.getInstance().getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(image));
                        local.add(new PlayerSkin(id, null, null, null, slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, true));
                    } catch (Exception e) {
                        com.ladleandlantern.LadleAndLantern.LOGGER.warn("Skipping skin {}: {}", file.getFileName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            com.ladleandlantern.LadleAndLantern.LOGGER.warn("Couldn't read the customer skins folder: {}", e.getMessage());
        }
        return local;
    }
}
