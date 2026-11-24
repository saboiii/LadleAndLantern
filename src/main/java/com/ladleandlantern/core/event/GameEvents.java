package com.ladleandlantern.core.event;

import com.ladleandlantern.core.block.CashierBlock;
import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.entity.CustomerEntity;
import com.ladleandlantern.core.entity.StaffEntity;
import com.ladleandlantern.core.network.CalendarPayload;
import com.ladleandlantern.core.restaurant.GameCalendar;
import com.ladleandlantern.core.shift.StaffDinner;
import com.ladleandlantern.core.shift.Wages;

import com.ladleandlantern.core.item.PlannerItem;
import com.ladleandlantern.core.restaurant.Restaurant;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.ladleandlantern.core.shift.ShiftManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class GameEvents {
    private GameEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(GameEvents::onLevelTick);
        bus.addListener(GameEvents::onServerTick);
        bus.addListener(GameEvents::onRightClickBlock);
        bus.addListener(GameEvents::onBreak);
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) syncCalendar(p.serverLevel(), java.util.List.of(p));
        });
        bus.addListener((RegisterCommandsEvent e) -> LadleCommands.register(e.getDispatcher(), e.getBuildContext()));
        bus.addListener((ServerStoppingEvent e) -> RestaurantStore.get(e.getServer()).saveNow());
        bus.addListener((ServerStoppedEvent e) -> {
            RestaurantStore.close();

            com.ladleandlantern.core.entity.StaffEntity.clearCaches();
            com.ladleandlantern.core.entity.CustomerEntity.clearCaches();
            com.ladleandlantern.core.shift.StaffDinner.clearAll();
            com.ladleandlantern.core.block.CashierBlock.clearCaches();
        });
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long time = level.getGameTime();
        RestaurantStore store = RestaurantStore.get(level.getServer());
        if (time % 20 == 0 && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            long before = store.day();
            if (store.tickCalendar(level.getDayTime())) onNewDay(level, store, before);
        }
        if (time % 20 == 0) {
            for (Restaurant r : store.all()) if (r.dimension.equals(level.dimension())) ShiftManager.tick(level, r);
        }
        if (time % 10 == 0) {
            for (ServerPlayer player : level.players()) {
                ItemStack held = player.getMainHandItem();
                if (held.getItem() instanceof PlannerItem) {
                    PlannerItem.linked(held).flatMap(store::byId).ifPresent(r -> PlannerItem.showMarkers(player, held, r));
                }
            }
        }
    }

    private static void onNewDay(ServerLevel level, RestaurantStore store, long before) {
        int length = com.ladleandlantern.core.config.Config.SEASON_LENGTH.get();
        var from = com.ladleandlantern.core.restaurant.GameCalendar.season(before, length);
        var to = com.ladleandlantern.core.restaurant.GameCalendar.season(store.day(), length);
        syncCalendar(level, level.getServer().getPlayerList().getPlayers());

        for (Restaurant r : store.all()) {
            ServerLevel home = level.getServer().getLevel(r.dimension);
            if (home != null) com.ladleandlantern.core.shift.Wages.payFromFund(home, r, store.day() - 1);
        }
        if (from == to) return;
        for (Restaurant r : store.all()) {
            if (!r.dimension.equals(level.dimension())) continue;
            ShiftManager.banner(level, r, Component.literal(to.label() + " is here").withStyle(ChatFormatting.GOLD),
                    Component.literal(r.seasonal.get(to).isEmpty() ? "Add some " + to.label().toLowerCase() + " dishes in the Seasons tab"
                            : r.seasonal.get(to).size() + " " + to.label().toLowerCase() + " dishes are on the menu"));
        }
    }

    public static void syncCalendar(ServerLevel level, java.util.List<ServerPlayer> players) {
        RestaurantStore store = RestaurantStore.get(level.getServer());
        long worldDay = level.getServer().overworld().getDayTime() / 24000L;
        String season = com.ladleandlantern.core.restaurant.GameCalendar.season(store.day(), com.ladleandlantern.core.config.Config.SEASON_LENGTH.get()).label();
        var payload = new com.ladleandlantern.core.network.CalendarPayload(store.day() - worldDay, season);
        for (ServerPlayer p : players) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, payload);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        RestaurantStore.get(event.getServer()).flushIfDue();
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof PlannerItem) {
            if (event.getEntity() instanceof ServerPlayer player) PlannerItem.clickBlock(player, held, event.getPos());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (event.getEntity().isShiftKeyDown() && held.is(com.ladleandlantern.core.config.Config.currency())
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof com.ladleandlantern.core.block.CashierBlock) {
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        RestaurantStore store = RestaurantStore.get(level.getServer());
        for (Restaurant r : store.all()) {
            if (!r.dimension.equals(level.dimension()) || !r.nearBounds(pos, 12)) continue;
            boolean known = r.seatAt(pos).isPresent() || r.counter.contains(pos) || r.seats.stream().anyMatch(s -> s.table.contains(pos));
            if (!known) continue;
            r.removeBlock(pos);
            store.markDirty();
            event.getPlayer().displayClientMessage(Component.literal("Removed from " + r.name + "'s layout."), true);
        }
    }
}
