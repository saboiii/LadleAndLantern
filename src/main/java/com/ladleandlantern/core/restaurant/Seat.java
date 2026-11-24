package com.ladleandlantern.core.restaurant;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

public final class Seat {
    public static final int TABLE_RADIUS = 4;

    public final BlockPos pos;
    public final Set<BlockPos> table = new LinkedHashSet<>();

    public Seat(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public boolean inReach(BlockPos candidate) {
        return candidate.distSqr(pos) <= TABLE_RADIUS * TABLE_RADIUS;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos.asLong());
        ListTag list = new ListTag();
        table.forEach(p -> list.add(LongTag.valueOf(p.asLong())));
        t.put("table", list);
        return t;
    }

    public static Seat load(CompoundTag t) {
        Seat s = new Seat(BlockPos.of(t.getLong("pos")));
        for (Tag e : t.getList("table", Tag.TAG_LONG)) s.table.add(BlockPos.of(((LongTag) e).getAsLong()));
        return s;
    }
}
