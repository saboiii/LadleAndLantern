package com.ladleandlantern.core.network;

import com.ladleandlantern.LadleAndLantern;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CashierActionPayload(BlockPos cashier, String action, CompoundTag args) implements CustomPacketPayload {
    public static final Type<CashierActionPayload> TYPE = new Type<>(LadleAndLantern.id("cashier_action"));
    public static final StreamCodec<ByteBuf, CashierActionPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CashierActionPayload::cashier,
            ByteBufCodecs.stringUtf8(32), CashierActionPayload::action,
            ByteBufCodecs.COMPOUND_TAG, CashierActionPayload::args,
            CashierActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
