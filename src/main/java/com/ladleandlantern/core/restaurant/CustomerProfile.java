package com.ladleandlantern.core.restaurant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CustomerProfile {
    public static final int MAX_STARS = 5;

    public final UUID id;
    public String name;
    public String skin;
    public CustomerType type;
    public int stars = 1;
    public int visits;
    public int favouriteCount;
    public boolean kid;
    public final List<ResourceLocation> favourites = new ArrayList<>();

    public CustomerProfile(UUID id, String name, String skin, CustomerType type, int favouriteCount) {
        this.id = id;
        this.name = name;
        this.skin = skin;
        this.type = type;
        this.favouriteCount = favouriteCount;
    }

    public void adjustStars(int delta) {
        stars = Math.max(0, Math.min(MAX_STARS, stars + delta));
    }

    public void refreshFavourites(List<ResourceLocation> menu, RandomSource random) {
        favourites.removeIf(f -> !menu.contains(f));
        List<ResourceLocation> pool = new ArrayList<>(menu);
        pool.removeAll(favourites);
        while (favourites.size() < favouriteCount && !pool.isEmpty()) {
            favourites.add(pool.remove(random.nextInt(pool.size())));
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("skin", skin);
        t.putInt("type", type.ordinal());
        t.putInt("stars", stars);
        t.putInt("visits", visits);
        t.putInt("favCount", favouriteCount);
        t.putBoolean("kid", kid);
        ListTag favs = new ListTag();
        favourites.forEach(f -> favs.add(StringTag.valueOf(f.toString())));
        t.put("favs", favs);
        return t;
    }

    public static CustomerProfile load(CompoundTag t) {
        CustomerProfile p = new CustomerProfile(t.getUUID("id"), t.getString("name"), t.getString("skin"),
                CustomerType.byId(t.getInt("type")), t.getInt("favCount"));
        p.stars = t.getInt("stars");
        p.visits = t.getInt("visits");
        p.kid = t.getBoolean("kid");
        for (Tag e : t.getList("favs", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
            if (id != null) p.favourites.add(id);
        }
        return p;
    }
}
