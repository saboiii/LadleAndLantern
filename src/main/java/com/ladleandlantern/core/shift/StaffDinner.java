package com.ladleandlantern.core.shift;

import com.ladleandlantern.core.util.Fancy;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.entity.Emote;
import com.ladleandlantern.core.entity.StaffEntity;
import com.ladleandlantern.core.registry.SoundEventRegistry;
import com.ladleandlantern.core.restaurant.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class StaffDinner {
    public static final int DINNER_LATEST = 14500;

    public static final int WRAP_UP = 15500;

    public static final int IN_BED_BY = 16000;

    public static final int BED_FROM = 13500, BED_UNTIL = 23000;
    private static final int SETTLE_TICKS = 10 * 20;
    private static final int LINE_GAP = 90;
    private static final int REPLY_WAIT = 25 * 20;
    private static final Map<UUID, Dinner> DINNERS = new HashMap<>();

    private static final class Dinner {
        final List<UUID> staff = new ArrayList<>();
        List<String> lines = List.of();
        int next;
        long started;
        long nextLineAt;
        boolean awaitingReply;
        long finishedAt = -1;
        long lastDayTime;

        long awaySince = -1;
    }

    private StaffDinner() {}

    public static void clearAll() {
        DINNERS.clear();
    }

    public static boolean timeForDinner(ServerLevel level) {
        return level.getDayTime() % 24000L < DINNER_LATEST;
    }

    public static boolean bedtimeFor(ServerLevel level, Restaurant r) {
        long t = level.getDayTime() % 24000L;
        if (r.shiftOpen || active(r) || t < BED_FROM || t >= BED_UNTIL) return false;
        return !r.ritualPending || t >= 15000 || !timeForDinner(level);
    }

    public static void start(ServerLevel level, Restaurant r) {
        stop(level, r);
        if (!timeForDinner(level)) return;
        List<StaffEntity> staff = new ArrayList<>();
        for (StaffRecord s : r.staff) {
            if (level.getEntity(s.entityId) instanceof StaffEntity e && !e.slacking() && !e.isSleeping()) staff.add(e);
        }
        if (staff.isEmpty() || r.seats.isEmpty()) return;
        List<BlockPos> seats = dinnerSeats(r, staff, Math.min(3, staff.size()));
        if (seats.isEmpty()) return;
        Dinner d = new Dinner();
        d.started = level.getGameTime();
        d.lastDayTime = level.getDayTime();
        for (int i = 0; i < staff.size() && i < seats.size() && i < 3; i++) {
            staff.get(i).sitForDinner(seats.get(i));
            d.staff.add(staff.get(i).getUUID());
        }
        DINNERS.put(r.id, d);
        announce(level, r, com.ladleandlantern.core.util.Fancy.gradient("\u273f It was a good day. Time to sit down and dine with your colleagues... \u273f",
                com.ladleandlantern.core.util.Fancy.PEACH, com.ladleandlantern.core.util.Fancy.PINK));
        int size = d.staff.size();
        List<List<String>> pool = size == 1 ? Conversations.SOLO : size == 2 ? Conversations.DUO : Conversations.TRIO;
        d.lines = pool.get(level.random.nextInt(pool.size()));
        d.nextLineAt = d.started + SETTLE_TICKS;
    }

    private static List<BlockPos> dinnerSeats(Restaurant r, List<StaffEntity> staff, int needed) {
        double cx = 0, cy = 0, cz = 0;
        for (StaffEntity e : staff) { cx += e.getX(); cy += e.getY(); cz += e.getZ(); }
        final double x = cx / staff.size(), y = cy / staff.size(), z = cz / staff.size();
        List<List<BlockPos>> groups = new ArrayList<>();
        for (List<BlockPos> g : r.tableGroups()) {
            List<BlockPos> usable = g.stream().filter(p -> r.seatAt(p).map(s -> !s.table.isEmpty()).orElse(false)).toList();
            if (!usable.isEmpty()) groups.add(usable);
        }
        if (groups.isEmpty()) return List.of();

        java.util.function.ToDoubleFunction<List<BlockPos>> away = g -> g.stream()
                .mapToDouble(p -> Math.sqrt((p.getX() + 0.5 - x) * (p.getX() + 0.5 - x) + (p.getZ() + 0.5 - z) * (p.getZ() + 0.5 - z))
                        + (Math.abs(p.getY() - y) > 2 ? 1000 : 0)).min().orElse(Double.MAX_VALUE);
        List<List<BlockPos>> byDistance = new ArrayList<>(groups);
        byDistance.sort(Comparator.comparingDouble(away));
        for (List<BlockPos> g : byDistance) {
            if (g.size() >= needed && away.applyAsDouble(g) < 1000) return g;
        }

        List<BlockPos> first = byDistance.get(0);
        BlockPos anchor = first.get(0);
        List<BlockPos> seats = new ArrayList<>(first);
        byDistance.stream().skip(1).sorted(Comparator.comparingDouble(g -> g.get(0).distSqr(anchor))).forEach(seats::addAll);
        return seats;
    }

    private static void announce(ServerLevel level, Restaurant r, net.minecraft.network.chat.Component line) {
        ShiftManager.nearbyPlayers(level, r, 8).forEach(p -> p.sendSystemMessage(line));
    }

    public static boolean active(Restaurant r) {
        return DINNERS.containsKey(r.id);
    }

    public static void stop(ServerLevel level, Restaurant r) {
        Dinner d = DINNERS.remove(r.id);
        if (d == null) return;
        for (UUID id : d.staff) if (level.getEntity(id) instanceof StaffEntity e) e.sitForDinner(null);
    }

    private static void end(ServerLevel level, Restaurant r) {
        Dinner d = DINNERS.remove(r.id);
        if (d == null) return;
        int delay = 10;
        for (UUID id : d.staff) {
            if (level.getEntity(id) instanceof StaffEntity e) {
                e.speak("", 1);
                e.goToBed(delay + level.random.nextInt(25));
                delay += 20 + level.random.nextInt(30);
            }
        }
    }

    public static void cancel(ServerLevel level, Restaurant r) {
        if (!DINNERS.containsKey(r.id)) return;
        end(level, r);
        announce(level, r, com.ladleandlantern.core.util.Fancy.gradient("\u263e The chairs are tucked in. Dinner will keep for another evening.",
                com.ladleandlantern.core.util.Fancy.LAVENDER, com.ladleandlantern.core.util.Fancy.PEACH));
    }

    public static void tick(ServerLevel level, Restaurant r) {
        Dinner d = DINNERS.get(r.id);
        if (d == null) return;
        long now = level.getGameTime();
        long dayTime = level.getDayTime();

        boolean clockJumped = Math.abs(dayTime - d.lastDayTime) > 1200;
        d.lastDayTime = dayTime;
        if (clockJumped || dayTime % 24000L >= WRAP_UP) {
            cancel(level, r);
            return;
        }

        List<ServerPlayer> near = ShiftManager.nearbyPlayers(level, r, 12);
        if (near.isEmpty()) {
            if (d.awaySince < 0) d.awaySince = now;
            if (now - d.awaySince > 30 * 20) cancel(level, r);
            return;
        }
        d.awaySince = -1;

        d.staff.removeIf(id -> !(level.getEntity(id) instanceof StaffEntity));
        List<StaffEntity> staff = d.staff.stream().map(level::getEntity).map(e -> (StaffEntity) e).toList();

        if (staff.stream().anyMatch(e -> !e.dinnerSeatValid(level, r))) {
            cancel(level, r);
            return;
        }
        if (staff.isEmpty()) {
            end(level, r);
            return;
        }
        if (d.finishedAt >= 0) {
            if (now - d.finishedAt > 8 * 20) finish(level, r, d);
            return;
        }
        if (!com.ladleandlantern.core.config.Config.DINNER_TALK.get()) {
            if (now - d.started > 60 * 20) finish(level, r, d);
            return;
        }
        boolean allSeated = staff.stream().allMatch(StaffEntity::seated);
        boolean anySeated = staff.stream().anyMatch(StaffEntity::seated);
        if (!allSeated && !(anySeated && now - d.started > SETTLE_TICKS)) {
            if (now - d.started > SETTLE_TICKS * 9) end(level, r);
            lookAround(staff, null, near.get(0));
            return;
        }
        if (d.next == 0 && d.nextLineAt > now + 40) d.nextLineAt = now + 40;
        if (now < d.nextLineAt || d.awaitingReply && now < d.nextLineAt + REPLY_WAIT) return;
        if (d.next >= d.lines.size()) {
            d.finishedAt = now;
            return;
        }
        String[] line = d.lines.get(d.next++).split("\\|", 2);
        StaffEntity speaker = staff.get(Math.min(staff.size() - 1, Integer.parseInt(line[0])));
        lookAround(staff, speaker, near.get(0));
        String text = fill(line[1], staff, near.get(0), RestaurantStore.get(level.getServer()).day());
        speaker.speak(text, LINE_GAP);
        speaker.voice(SoundEventRegistry.CHATTER.get(), 0.6f);
        if (text.contains("Ha") || text.contains("!")) speaker.emote(Emote.SMILE, 40);
        Component chat = Component.literal("[" + speaker.firstName() + "] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        near.forEach(p -> p.sendSystemMessage(chat));
        d.nextLineAt = now + LINE_GAP + text.length();
        d.awaitingReply = staff.size() == 1;
        if (d.awaitingReply) near.forEach(p -> p.sendSystemMessage(replyOptions(level)));
    }

    private static Component replyOptions(ServerLevel level) {
        List<Integer> picks = new ArrayList<>();
        while (picks.size() < 3) {
            int i = level.random.nextInt(Conversations.REPLIES.size());
            if (!picks.contains(i)) picks.add(i);
        }
        MutableComponent out = Component.literal("  ");
        for (int i : picks) {
            String reply = Conversations.REPLIES.get(i);
            out.append(Component.literal("[" + reply + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ladle reply " + i))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Say this")))));
            out.append(Component.literal("  "));
        }
        return out;
    }

    public static void reply(ServerPlayer player, int index) {
        if (index < 0 || index >= Conversations.REPLIES.size()) return;
        ServerLevel level = player.serverLevel();
        RestaurantStore store = RestaurantStore.get(level.getServer());
        for (Restaurant r : store.all()) {
            Dinner d = DINNERS.get(r.id);
            if (d == null || !d.awaitingReply || !r.box().inflate(16).contains(player.position())) continue;
            d.awaitingReply = false;
            d.nextLineAt = level.getGameTime() + 40;
            Component chat = Component.literal("<" + player.getName().getString() + "> " + Conversations.REPLIES.get(index));
            ShiftManager.nearbyPlayers(level, r, 16).forEach(p -> p.sendSystemMessage(chat));
            return;
        }
    }

    private static void finish(ServerLevel level, Restaurant r, Dinner d) {
        for (UUID id : d.staff) r.staffRecord(id).ifPresent(s -> s.adjustAffection(5));
        RestaurantStore.get(level.getServer()).markDirty();
        end(level, r);
    }

    private static void lookAround(List<StaffEntity> staff, StaffEntity speaker, ServerPlayer player) {
        for (int i = 0; i < staff.size(); i++) {
            StaffEntity s = staff.get(i);
            if (speaker != null && s != speaker) s.lookAt(speaker.getUUID());
            else if (staff.size() == 1) s.lookAt(player.getUUID());
            else s.lookAt(staff.get((i + 1) % staff.size()).getUUID());
        }
    }

    private static String fill(String text, List<StaffEntity> staff, ServerPlayer player, long day) {
        String[] keys = {"{a}", "{b}", "{c}"};
        for (int i = 0; i < keys.length; i++) {
            text = text.replace(keys[i], i < staff.size() ? staff.get(i).firstName() : "you");
        }
        return text.replace("{player}", player.getName().getString())
                .replace("{season}", GameCalendar.season(day, Config.SEASON_LENGTH.get()).label().toLowerCase(Locale.ROOT));
    }
}
