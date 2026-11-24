package com.ladleandlantern.core.block;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.item.MenuCardItem;
import com.ladleandlantern.core.registry.ItemRegistry;
import com.ladleandlantern.core.registry.SoundEventRegistry;

import com.ladleandlantern.core.network.ServerScreens;
import com.ladleandlantern.core.restaurant.Restaurant;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CashierBlock extends FacingBlock {
    public static final MapCodec<CashierBlock> CODEC = simpleCodec(CashierBlock::new);

    public CashierBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public static Direction customerSide(Level level, BlockPos pos, @javax.annotation.Nullable BlockPos entrance) {
        BlockState state = level.getBlockState(pos);
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        if (entrance == null) return facing.getOpposite();
        double toward = pos.relative(facing, 2).distSqr(entrance);
        double away = pos.relative(facing.getOpposite(), 2).distSqr(entrance);
        return toward < away ? facing : facing.getOpposite();
    }

    private record QueueCache(long tick, java.util.List<BlockPos> spots) {}
    private static final java.util.Map<Long, QueueCache> QUEUES = new java.util.HashMap<>();

    public static void clearCaches() {
        QUEUES.clear();
    }

    public static java.util.List<BlockPos> queueSpots(Level level, Restaurant r, BlockPos cashier) {
        long now = level.getGameTime();
        QueueCache cached = QUEUES.get(cashier.asLong());
        if (cached != null && now - cached.tick() < 20 && now >= cached.tick()) return cached.spots();
        if (QUEUES.size() > 64) QUEUES.clear();
        java.util.List<BlockPos> spots = computeQueueSpots(level, r, cashier);
        QUEUES.put(cashier.asLong(), new QueueCache(now, spots));
        return spots;
    }

    private static java.util.List<BlockPos> computeQueueSpots(Level level, Restaurant r, BlockPos cashier) {
        Direction side = customerSide(level, cashier, r.entrance);
        java.util.List<BlockPos> spots = new java.util.ArrayList<>();
        for (int i = 1; i <= com.ladleandlantern.core.config.Config.MAX_QUEUE.get(); i++) {
            BlockPos p = cashier.relative(side, i);
            var here = level.getBlockState(p).getCollisionShape(level, p);

            boolean low = here.isEmpty() || here.max(Direction.Axis.Y) <= 0.2;
            boolean floor = !here.isEmpty() || !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
            boolean clear = low && floor
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && !r.noWalk().contains(p.asLong()) && !r.noWalk().contains(p.below().asLong());
            if (!clear) break;
            spots.add(p);
        }

        if (spots.isEmpty()) spots.add(cashier.relative(side));
        return spots;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!(level instanceof ServerLevel server) || !(placer instanceof ServerPlayer player)) return;
        RestaurantStore store = RestaurantStore.get(server.getServer());
        Restaurant r = store.at(level.dimension(), pos).orElse(null);
        if (r == null) {
            Restaurant nearby = store.all().stream().filter(o -> o.dimension.equals(level.dimension()) && o.nearBounds(pos, 16)).findFirst().orElse(null);
            r = store.create(level.dimension(), pos, player.getUUID());
            if (nearby != null) {
                player.sendSystemMessage(Component.literal("This cash register is outside " + nearby.name + "'s bounds, so it opened a separate restaurant. "
                        + "To add it to " + nearby.name + " instead, break it, widen " + nearby.name + "'s bounds with the planner, and place it again inside.")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            } else {
                player.sendSystemMessage(Component.literal("Opened a new restaurant. Sneak-right-click this cash register with a Restaurant Planner to set it up."));
            }
        } else {
            player.sendSystemMessage(Component.literal("Added another till to " + r.name + ". Takeout customers queue at whichever has the shorter line, "
                    + "and each hired cashier works the till they were hired at.").withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        if (!r.cashiers.contains(pos)) r.cashiers.add(pos.immutable());
        r.layoutChanged();
        if (queueSpots(server, r, pos).isEmpty()) {
            player.sendSystemMessage(Component.literal("There's no room in front of this cash register for a queue. Leave a few clear blocks on the customer side so takeout customers can line up.")
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        store.markDirty();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            RestaurantStore store = RestaurantStore.get(server.getServer());
            store.all().forEach(r -> { if (r.cashiers.remove(pos)) r.layoutChanged(); });
            store.markDirty();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() && stack.is(com.ladleandlantern.core.config.Config.currency())) {
            if (level instanceof ServerLevel server) {
                RestaurantStore.get(server.getServer()).byCashier(level.dimension(), pos).ifPresent(r -> {
                    r.wageFund += stack.getCount();
                    player.displayClientMessage(Component.literal("Added " + stack.getCount() + " to the wage fund. It now holds " + r.wageFund + "."), true);
                    stack.setCount(0);
                    level.playSound(null, pos, com.ladleandlantern.core.registry.SoundEventRegistry.CHA_CHING.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1f);
                    RestaurantStore.get(server.getServer()).markDirty();
                });
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!stack.is(com.ladleandlantern.core.registry.ItemRegistry.MENU_CARD.get())) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel server) {
            RestaurantStore store = RestaurantStore.get(server.getServer());
            store.byCashier(level.dimension(), pos).ifPresent(r -> {
                com.ladleandlantern.core.item.MenuCardItem.write(stack, r, store.day());
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.VILLAGER_WORK_CARTOGRAPHER, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1f);
                player.displayClientMessage(Component.literal("Wrote today's menu on " + stack.getCount() + " card" + (stack.getCount() == 1 ? "" : "s") + "."), true);
            });
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            RestaurantStore.get(server.getServer()).byCashier(level.dimension(), pos)
                    .ifPresentOrElse(r -> ServerScreens.openCashier(sp, r, pos),
                            () -> sp.displayClientMessage(Component.literal("This cash register isn't linked to a restaurant. Break it and place it again."), true));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
