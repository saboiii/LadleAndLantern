package com.ladleandlantern.core.network;

import com.ladleandlantern.LadleAndLantern;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenScreenPayload(int screen, CompoundTag data) implements CustomPacketPayload {
    public static final int CASHIER = 0;
    public static final int NOTEBOOK = 1;
    public static final int RECEIPT = 2;
    public static final int EMPLOYEE = 3;

    public static final int CLOSING_CARD = 4;

    public static final Type<OpenScreenPayload> TYPE = new Type<>(LadleAndLantern.id("open_screen"));
    public static final StreamCodec<ByteBuf, OpenScreenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenScreenPayload::screen,
            ByteBufCodecs.COMPOUND_TAG, OpenScreenPayload::data,
            OpenScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
