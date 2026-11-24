package com.ladleandlantern.core.network;

import com.ladleandlantern.client.ClientPayloads;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(OpenScreenPayload.TYPE, OpenScreenPayload.CODEC, (payload, ctx) -> ClientPayloads.openScreen(payload));
        registrar.playToClient(CalendarPayload.TYPE, CalendarPayload.CODEC, (payload, ctx) -> ClientPayloads.calendar(payload));
        registrar.playToServer(CashierActionPayload.TYPE, CashierActionPayload.CODEC, CashierActions::handle);
        registrar.playToServer(PlannerModePayload.TYPE, PlannerModePayload.CODEC, PlannerModePayload::handle);
    }
}
