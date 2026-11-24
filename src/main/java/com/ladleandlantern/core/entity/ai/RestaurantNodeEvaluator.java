package com.ladleandlantern.core.entity.ai;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.entity.RestaurantNpc;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

public class RestaurantNodeEvaluator extends WalkNodeEvaluator {
    private LongSet noWalk;
    private LongSet occupied;
    private AABB bounds;

    private long start, startBelow;

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        start = mob.blockPosition().asLong();
        startBelow = mob.blockPosition().below().asLong();
        if (mob instanceof RestaurantNpc npc) {
            noWalk = npc.noWalkBlocks();
            bounds = npc.pathBounds();
            occupied = new LongOpenHashSet();
            for (RestaurantNpc other : npc.level().getEntitiesOfClass(RestaurantNpc.class, mob.getBoundingBox().inflate(12),
                    o -> o != npc && o.solidToOthers() && !o.isPassenger())) {
                occupied.add(other.blockPosition().asLong());
            }
        }
    }

    @Override
    public void done() {
        super.done();
        noWalk = null;
        occupied = null;
        bounds = null;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        long here = BlockPos.asLong(x, y, z);
        if (here == start || here == startBelow) return super.getPathType(context, x, y, z);
        if (bounds != null && !bounds.contains(x + 0.5, y + 0.5, z + 0.5)) return PathType.BLOCKED;
        if (noWalk != null && (noWalk.contains(BlockPos.asLong(x, y, z)) || noWalk.contains(BlockPos.asLong(x, y - 1, z)))) {
            return PathType.BLOCKED;
        }
        if (occupied != null && occupied.contains(BlockPos.asLong(x, y, z))) return PathType.BLOCKED;
        BlockState state = context.getBlockState(new BlockPos(x, y, z));
        if (ours(state) || ours(context.getBlockState(new BlockPos(x, y - 1, z)))) return PathType.BLOCKED;
        if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) return PathType.DOOR_WOOD_CLOSED;
        return super.getPathType(context, x, y, z);
    }

    private static boolean ours(BlockState state) {
        return !state.isAir() && LadleAndLantern.MODID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }
}
