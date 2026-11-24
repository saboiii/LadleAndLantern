package com.ladleandlantern.core.block;

import com.ladleandlantern.core.restaurant.Fixture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class FixtureBlock extends FacingBlock {
    private final Fixture kind;
    private final VoxelShape shape;

    protected FixtureBlock(Properties props, Fixture kind, VoxelShape shape) {
        super(props);
        this.kind = kind;
        this.shape = shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Fixtures.register(level, pos, kind);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) Fixtures.unregister(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
