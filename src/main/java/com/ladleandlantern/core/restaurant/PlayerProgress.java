package com.ladleandlantern.core.restaurant;

import net.minecraft.nbt.CompoundTag;

public final class PlayerProgress {
    public long points;
    public int ribbons;
    public int ordersServed;

    public int rank() {
        return Rank.of(points);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("points", points);
        t.putInt("ribbons", ribbons);
        t.putInt("served", ordersServed);
        return t;
    }

    public static PlayerProgress load(CompoundTag t) {
        PlayerProgress p = new PlayerProgress();
        p.points = t.getLong("points");
        p.ribbons = t.getInt("ribbons");
        p.ordersServed = t.getInt("served");
        return p;
    }
}
