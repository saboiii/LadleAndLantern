package com.ladleandlantern.core.network;

import com.ladleandlantern.core.shift.Wages;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.block.CashierBlock;
import com.ladleandlantern.core.entity.StaffEntity;
import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.food.Names;
import com.ladleandlantern.core.restaurant.*;
import com.ladleandlantern.core.restaurant.GameCalendar.Holiday;
import com.ladleandlantern.core.restaurant.GameCalendar.Season;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public final class CashierActions {
    private static final int MAX_STAFF = 8;
    private static final int MAX_TEXT = 32;

    private CashierActions() {}

    public static void handle(CashierActionPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos cashier = payload.cashier();
        if (player.distanceToSqr(cashier.getCenter()) > 64 || !(level.getBlockState(cashier).getBlock() instanceof CashierBlock)) return;
        RestaurantStore store = RestaurantStore.get(level.getServer());
        Restaurant r = store.byCashier(level.dimension(), cashier).orElse(null);
        if (r == null) return;

        if (tooFast(player, level.getGameTime())) return;
        boolean view = payload.action().equals("view");
        if (!view && !canManage(player, r)) {
            player.displayClientMessage(Component.literal("Only the owner can change " + r.name + "."), true);
            return;
        }
        String error = view ? null : apply(player, level, r, cashier, payload.action(), payload.args());
        if (error != null) player.displayClientMessage(Component.literal(error), true);

        if (!view) store.markDirty();
        ServerScreens.openCashier(player, r, cashier, payload.args().getBoolean("_profiles"), view && payload.args().getBoolean("_live"));
    }

    public static boolean canManage(ServerPlayer player, Restaurant r) {
        if (r.owner == null || r.owner.equals(player.getUUID()) || player.hasPermissions(2)) return true;
        net.minecraft.world.scores.PlayerTeam team = player.getTeam();
        if (team == null) return false;
        ServerPlayer owner = player.server.getPlayerList().getPlayer(r.owner);
        return owner != null && team.equals(owner.getTeam());
    }

    private static final java.util.Map<java.util.UUID, long[]> RATE = new java.util.HashMap<>();

    private static boolean tooFast(ServerPlayer player, long tick) {
        long[] seen = RATE.computeIfAbsent(player.getUUID(), k -> new long[]{-1, 0});
        if (seen[0] != tick) {
            seen[0] = tick;
            seen[1] = 0;
        }
        if (RATE.size() > 256) RATE.keySet().removeIf(id -> player.server.getPlayerList().getPlayer(id) == null);
        return ++seen[1] > 4;
    }

    private static String apply(ServerPlayer player, ServerLevel level, Restaurant r, BlockPos cashier, String action, CompoundTag a) {
        long day = RestaurantStore.get(level.getServer()).day();
        switch (action) {
            case "rename" -> r.name = text(a, r.name);
            case "cuisine" -> r.cuisine = text(a, r.cuisine);
            case "menu_add" -> {
                ResourceLocation id = food(a);
                if (id == null) return "That isn't food.";
                if (r.menu.putIfAbsent(id, FoodRules.classify(id)) == null) r.menuChanged(id, level.random);
            }
            case "menu_remove" -> {
                if (r.menu.remove(ResourceLocation.tryParse(a.getString("id"))) != null) r.menuChanged(null, level.random);
            }
            case "menu_course" -> {
                ResourceLocation id = ResourceLocation.tryParse(a.getString("id"));
                if (id != null) r.menu.computeIfPresent(id, (k, c) -> c.next());
            }
            case "slot_add", "slot_remove" -> {
                List<ResourceLocation> slot = slot(r, a.getString("slot"));
                if (slot == null) return null;
                if (action.equals("slot_remove")) {
                    if (slot.remove(ResourceLocation.tryParse(a.getString("id")))) r.menuChanged(null, level.random);
                } else {
                    ResourceLocation id = food(a);
                    if (id == null) return "That isn't food.";
                    if (!slot.contains(id)) {
                        slot.add(id);
                        r.menuChanged(id, level.random);
                    }
                }
            }
            case "day" -> {
                int d = a.getInt("v");
                if (d >= 0 && d < 7) r.schedule.toggleDay(d);
            }
            case "time" -> {
                int delta = Math.max(-6000, Math.min(6000, a.getInt("v")));
                if (a.getString("which").equals("open")) r.schedule.openTime = Math.floorMod(r.schedule.openTime + delta, 24000);
                else r.schedule.closeTime = Math.floorMod(r.schedule.closeTime + delta, 24000);
            }
            case "hire" -> {
                if (r.staff.size() >= MAX_STAFF) return "You can't hire more than " + MAX_STAFF + " staff.";
                StaffRole role = StaffRole.byName(a.getString("role"));

                if (role == StaffRole.CASHIER && com.ladleandlantern.core.entity.StaffEntity.staffedTill(level, r, cashier)) {
                    boolean freeTill = r.cashiers.stream().anyMatch(c -> !com.ladleandlantern.core.entity.StaffEntity.staffedTill(level, r, c));
                    return freeTill ? "This till already has a cashier. Open the book at your other cash register to hire one there."
                            : "Each cash register has a cashier already. Place another cash register to hire another.";
                }
                int cost = Config.HIRE_COST.get();
                if (!take(player, Config.currency(), cost)) return "Hiring costs " + cost + " " + currencyName() + ".";
                StaffRecord s = new StaffRecord();
                s.role = role;
                s.woman = level.random.nextBoolean();
                s.name = Names.staff(level.random, s.woman);
                s.skin = Names.staffSkin(level.random, s.woman);
                s.paidThroughDay = day;
                s.ensureDetails(level.random, Config.SEASON_LENGTH.get());
                StaffEntity.spawn(level, r, s, cashier);
                r.staff.add(s);
            }
            case "upgrade" -> {
                StaffRecord s = staff(r, a);
                if (s == null) return null;
                if (s.level >= StaffRecord.MAX_LEVEL) return s.name + " is already at the top level.";
                int cost = upgradeCost(s);
                if (!take(player, Config.currency(), cost)) return "Upgrading costs " + cost + " " + currencyName() + ".";
                s.level++;
            }
            case "pay" -> {
                int due = r.staff.stream().filter(s -> !s.paidFor(day)).mapToInt(StaffRecord::wage).sum();
                if (due == 0) return "Everyone's paid for today.";
                if (!take(player, Config.currency(), due)) return "Wages today are " + due + " " + currencyName() + ".";
                r.staff.forEach(s -> {
                    if (!s.paidFor(day)) s.adjustAffection(1);
                    s.paidThroughDay = Math.max(s.paidThroughDay, day);
                });
            }
            case "withdraw" -> {
                if (r.wageFund <= 0) return "The wage fund is empty.";
                int left = r.wageFund;
                r.wageFund = 0;
                int max = Config.currency().getDefaultMaxStackSize();
                while (left > 0) {
                    ItemStack s = new ItemStack(Config.currency(), Math.min(max, left));
                    left -= s.getCount();
                    if (!player.getInventory().add(s)) player.drop(s, false);
                }
            }
            case "dismiss" -> {
                StaffRecord s = staff(r, a);
                if (s == null) return null;
                if (level.getEntity(s.entityId) != null) level.getEntity(s.entityId).discard();
                r.staff.remove(s);
            }
            default -> {}
        }
        return null;
    }

    public static int upgradeCost(StaffRecord s) {
        return Config.HIRE_COST.get() * (s.level + 1);
    }

    private static String text(CompoundTag a, String fallback) {
        String v = a.getString("v").replaceAll("[\\p{Cntrl}\u00a7]", "").trim();
        return v.isEmpty() ? fallback : v.substring(0, Math.min(MAX_TEXT, v.length()));
    }

    private static ResourceLocation food(CompoundTag a) {
        ResourceLocation id = ResourceLocation.tryParse(a.getString("id"));
        return id != null && FoodRules.isFood(id) ? id : null;
    }

    private static List<ResourceLocation> slot(Restaurant r, String key) {
        return switch (ServerScreens.parseSlot(key)) {
            case Season s -> r.seasonal.get(s);
            case Holiday h -> r.holidays.get(h);
            case null, default -> null;
        };
    }

    private static StaffRecord staff(Restaurant r, CompoundTag a) {
        int i = a.getInt("index");
        return i >= 0 && i < r.staff.size() ? r.staff.get(i) : null;
    }

    private static String currencyName() {
        return new ItemStack(Config.currency()).getHoverName().getString();
    }

    private static boolean take(ServerPlayer player, Item item, int amount) {
        if (player.getAbilities().instabuild) return true;
        var inv = player.getInventory();
        if (inv.countItem(item) < amount) return false;
        inv.clearOrCountMatchingItems(s -> s.is(item), amount, player.inventoryMenu.getCraftSlots());
        inv.setChanged();
        return true;
    }
}
