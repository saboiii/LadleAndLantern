package com.ladleandlantern.core.food;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.block.ServedPlateBlock;
import com.ladleandlantern.core.block.entity.ServedPlateBlockEntity;
import com.ladleandlantern.core.registry.BlockRegistry;
import com.ladleandlantern.core.restaurant.Seat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class TableService {
    public record Found(ItemStack stack, BlockPos table, Runnable consume) {}

    private TableService() {}

    public static List<Found> scan(ServerLevel level, Seat seat) {
        return scanTables(level, seat.table);
    }

    public static List<Found> scanTables(ServerLevel level, java.util.Collection<BlockPos> tables) {
        List<Found> out = new ArrayList<>();
        if (tables.isEmpty()) return out;
        AABB area = null;
        for (BlockPos table : tables) {
            BlockPos above = table.above();
            area = area == null ? new AABB(above) : area.minmax(new AABB(above));
            BlockState state = level.getBlockState(above);
            if (state.is(BlockRegistry.SERVED_PLATE.get())) {
                if (level.getBlockEntity(above) instanceof ServedPlateBlockEntity plate) {
                    for (ItemStack dish : plate.food()) {
                        ItemStack one = dish.copyWithCount(1);
                        out.add(new Found(one, table, () -> ServedPlateBlock.consume(level, above, one)));
                    }
                }
            } else if (!state.isAir()) {
                ItemStack picked = pickBlock(level, above, state);
                if (FoodRules.isFood(picked)) out.add(new Found(picked.copyWithCount(1), table, () -> level.removeBlock(above, false)));
            }
        }

        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area.inflate(0.2))) {
            if (!FoodRules.isFood(e.getItem())) continue;
            BlockPos under = e.blockPosition().below();
            BlockPos table = tables.contains(under) ? under : tables.iterator().next();
            for (int i = 0; i < e.getItem().getCount(); i++) {
                out.add(new Found(e.getItem().copyWithCount(1), table, () -> {
                    ItemStack s = e.getItem().copy();
                    s.shrink(1);
                    if (s.isEmpty()) e.discard();
                    else e.setItem(s);
                }));
            }
        }
        return out;
    }

    private static ItemStack pickBlock(ServerLevel level, BlockPos pos, BlockState state) {
        try {
            return state.getBlock().getCloneItemStack(level, pos, state);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    public static boolean serveDish(ServerPlayer player, Seat seat, List<Seat> allSeats, ItemStack held, int dishesStillToCome) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> free = freeTableBlocks(level, seat, allSeats);
        if (!free.isEmpty() && free.size() >= dishesStillToCome && placeWithOwnMod(player, level, free.get(0), held)) return true;
        return addToPlate(level, seat, free, held);
    }

    public static List<ItemStack> plateDishes(ServerLevel level, Seat seat, List<Seat> allSeats, List<ItemStack> dishes) {
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack dish : units(dishes)) {
            if (!addToPlate(level, seat, freeTableBlocks(level, seat, allSeats), dish)) leftover.add(dish);
        }
        return leftover;
    }

    private static boolean addToPlate(ServerLevel level, Seat seat, List<BlockPos> free, ItemStack dish) {
        for (BlockPos table : seat.table) {
            if (level.getBlockEntity(table.above()) instanceof ServedPlateBlockEntity plate && plate.hasRoom()) {
                plate.add(dish);
                return true;
            }
        }
        if (free.isEmpty()) return false;
        BlockPos at = free.get(0).above();
        level.setBlock(at, com.ladleandlantern.core.block.ServedPlateBlock.facing(at, seat.pos), Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof ServedPlateBlockEntity plate) plate.add(dish);
        return true;
    }

    private static List<BlockPos> freeTableBlocks(ServerLevel level, Seat seat, List<Seat> allSeats) {
        List<BlockPos> own = new ArrayList<>(), shared = new ArrayList<>();
        for (BlockPos table : seat.table) {
            if (level.getBlockState(table).isAir() || !level.getBlockState(table.above()).canBeReplaced()) continue;
            boolean isShared = allSeats.stream().anyMatch(o -> o != seat && o.table.contains(table));
            (isShared ? shared : own).add(table);
        }
        own.addAll(shared);
        return own;
    }

    private static boolean placeWithOwnMod(ServerPlayer player, ServerLevel level, BlockPos table, ItemStack held) {
        BlockPos above = table.above();
        BlockState before = level.getBlockState(above);
        ItemStack original = player.getMainHandItem();
        ItemStack one = held.copyWithCount(1);
        boolean sneaking = player.isShiftKeyDown();
        Pose pose = player.getPose();
        player.setItemInHand(InteractionHand.MAIN_HAND, one);

        player.setShiftKeyDown(true);
        player.setPose(Pose.CROUCHING);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(table).add(0, 0.5, 0), Direction.UP, table, false);
            player.gameMode.useItemOn(player, level, one, InteractionHand.MAIN_HAND, hit);
        } catch (RuntimeException e) {
            LadleAndLantern.LOGGER.debug("Placing {} with its own mod failed", held, e);
        } finally {
            player.setPose(pose);
            player.setShiftKeyDown(sneaking);
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        }
        BlockState after = level.getBlockState(above);
        if (after == before || after.isAir()) return false;
        if (!FoodRules.isFood(pickBlock(level, above, after))) {
            level.setBlock(above, before, Block.UPDATE_ALL);
            return false;
        }
        return true;
    }

    public static double sitHeight(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return pos.getY();
        double lowest = Double.MAX_VALUE;
        for (double x = 0.2; x < 0.85; x += 0.2) {
            for (double z = 0.2; z < 0.85; z += 0.2) {
                Vec3 from = new Vec3(pos.getX() + x, pos.getY() + 2.0, pos.getZ() + z);
                Vec3 to = new Vec3(pos.getX() + x, pos.getY() - 0.01, pos.getZ() + z);
                BlockHitResult hit = shape.clip(from, to, pos);
                if (hit != null) lowest = Math.min(lowest, hit.getLocation().y);
            }
        }
        return lowest == Double.MAX_VALUE ? pos.getY() + shape.max(Direction.Axis.Y) : lowest;
    }

    public static List<ItemStack> units(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : stacks) for (int i = 0; i < s.getCount(); i++) out.add(s.copyWithCount(1));
        return out;
    }
}
