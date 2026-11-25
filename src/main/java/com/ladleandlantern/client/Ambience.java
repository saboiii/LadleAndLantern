package com.ladleandlantern.client;

import com.ladleandlantern.core.entity.CustomerEntity;
import com.ladleandlantern.core.registry.SoundEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class Ambience {
    private static final RandomSource RANDOM = RandomSource.create();
    private static Loop loop;
    private static int crowd;
    private static int retryAt, ticks;

    private Ambience() {}

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.player.tickCount % 20 == 0) {
            crowd = mc.level.getEntitiesOfClass(CustomerEntity.class, mc.player.getBoundingBox().inflate(20, 8, 20)).size();
        }
        float target = volumeFor(crowd);

        boolean playing = loop != null && !loop.isStopped() && mc.getSoundManager().isActive(loop);
        ticks++;
        if (target > 0 && !playing && ticks >= retryAt) {
            retryAt = ticks + 100;
            loop = new Loop(SoundEventRegistry.AMBIENCE_ROOM.get());
            mc.getSoundManager().play(loop);
        }
        if (loop != null) loop.target = target;

        if (crowd >= 4 && RANDOM.nextInt(20 * 150) == 0) {
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEventRegistry.LAUGHTER.get().getLocation(), SoundSource.AMBIENT,
                    0.18f + crowd * 0.01f, 0.95f + RANDOM.nextFloat() * 0.1f, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        }
    }

    private static float volumeFor(int customers) {
        if (customers == 0) return 0;
        if (customers <= 2) return 0.12f;
        if (customers <= 5) return 0.22f;
        if (customers <= 9) return 0.32f;
        return 0.42f;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        float target;

        Loop(SoundEvent sound) {
            super(sound, SoundSource.AMBIENT, RANDOM);
            looping = true;
            delay = 0;
            volume = 0.01f;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            volume += (target - volume) * 0.02f;
            if (target == 0 && volume < 0.005f) stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
