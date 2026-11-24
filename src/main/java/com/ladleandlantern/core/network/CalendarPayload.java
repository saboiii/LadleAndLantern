package com.ladleandlantern.core.network;

import com.ladleandlantern.LadleAndLantern;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CalendarPayload(long dayOffset, String season) implements CustomPacketPayload {
    public static final Type<CalendarPayload> TYPE = new Type<>(LadleAndLantern.id("calendar"));
    public static final StreamCodec<ByteBuf, CalendarPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CalendarPayload::dayOffset,
            ByteBufCodecs.STRING_UTF8, CalendarPayload::season,
            CalendarPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
