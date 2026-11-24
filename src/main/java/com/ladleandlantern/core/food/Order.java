package com.ladleandlantern.core.food;

import com.ladleandlantern.core.restaurant.Course;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class Order {
    public record Line(ResourceLocation item, Course course) {}

    public final List<Line> lines = new ArrayList<>();
    public int ticket;
    public long readyTick = -1;
    public int deadlineTicks;
    public boolean taken;

    public List<ResourceLocation> items() {
        return lines.stream().map(Line::item).toList();
    }

    public boolean started() {
        return readyTick >= 0;
    }

    public long elapsed(long gameTime) {
        return started() ? gameTime - readyTick : 0;
    }

    public float patience(long gameTime) {
        return deadlineTicks <= 0 ? 1 : 1f - elapsed(gameTime) / (float) deadlineTicks;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (Line l : lines) {
            CompoundTag e = new CompoundTag();
            e.putString("item", l.item().toString());
            e.putString("course", l.course().name());
            list.add(e);
        }
        t.put("lines", list);
        t.putInt("ticket", ticket);
        t.putLong("ready", readyTick);
        t.putInt("deadline", deadlineTicks);
        t.putBoolean("taken", taken);
        return t;
    }

    public static Order load(CompoundTag t) {
        Order o = new Order();
        for (Tag e : t.getList("lines", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            ResourceLocation id = ResourceLocation.tryParse(c.getString("item"));
            if (id != null) o.lines.add(new Line(id, Course.byName(c.getString("course"))));
        }
        o.ticket = t.getInt("ticket");
        o.readyTick = t.getLong("ready");
        o.deadlineTicks = t.getInt("deadline");
        o.taken = t.getBoolean("taken");
        return o;
    }
}
