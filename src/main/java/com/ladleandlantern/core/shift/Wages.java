package com.ladleandlantern.core.shift;

import com.ladleandlantern.core.config.Config;

import com.ladleandlantern.core.item.ThankYouNoteItem;
import com.ladleandlantern.core.restaurant.Restaurant;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.ladleandlantern.core.restaurant.StaffRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;

public final class Wages {
    private static final int HEARTS_LOST_PER_DAY = 15;
    private static final int DAYS_BEFORE_RESIGNING = 3;

    private Wages() {}

    public static void payFromFund(ServerLevel level, Restaurant r, long today) {
        String currency = new net.minecraft.world.item.ItemStack(com.ladleandlantern.core.config.Config.currency()).getHoverName().getString();
        int paid = settle(r, today);
        int owed = r.staff.stream().filter(s -> !s.paidFor(today)).mapToInt(StaffRecord::wage).sum();
        if (paid == 0 && owed == 0) return;
        Component line;
        if (owed == 0) {
            line = Component.literal("Paid " + paid + " " + currency + " in wages from the wage fund. " + r.wageFund + " left.")
                    .withStyle(ChatFormatting.GREEN);
        } else {
            line = Component.literal((paid > 0 ? "Paid " + paid + " " + currency + " in wages. " : "") + "Still owed: " + owed + " " + currency
                    + ". Sneak-right-click a cash register with " + currency + " to top up the wage fund.").withStyle(ChatFormatting.GOLD);
        }
        ShiftManager.nearbyPlayers(level, r, 64).forEach(p -> p.sendSystemMessage(line));
        RestaurantStore.get(level.getServer()).markDirty();
    }

    public static int settle(Restaurant r, long throughDay) {
        int paid = 0;
        for (StaffRecord s : r.staff) {
            s.paidThroughDay = Math.max(s.paidThroughDay, throughDay - 7);
            while (s.wage() > 0 && s.paidThroughDay < throughDay && r.wageFund >= s.wage()) {
                r.wageFund -= s.wage();
                paid += s.wage();
                s.paidThroughDay++;
                s.adjustAffection(1);
            }
        }
        return paid;
    }

    public static void checkUnpaid(ServerLevel level, Restaurant r, long today) {
        settle(r, today - 1);
        Iterator<StaffRecord> it = r.staff.iterator();
        while (it.hasNext()) {
            StaffRecord s = it.next();
            if (s.paidThroughDay >= today - 1) {
                s.unpaidDays = 0;
                continue;
            }
            s.unpaidDays++;
            s.adjustAffection(-HEARTS_LOST_PER_DAY);
            if (s.unpaidDays < DAYS_BEFORE_RESIGNING) continue;
            it.remove();
            if (level.getEntity(s.entityId) != null) level.getEntity(s.entityId).discard();
            leaveNote(level, r, s);
        }
        RestaurantStore.get(level.getServer()).markDirty();
    }

    private static void leaveNote(ServerLevel level, Restaurant r, StaffRecord s) {
        BlockPos spot = r.counter.isEmpty() ? (r.cashiers.isEmpty() ? r.min : r.cashiers.get(0)) : r.counter.iterator().next();
        ItemEntity note = new ItemEntity(level, spot.getX() + 0.5, spot.getY() + 1.05, spot.getZ() + 0.5, ThankYouNoteItem.goodbye(s.name));
        note.setDeltaMovement(Vec3.ZERO);
        note.setUnlimitedLifetime();
        level.addFreshEntity(note);
        Component line = Component.literal(s.title() + " " + s.name + " has resigned after going unpaid. They left a note on the counter.")
                .withStyle(ChatFormatting.RED);
        ShiftManager.nearbyPlayers(level, r, 64).forEach(p -> p.sendSystemMessage(line));
    }
}
