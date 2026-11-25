package com.ladleandlantern.core.restaurant;

import com.ladleandlantern.core.food.FoodRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.UUID;

public final class StaffRecord {
    public static final int MAX_LEVEL = 5;
    public static final int MAX_AFFECTION = 100;

    public static final int HELPS_AT = 40;

    public UUID entityId;
    public StaffRole role;
    public int level = 1;
    public String name;
    public String skin;

    public long paidThroughDay;

    public int birthday = -1;
    public ResourceLocation favouriteCake = ResourceLocation.withDefaultNamespace("cake");
    private boolean cakeChosen;
    public int affection;

    public long cakeDay = -1;
    public long helpedDay = -1;
    public int helpedToday;

    public int unpaidDays;
    public boolean woman;

    public int wage() {
        return role.baseWage + level - 1;
    }

    public boolean paidFor(long day) {
        return paidThroughDay >= day;
    }

    public String title() {
        return role == StaffRole.WAITER && woman ? "Waitress" : role.title;
    }

    public void ensureDetails(RandomSource random, int seasonLength) {
        if (birthday < 0) birthday = random.nextInt(seasonLength * 4);

        if (!cakeChosen) {
            List<ResourceLocation> cakes = FoodRules.cakes();
            favouriteCake = cakes.get(random.nextInt(cakes.size()));
            cakeChosen = true;
        }
    }

    public boolean birthdayToday(long day, int seasonLength) {
        return birthday >= 0 && Math.floorMod(day, seasonLength * 4L) == birthday;
    }

    public int daysToBirthday(long day, int seasonLength) {
        int year = seasonLength * 4;
        return birthday < 0 ? -1 : (int) Math.floorMod(birthday - Math.floorMod(day, (long) year), (long) year);
    }

    public void adjustAffection(int delta) {
        affection = Math.max(0, Math.min(MAX_AFFECTION, affection + delta));
    }

    public int hearts() {
        return affection / 20;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("entity", entityId);
        t.putString("role", role.name());
        t.putInt("level", level);
        t.putString("name", name);
        t.putString("skin", skin);
        t.putLong("paid", paidThroughDay);
        t.putInt("birthday", birthday);
        t.putString("cake", favouriteCake.toString());
        t.putInt("affection", affection);
        t.putLong("cakeDay", cakeDay);
        t.putLong("helpedDay", helpedDay);
        t.putInt("helped", helpedToday);
        t.putInt("unpaidDays", unpaidDays);
        t.putBoolean("woman", woman);
        return t;
    }

    public static StaffRecord load(CompoundTag t) {
        StaffRecord s = new StaffRecord();
        s.entityId = t.getUUID("entity");
        s.role = StaffRole.byName(t.getString("role"));
        s.level = Math.max(1, t.getInt("level"));
        s.name = t.getString("name");
        s.skin = t.getString("skin");
        s.paidThroughDay = t.getLong("paid");
        s.birthday = t.contains("birthday") ? t.getInt("birthday") : -1;
        ResourceLocation cake = ResourceLocation.tryParse(t.getString("cake"));

        if (cake != null && !cake.getPath().isEmpty()) {
            s.favouriteCake = cake;
            s.cakeChosen = true;
        }
        s.unpaidDays = t.getInt("unpaidDays");
        s.woman = t.getBoolean("woman");
        s.affection = t.getInt("affection");
        s.cakeDay = t.contains("cakeDay") ? t.getLong("cakeDay") : -1;
        s.helpedDay = t.contains("helpedDay") ? t.getLong("helpedDay") : -1;
        s.helpedToday = t.getInt("helped");
        return s;
    }
}
