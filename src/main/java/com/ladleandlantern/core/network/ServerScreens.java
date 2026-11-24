package com.ladleandlantern.core.network;

import com.ladleandlantern.core.registry.BlockRegistry;
import com.ladleandlantern.core.shift.ShiftManager;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.entity.CustomerEntity;
import com.ladleandlantern.core.food.Order;
import com.ladleandlantern.core.restaurant.*;
import com.ladleandlantern.core.restaurant.GameCalendar.Holiday;
import com.ladleandlantern.core.restaurant.GameCalendar.Season;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public final class ServerScreens {
    public static final int STATUS_EMPTY = 0, STATUS_ARRIVING = 1, STATUS_READY = 2, STATUS_WAITING = 3, STATUS_EATING = 4;

    private ServerScreens() {}

    public static void openCashier(ServerPlayer player, Restaurant r, BlockPos cashier) {
        openCashier(player, r, cashier, false, false);
    }

    private static final List<String> STILL = List.of("menu", "slots", "activeSlots", "today", "profiles", "staff", "fixtures", "counter", "cashiers", "trays", "line");

    public static void openCashier(ServerPlayer player, Restaurant r, BlockPos cashier, boolean withProfiles, boolean live) {
        CompoundTag data = snapshot(player, r);
        if (!withProfiles) data.remove("profiles");
        if (live) {
            STILL.forEach(data::remove);
            data.putBoolean("_live", true);
        }
        data.putLong("cashier", cashier.asLong());
        PacketDistributor.sendToPlayer(player, new OpenScreenPayload(OpenScreenPayload.CASHIER, data));
    }

    public static void openNotebook(ServerPlayer player, Restaurant r) {
        PacketDistributor.sendToPlayer(player, new OpenScreenPayload(OpenScreenPayload.NOTEBOOK, snapshot(player, r)));
    }

    public static String slotKey(Season s) { return "S:" + s.name(); }
    public static String slotKey(Holiday h) { return "H:" + h.name(); }

    public static Enum<?> parseSlot(String key) {
        try {
            if (key.startsWith("S:")) return Season.valueOf(key.substring(2));
            if (key.startsWith("H:")) return Holiday.valueOf(key.substring(2));
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static CompoundTag snapshot(ServerPlayer player, Restaurant r) {
        ServerLevel level = player.serverLevel();
        long dayTime = level.getDayTime();
        long day = RestaurantStore.get(level.getServer()).day();
        int seasonLength = Config.SEASON_LENGTH.get();
        CompoundTag t = new CompoundTag();

        t.putString("name", r.name);
        t.putString("cuisine", r.cuisine);
        t.putBoolean("open", r.shiftOpen);
        t.putString("clock", Schedule.clock((int) (dayTime % 24000L)));
        t.putString("weekday", Schedule.DAY_NAMES[Schedule.weekday(day)]);
        Season season = GameCalendar.season(day, seasonLength);
        t.putString("season", season.label() + ", day " + GameCalendar.dayOfSeason(day, seasonLength) + " of " + seasonLength);
        List<Holiday> holidays = GameCalendar.holidays(day, seasonLength);
        t.putString("holidays", String.join(", ", holidays.stream().map(h -> h.label).toList()));
        t.putString("special", r.special == null ? "" : r.special.toString());
        t.putLong("dayAbs", day);
        t.putInt("seasonLength", seasonLength);
        t.putInt("criticDay", com.ladleandlantern.core.shift.ShiftManager.CRITIC_DAY);
        ListTag todayIds = new ListTag();
        r.available(day, seasonLength).keySet().forEach(id -> todayIds.add(StringTag.valueOf(id.toString())));
        t.put("today", todayIds);
        ListTag fixtures = new ListTag();
        r.fixtures.forEach((pos, kind) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", pos.asLong());
            e.putString("kind", kind.name());
            fixtures.add(e);
        });
        t.put("fixtures", fixtures);
        t.putInt("shiftOrders", r.shiftOrders);
        t.putInt("shiftAverage", r.shiftOrders == 0 ? 0 : Math.round(r.shiftScoreTotal / (float) r.shiftOrders));
        t.putInt("regulars", r.customers.size());
        t.putInt("arrivals", r.shiftArrivals);
        t.putInt("quota", com.ladleandlantern.core.shift.ShiftManager.dailyQuota(r));
        t.putInt("till", r.till);

        PlayerProgress p = RestaurantStore.get(level.getServer()).progress(player.getUUID());
        t.putLong("points", p.points);
        t.putInt("rank", p.rank());
        t.putString("title", Rank.title(p.rank()));
        t.putInt("rankFrom", Rank.threshold(p.rank()));
        t.putInt("rankTo", Rank.nextThreshold(p.rank()));
        t.putInt("ribbons", p.ribbons);

        ListTag checklist = new ListTag();
        if (r.entrance == null) checklist.add(StringTag.valueOf("Set an entrance with the planner"));
        if (r.seats.isEmpty()) checklist.add(StringTag.valueOf("Add seats with the planner (or rely on takeout)"));
        long noTable = r.seats.stream().filter(s -> s.table.isEmpty()).count();
        if (noTable > 0) checklist.add(StringTag.valueOf(noTable + (noTable == 1 ? " seat has" : " seats have") + " no table, so nobody sits there"));
        long outside = r.seats.stream().filter(s -> !r.contains(s.pos)).count();
        if (outside > 0) checklist.add(StringTag.valueOf(outside + (outside == 1 ? " seat is" : " seats are") + " outside the bounds"));
        long blocked = r.seats.stream().flatMap(s -> s.table.stream()).distinct()
                .filter(tb -> level.isLoaded(tb) && !level.getBlockState(tb.above()).canBeReplaced()
                        && !level.getBlockState(tb.above()).is(com.ladleandlantern.core.registry.BlockRegistry.SERVED_PLATE.get())
                        && !level.getBlockState(tb.above()).is(com.ladleandlantern.core.registry.BlockRegistry.TABLE_CRUMBS.get())).count();
        if (blocked > 0) checklist.add(StringTag.valueOf(blocked + (blocked == 1 ? " table has" : " tables have") + " something on top, so food can't go there"));
        if (r.trays.isEmpty()) checklist.add(StringTag.valueOf("Place a tray for finished orders"));
        if (r.waitingLine.isEmpty() && !r.seats.isEmpty()) checklist.add(StringTag.valueOf("Add waiting line spots so people can queue when it's full"));
        if (r.available(day, seasonLength).isEmpty()) checklist.add(StringTag.valueOf("Add dishes in the Menu tab"));
        if (!r.schedule.opensOn(Schedule.weekday(day))) checklist.add(StringTag.valueOf("Closed today (see Hours)"));
        if (r.fixtures(Fixture.MENU_STAND).isEmpty()) checklist.add(StringTag.valueOf("Write menu cards at a cash register and stand them in a menu stand"));
        t.put("checklist", checklist);

        t.putLong("min", r.min.asLong());
        t.putLong("max", r.max.asLong());
        t.putIntArray("floors", r.floors());
        Map<BlockPos, String> labels = r.seatLabels();
        List<CustomerEntity> customers = CustomerEntity.inRestaurant(level, r);
        Map<BlockPos, Integer> status = new HashMap<>();
        for (CustomerEntity c : customers) {
            if (c.seatPos() == null || c.state().leaving()) continue;
            status.put(c.seatPos(), switch (c.state()) {
                case ARRIVING -> STATUS_ARRIVING;
                case READY -> STATUS_READY;
                case WAITING_FOOD -> STATUS_WAITING;
                default -> STATUS_EATING;
            });
        }
        ListTag seats = new ListTag();
        for (Seat s : r.seats) {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", s.pos.asLong());
            e.putString("label", labels.getOrDefault(s.pos, "?"));
            e.putInt("status", status.getOrDefault(s.pos, STATUS_EMPTY));
            e.put("table", new LongArrayTag(s.table.stream().mapToLong(BlockPos::asLong).toArray()));
            seats.add(e);
        }
        t.put("seats", seats);
        t.put("counter", longs(r.counter));
        t.put("cashiers", longs(r.cashiers));
        t.put("trays", longs(r.trays));
        t.put("line", longs(r.waitingLine));
        if (r.entrance != null) t.putLong("entrance", r.entrance.asLong());

        ListTag menu = new ListTag();
        r.menu.forEach((id, course) -> menu.add(line(id, course)));
        t.put("menu", menu);
        CompoundTag slots = new CompoundTag();
        r.seasonal.forEach((s, ids) -> slots.put(slotKey(s), ids(ids)));
        r.holidays.forEach((h, ids) -> slots.put(slotKey(h), ids(ids)));
        t.put("slots", slots);
        ListTag active = new ListTag();
        active.add(StringTag.valueOf(slotKey(season)));
        holidays.forEach(h -> active.add(StringTag.valueOf(slotKey(h))));
        t.put("activeSlots", active);

        t.putInt("days", r.schedule.dayMask);
        t.putInt("openTime", r.schedule.openTime);
        t.putInt("closeTime", r.schedule.closeTime);

        ListTag staff = new ListTag();
        int due = 0;
        for (StaffRecord s : r.staff) {
            CompoundTag e = new CompoundTag();
            e.putString("name", s.name);
            e.putInt("level", s.level);
            e.putInt("wage", s.wage());
            if (!s.paidFor(day)) due += s.wage();
            e.putInt("hearts", s.hearts());
            e.putString("skin", s.skin == null ? "" : s.skin);
            e.putInt("birthday", s.birthday);
            e.putBoolean("owed", s.unpaidDays > 0);
            e.putString("title", s.title());
            e.putInt("upgrade", CashierActions.upgradeCost(s));
            e.putInt("birthdayIn", s.daysToBirthday(day, seasonLength));
            e.putString("cake", s.favouriteCake.toString());
            e.putBoolean("helps", s.role == StaffRole.CASHIER && s.affection >= StaffRecord.HELPS_AT);
            staff.add(e);
        }
        t.put("staff", staff);
        t.putInt("wagesDue", due);
        t.putInt("hireCost", Config.HIRE_COST.get());
        t.putInt("wageFund", r.wageFund);

        Set<ResourceLocation> today = r.available(day, seasonLength).keySet();
        ListTag profiles = new ListTag();
        r.customers.values().stream().sorted(Comparator.comparingInt((CustomerProfile c) -> -c.visits).thenComparing(c -> c.name))
                .forEach(c -> {
                    CompoundTag e = new CompoundTag();
                    e.putString("name", c.name);
                    e.putString("skin", c.skin);
                    e.putInt("stars", c.stars);
                    e.putInt("visits", c.visits);
                    e.putInt("type", c.type.ordinal());
                    e.putBoolean("kid", c.kid);
                    ListTag favs = new ListTag();
                    for (ResourceLocation f : c.favourites) {
                        CompoundTag fav = new CompoundTag();
                        fav.putString("id", f.toString());
                        fav.putBoolean("today", today.contains(f));
                        favs.add(fav);
                    }
                    e.put("favs", favs);
                    profiles.add(e);
                });
        t.put("profiles", profiles);
        t.putString("currency", new net.minecraft.world.item.ItemStack(Config.currency()).getHoverName().getString());
        t.putString("currencyId", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Config.currency()).toString());

        ListTag orders = new ListTag();
        long now = level.getGameTime();
        customers.stream().filter(c -> c.state().waiting())
                .sorted(Comparator.comparingLong(c -> c.order().readyTick))
                .forEach(c -> {
                    CompoundTag e = new CompoundTag();
                    Order o = c.order();
                    e.putString("label", c.label(r));
                    e.putString("customer", c.getCustomName() == null ? "Customer" : c.getCustomName().getString());
                    e.putBoolean("taken", o.taken);
                    e.putLong("secondsLeft", (o.deadlineTicks - o.elapsed(now)) / 20);
                    e.putFloat("patience", c.patience());
                    ListTag lines = new ListTag();
                    o.lines.forEach(l -> lines.add(line(l.item(), l.course())));
                    e.put("items", lines);
                    orders.add(e);
                });
        t.put("orders", orders);
        return t;
    }

    private static CompoundTag line(ResourceLocation id, Course course) {
        CompoundTag e = new CompoundTag();
        e.putString("id", id.toString());
        e.putString("course", course.name());
        return e;
    }

    private static ListTag ids(List<ResourceLocation> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    private static LongArrayTag longs(Collection<BlockPos> positions) {
        return new LongArrayTag(positions.stream().mapToLong(BlockPos::asLong).toArray());
    }
}
