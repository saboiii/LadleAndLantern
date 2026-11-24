package com.ladleandlantern.client;

import com.ladleandlantern.client.screen.ClosingCardScreen;
import com.ladleandlantern.client.screen.EmployeeCardScreen;
import com.ladleandlantern.client.screen.ReceiptScreen;

import com.ladleandlantern.client.screen.CashierScreen;
import com.ladleandlantern.client.screen.NotebookScreen;
import com.ladleandlantern.core.network.CalendarPayload;
import com.ladleandlantern.core.network.OpenScreenPayload;
import net.minecraft.client.Minecraft;

public final class ClientPayloads {
    public static long dayOffset;
    public static String season = "";

    private ClientPayloads() {}

    public static void openScreen(OpenScreenPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.screen() == OpenScreenPayload.CASHIER) {
            if (mc.screen instanceof CashierScreen open) open.update(payload.data());
            else if (mc.screen == null) mc.setScreen(new CashierScreen(payload.data()));
        } else if (payload.screen() == OpenScreenPayload.EMPLOYEE) {
            mc.setScreen(new com.ladleandlantern.client.screen.EmployeeCardScreen(payload.data()));
        } else if (payload.screen() == OpenScreenPayload.CLOSING_CARD) {
            if (mc.screen != null) pendingCard = payload.data();
            else mc.setScreen(new com.ladleandlantern.client.screen.ClosingCardScreen(payload.data()));
        } else if (payload.screen() == OpenScreenPayload.RECEIPT) {
            mc.setScreen(new com.ladleandlantern.client.screen.ReceiptScreen(payload.data()));
        } else {
            mc.setScreen(new NotebookScreen(payload.data()));
        }
    }

    private static net.minecraft.nbt.CompoundTag pendingCard;

    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pendingCard == null || mc.screen != null || mc.player == null) return;
        net.minecraft.nbt.CompoundTag card = pendingCard;
        pendingCard = null;
        mc.setScreen(new com.ladleandlantern.client.screen.ClosingCardScreen(card));
    }

    public static void calendar(CalendarPayload payload) {
        dayOffset = payload.dayOffset();
        season = payload.season();
    }
}
