package com.ladleandlantern.core.restaurant;

import net.minecraft.nbt.CompoundTag;

public final class Schedule {
    public static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    public int dayMask = 0b1111111;
    public int openTime = 1000;
    public int closeTime = 12500;

    public static int weekday(long day) {
        return (int) Math.floorMod(day, 7L);
    }

    public boolean opensOn(int weekday) {
        return (dayMask & (1 << weekday)) != 0;
    }

    public boolean isOpen(long day, long dayTime) {
        int t = (int) (dayTime % 24000L);
        if (!opensOn(weekday(day))) return false;
        return openTime <= closeTime ? t >= openTime && t < closeTime : t >= openTime || t < closeTime;
    }

    public float progress(long dayTime) {
        int t = (int) (dayTime % 24000L);
        int length = Math.floorMod(closeTime - openTime, 24000);
        if (length == 0) return 0;
        return Math.floorMod(t - openTime, 24000) / (float) length;
    }

    public void toggleDay(int weekday) {
        dayMask ^= 1 << weekday;
    }

    public static String clock(int ticks) {
        int minutes = (int) ((Math.floorMod(ticks + 6000, 24000)) * 1440L / 24000L);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("days", dayMask);
        t.putInt("open", openTime);
        t.putInt("close", closeTime);
        return t;
    }

    public void load(CompoundTag t) {
        if (t.contains("days")) dayMask = t.getInt("days");
        if (t.contains("open")) openTime = t.getInt("open");
        if (t.contains("close")) closeTime = t.getInt("close");
    }
}
