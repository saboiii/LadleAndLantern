package com.ladleandlantern.core.block;

import com.ladleandlantern.core.restaurant.Fixture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public abstract class WallMountedBlock extends FacingBlock {
    @Nullable
    private final Fixture kind;
    private final VoxelShape[] shapes;

    protected WallMountedBlock(Properties props, @Nullable Fixture kind, double x0, double y0, double x1, double y1, double depth) {
        super(props);
        this.kind = kind;
        shapes = new VoxelShape[]{
                box(x0, y0, 16 - depth, x1, y1, 16),
                box(16 - x1, y0, 0, 16 - x0, y1, depth),
                box(16 - depth, y0, 16 - x1, 16, y1, 16 - x0),
                box(0, y0, x0, depth, y1, x1)};
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) return null;
        BlockState state = defaultBlockState().setValue(FACING, face);
        return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos behind = pos.relative(facing.getOpposite());
        return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> shapes[1];
            case WEST -> shapes[2];
            case EAST -> shapes[3];
            default -> shapes[0];
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (kind != null) Fixtures.register(level, pos, kind);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (kind != null && !state.is(newState.getBlock())) Fixtures.unregister(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
