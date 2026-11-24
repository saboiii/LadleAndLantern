package com.ladleandlantern.core.network;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.item.PlannerItem;
import com.ladleandlantern.core.item.PlannerMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlannerModePayload(int mode) implements CustomPacketPayload {
    public static final Type<PlannerModePayload> TYPE = new Type<>(LadleAndLantern.id("planner_mode"));
    public static final StreamCodec<ByteBuf, PlannerModePayload> CODEC =
            ByteBufCodecs.VAR_INT.map(PlannerModePayload::new, PlannerModePayload::mode);

    public static void handle(PlannerModePayload payload, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) PlannerItem.setMode(player, PlannerMode.byId(payload.mode()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
