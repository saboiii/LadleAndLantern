package com.ladleandlantern.client;

import com.ladleandlantern.core.util.ClientAccess;

import com.ladleandlantern.core.block.GramophoneBlock;
import com.ladleandlantern.client.screen.PlannerScreen;
import com.ladleandlantern.core.item.RecordItem;
import com.ladleandlantern.core.item.PlannerMode;
import com.ladleandlantern.core.registry.SoundEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

public final class ClientHooks implements com.ladleandlantern.core.util.ClientAccess {
    @Override
    public void openPlanner(PlannerMode current, boolean linked) {
        Minecraft.getInstance().setScreen(new PlannerScreen(current, linked));
    }

    @Override
    public void openBook(ItemStack stack) {
        Minecraft.getInstance().setScreen(new BookViewScreen(BookViewScreen.BookAccess.fromItem(stack)));
    }

    @Override
    public void tickGramophone(GramophoneBlock.GramophoneEntity be) {
        Loop current = be.sound instanceof Loop l ? l : null;
        int track = be.disc().getItem() instanceof RecordItem disc ? disc.track : -1;
        if (current != null && !current.isStopped() && current.track == track) return;
        if (current != null) current.end();
        be.sound = null;
        if (track < 0) return;
        Loop loop = new Loop(be, track);
        be.sound = loop;
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final GramophoneBlock.GramophoneEntity be;
        private final int track;

        Loop(GramophoneBlock.GramophoneEntity be, int track) {
            super(SoundEventRegistry.RECORDS.get(track).get(), SoundSource.RECORDS, RandomSource.create());
            this.be = be;
            this.track = track;
            looping = true;
            delay = 0;
            volume = 0.7f;
            attenuation = SoundInstance.Attenuation.LINEAR;
            x = be.getBlockPos().getX() + 0.5;
            y = be.getBlockPos().getY() + 0.5;
            z = be.getBlockPos().getZ() + 0.5;
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            boolean same = be.disc().getItem() instanceof RecordItem disc && disc.track == track;
            if (be.isRemoved() || !same) stop();
        }
    }
}
