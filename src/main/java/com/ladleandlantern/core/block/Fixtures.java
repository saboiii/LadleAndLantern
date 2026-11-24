package com.ladleandlantern.core.block;

import com.ladleandlantern.core.entity.CustomerEntity;
import com.ladleandlantern.core.restaurant.Fixture;
import com.ladleandlantern.core.restaurant.Restaurant;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.ladleandlantern.core.world.Weather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class Fixtures {
    private static final int REACH = 4;

    private Fixtures() {}

    public static void register(Level level, BlockPos pos, Fixture kind) {
        if (!(level instanceof ServerLevel server)) return;
        RestaurantStore store = RestaurantStore.get(server.getServer());
        store.near(level.dimension(), pos, REACH).ifPresent(r -> {
            r.fixtures.put(pos.immutable(), kind);
            r.layoutChanged();
            store.markDirty();
        });
    }

    public static void unregister(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        RestaurantStore store = RestaurantStore.get(server.getServer());
        for (Restaurant r : store.all()) {
            if (r.fixtures.remove(pos) == null) continue;
            r.layoutChanged();
            store.markDirty();
        }
    }

    public static BlockPos front(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(FixtureBlock.FACING) ? pos.relative(state.getValue(FixtureBlock.FACING)) : pos.north();
    }

    public static boolean musicPlaying(ServerLevel level, Restaurant r) {
        for (BlockPos p : r.fixtures(Fixture.GRAMOPHONE)) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof GramophoneBlock.GramophoneEntity g && g.playing()) return true;
        }
        return false;
    }

    public static void updateStands(ServerLevel level, Restaurant r, List<CustomerEntity> present, long day) {
        long inside = present.stream().filter(c -> c.state().atTable() || c.state() == CustomerEntity.State.READY).count();
        int fill = inside == 0 ? 0 : inside == 1 ? 1 : inside <= 3 ? 2 : 3;
        setFill(level, r.fixtures(Fixture.UMBRELLA_STAND), Weather.wet(level, r) ? fill : 0);
        setFill(level, r.fixtures(Fixture.COAT_RACK), Weather.cold(level, r, day) ? fill : 0);
    }

    private static void setFill(ServerLevel level, List<BlockPos> stands, int fill) {
        for (int i = 0; i < stands.size(); i++) {
            BlockPos p = stands.get(i);
            if (!level.isLoaded(p)) continue;
            BlockState state = level.getBlockState(p);
            if (!state.hasProperty(StandBlock.FILL)) continue;
            int mine = i == 0 ? fill : Math.max(0, fill - i);
            if (state.getValue(StandBlock.FILL) != mine) level.setBlock(p, state.setValue(StandBlock.FILL, mine), Block.UPDATE_ALL);
        }
    }

    public static void refillWater(ServerLevel level, Restaurant r) {
        for (BlockPos p : r.fixtures(Fixture.WATER_STATION)) {
            BlockState state = level.getBlockState(p);
            if (state.hasProperty(WaterStationBlock.GLASSES)) level.setBlock(p, state.setValue(WaterStationBlock.GLASSES, WaterStationBlock.MAX_GLASSES), Block.UPDATE_ALL);
        }
    }
}
