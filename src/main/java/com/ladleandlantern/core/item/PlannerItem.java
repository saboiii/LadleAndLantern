package com.ladleandlantern.core.item;

import com.ladleandlantern.core.network.CashierActions;
import com.ladleandlantern.core.util.ClientAccess;

import com.ladleandlantern.core.block.CashierBlock;
import com.ladleandlantern.core.restaurant.Restaurant;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.ladleandlantern.core.restaurant.Seat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlannerItem extends Item {
    private static final DustParticleOptions SEAT = new DustParticleOptions(new Vector3f(1f, 0.8f, 0.2f), 1.2f);
    private static final DustParticleOptions SELECTED = new DustParticleOptions(new Vector3f(1f, 1f, 1f), 1.6f);
    private static final DustParticleOptions WARNING = new DustParticleOptions(new Vector3f(0.95f, 0.12f, 0.12f), 1.3f);
    private static final DustParticleOptions TABLE = new DustParticleOptions(new Vector3f(0.55f, 0.35f, 0.2f), 1.2f);
    private static final DustParticleOptions COUNTER = new DustParticleOptions(new Vector3f(0.95f, 0.95f, 0.95f), 1.0f);
    private static final DustParticleOptions ENTRANCE = new DustParticleOptions(new Vector3f(0.2f, 1f, 0.3f), 1.6f);
    private static final DustParticleOptions BOUNDS = new DustParticleOptions(new Vector3f(0.4f, 0.6f, 1f), 1.0f);
    private static final DustParticleOptions LINE = new DustParticleOptions(new Vector3f(0.2f, 0.8f, 0.8f), 1.4f);

    public PlannerItem(Properties props) {
        super(props);
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void edit(ItemStack stack, java.util.function.Consumer<CompoundTag> edit) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, edit);
    }

    public static PlannerMode mode(ItemStack stack) {
        return PlannerMode.byId(data(stack).getInt("mode"));
    }

    public static Optional<UUID> linked(ItemStack stack) {
        CompoundTag t = data(stack);
        return t.hasUUID("restaurant") ? Optional.of(t.getUUID("restaurant")) : Optional.empty();
    }

    private static Optional<BlockPos> stored(ItemStack stack, String key) {
        CompoundTag t = data(stack);
        return t.contains(key) ? Optional.of(BlockPos.of(t.getLong(key))) : Optional.empty();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) com.ladleandlantern.core.util.ClientAccess.get().openPlanner(mode(stack), linked(stack).isPresent());
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void setMode(ServerPlayer player, PlannerMode mode) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof PlannerItem)) return;
        edit(stack, t -> {
            t.putInt("mode", mode.ordinal());
            t.remove("corner");
        });
        player.displayClientMessage(Component.literal(mode.label + ": " + mode.hint), true);
    }

    public static void clickBlock(ServerPlayer player, ItemStack stack, BlockPos clicked) {
        BlockPos pos = clicked.immutable();
        ServerLevel level = player.serverLevel();
        RestaurantStore store = RestaurantStore.get(level.getServer());

        if (player.isShiftKeyDown() && level.getBlockState(pos).getBlock() instanceof CashierBlock) {
            store.byCashier(level.dimension(), pos)
                    .ifPresent(r -> {
                        edit(stack, t -> t.putUUID("restaurant", r.id));
                        tell(player, "Linked to " + r.name + ". Right-click the air to change mode.");
                    });
            return;
        }

        Restaurant r = linked(stack).flatMap(store::byId).filter(x -> x.dimension.equals(level.dimension())).orElse(null);
        if (r == null) {
            tell(player, "Sneak-right-click a cash register first to link this planner.");
            return;
        }
        if (!com.ladleandlantern.core.network.CashierActions.canManage(player, r)) {
            tell(player, "Only the owner can change " + r.name + "'s layout.");
            return;
        }
        switch (mode(stack)) {
            case BOUNDS -> clickBounds(player, stack, r, pos);
            case SEAT -> clickSeat(player, stack, r, pos);
            case TABLE -> clickTable(player, stack, r, pos);
            case COUNTER -> {
                if (!inside(player, r, pos)) return;
                boolean added = r.counter.add(pos);
                if (!added) r.counter.remove(pos);
                tell(player, added ? "Counter block added." : "Counter block removed.");
            }
            case WAITING_LINE -> {
                BlockPos spot = pos.above();
                if (r.waitingLine.remove(spot)) {
                    tell(player, "Removed from the waiting line.");
                } else if (r.waitingLine.size() >= Restaurant.MAX_WAITING_LINE) {
                    tell(player, "The line already has " + Restaurant.MAX_WAITING_LINE + " spots. Click one to remove it.");
                    return;
                } else if (!r.nearBounds(pos, 12)) {
                    tell(player, "Keep the line within 12 blocks of the restaurant.");
                    return;
                } else if (!standable(player.serverLevel(), spot)) {
                    tell(player, "Customers can't stand there. Pick a floor block with two blocks of space above it.");
                    return;
                } else {
                    r.waitingLine.add(spot);
                    tell(player, "Waiting spot " + r.waitingLine.size() + " of " + Restaurant.MAX_WAITING_LINE + " added.");
                }
            }
            case ENTRANCE -> {
                if (!r.nearBounds(pos, 8)) {
                    tell(player, "The entrance has to be within 8 blocks of the restaurant.");
                    return;
                }
                if (!standable(player.serverLevel(), pos.above())) {
                    tell(player, "Customers can't stand there. Click the floor in the doorway, with two blocks of space above it.");
                    return;
                }
                r.entrance = pos.above();
                tell(player, "Customers will arrive and leave here.");
            }
        }
        r.layoutChanged();
        store.markDirty();
    }

    private static void clickBounds(ServerPlayer player, ItemStack stack, Restaurant r, BlockPos pos) {
        if (r.owner != null && !r.owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
            tell(player, "Only the owner can move the restaurant's bounds.");
            return;
        }
        if (player.isShiftKeyDown()) {
            BlockPos c = r.cashiers.isEmpty() ? pos : r.cashiers.get(0);
            r.setBounds(c.offset(-8, -1, -8), c.offset(8, 6, 8));
            edit(stack, t -> t.remove("corner"));
            tell(player, "Bounds reset around the cash register.");
            return;
        }
        Optional<BlockPos> corner = stored(stack, "corner");
        if (corner.isEmpty()) {
            edit(stack, t -> t.putLong("corner", pos.asLong()));
            tell(player, "Corner A set. Click the opposite corner.");
        } else if (!Restaurant.validSize(corner.get(), pos)) {
            tell(player, "That's too big. Keep each side under " + Restaurant.MAX_SIZE + " blocks.");
        } else {
            r.setBounds(corner.get(), pos);
            edit(stack, t -> t.remove("corner"));
            tell(player, "Bounds set.");
        }
    }

    private static void clickSeat(ServerPlayer player, ItemStack stack, Restaurant r, BlockPos pos) {
        Optional<Seat> existing = r.seatAt(pos);
        if (player.isShiftKeyDown()) {
            if (existing.isPresent()) {
                r.seats.remove(existing.get());
                edit(stack, t -> t.remove("seat"));
                tell(player, "Seat removed.");
            } else {
                tell(player, "That isn't a seat. Sneak-click a seat to remove it.");
            }
            return;
        }
        if (r.seats.stream().anyMatch(s -> s.table.contains(pos))) {
            tell(player, "That block is a table. Switch to Tables mode to work with tables.");
            return;
        }
        if (existing.isEmpty() && !inside(player, r, pos)) return;
        Seat seat = existing.orElseGet(() -> {
            Seat fresh = new Seat(pos.immutable());
            r.seats.add(fresh);
            return fresh;
        });
        edit(stack, t -> t.putLong("seat", pos.asLong()));
        String label = "Seat " + r.seatLabels().getOrDefault(pos, "?");

        Optional<BlockPos> table = "table".equals(last(stack)) ? stored(stack, "table") : Optional.empty();
        if (table.isPresent()) {
            if (!seat.inReach(table.get())) {
                tell(player, label + " is too far from the selected table. Keep tables within " + Seat.TABLE_RADIUS + " blocks of their seats.");
                return;
            }
            link(seat, table.get());
            tell(player, label + " sits at the selected table. Click more chairs, or pick another table in Tables mode.");
            return;
        }
        edit(stack, t -> t.putString("last", "seat"));
        tell(player, existing.isEmpty()
                ? label + " added. Now switch to Tables mode and click the table in front of it."
                : seat.table.isEmpty() ? label + " selected. It needs a table: switch to Tables mode and click one." : label + " selected.");
    }

    private static void clickTable(ServerPlayer player, ItemStack stack, Restaurant r, BlockPos pos) {
        if (player.isShiftKeyDown()) {
            int unlinked = 0;
            for (Seat s : r.seats) if (s.table.remove(pos)) unlinked++;
            edit(stack, t -> { if (t.getLong("table") == pos.asLong()) t.remove("table"); });
            tell(player, unlinked > 0 ? "Table removed from " + unlinked + (unlinked == 1 ? " seat." : " seats.") : "That isn't a table. Sneak-click a table to remove it.");
            return;
        }
        if (r.seatAt(pos).isPresent()) {
            tell(player, "That block is a seat. Pick a table block instead.");
            return;
        }
        if (!inside(player, r, pos)) return;
        Seat seat = "seat".equals(last(stack)) ? stored(stack, "seat").flatMap(r::seatAt).orElse(null) : null;
        if (seat != null) {
            if (!seat.inReach(pos)) {
                tell(player, "That table is too far from seat " + r.seatLabels().get(seat.pos) + ". Keep it within " + Seat.TABLE_RADIUS + " blocks.");
                return;
            }
            if (!player.serverLevel().getBlockState(pos.above()).canBeReplaced()) {
                tell(player, "There's something on top of that table, so food has nowhere to go. Clear the top first.");
                return;
            }
            link(seat, pos);
            edit(stack, t -> { t.putLong("table", pos.asLong()); t.putString("last", "table"); });
            tell(player, "Seat " + r.seatLabels().get(seat.pos) + " now sits at this table. It's selected: click more chairs in Seats mode to add them.");
            return;
        }
        edit(stack, t -> { t.putLong("table", pos.asLong()); t.putString("last", "table"); });
        tell(player, "Table selected. Switch to Seats mode and click each chair that sits at it.");
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static void link(Seat seat, BlockPos table) {
        seat.table.clear();
        seat.table.add(table.immutable());
    }

    private static String last(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString("last");
    }

    private static boolean inside(ServerPlayer player, Restaurant r, BlockPos pos) {
        if (r.contains(pos)) return true;
        tell(player, "That block is outside the restaurant's bounds.");
        return false;
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    public static void showMarkers(ServerPlayer player, ItemStack stack, Restaurant r) {
        ServerLevel level = player.serverLevel();
        BlockPos selected = "seat".equals(last(stack)) ? stored(stack, "seat").orElse(null) : null;
        BlockPos selectedTable = "table".equals(last(stack)) ? stored(stack, "table").orElse(null) : null;
        for (Seat s : r.seats) {
            dot(player, level, s.pos, s.pos.equals(selected) ? SELECTED : SEAT, 1.15);
            for (BlockPos t : s.table) dot(player, level, t, t.equals(selectedTable) ? SELECTED : TABLE, 1.1);

            if (s.table.isEmpty()) {
                for (double y : new double[]{1.5, 1.7, 1.9, 2.1}) dot(player, level, s.pos, WARNING, y);
                dot(player, level, s.pos, WARNING, 1.25);
            }
        }
        if (selectedTable != null && r.seats.stream().noneMatch(s -> s.table.contains(selectedTable))) dot(player, level, selectedTable, SELECTED, 1.1);
        r.counter.forEach(p -> dot(player, level, p, COUNTER, 1.1));
        if (r.entrance != null) dot(player, level, r.entrance, ENTRANCE, 0.2);
        for (int i = 0; i < r.waitingLine.size(); i++) {
            BlockPos p = r.waitingLine.get(i);

            for (int k = 0; k <= (i == 0 ? 2 : 0); k++) dot(player, level, p, LINE, 0.2 + k * 0.4);
        }
        if (mode(stack) == PlannerMode.BOUNDS) {
            BlockPos a = r.min, b = r.max.offset(1, 1, 1);
            for (int x : new int[]{a.getX(), b.getX()}) for (int y : new int[]{a.getY(), b.getY()}) for (int z : new int[]{a.getZ(), b.getZ()}) {
                level.sendParticles(player, BOUNDS, true, x, y, z, 2, 0, 0, 0, 0);
            }
        }
    }

    private static void dot(ServerPlayer player, ServerLevel level, BlockPos pos, DustParticleOptions colour, double y) {
        if (pos.distSqr(player.blockPosition()) > 32 * 32) return;
        level.sendParticles(player, colour, true, pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Mode: " + mode(stack).label).withStyle(net.minecraft.ChatFormatting.GOLD));
        tooltip.add(Component.literal(linked(stack).isPresent() ? "Linked" : "Sneak-right-click a cash register to link")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
