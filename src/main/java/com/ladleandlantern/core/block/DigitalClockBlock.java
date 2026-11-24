package com.ladleandlantern.core.block;

import com.ladleandlantern.core.block.entity.DigitalClockBlockEntity;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DigitalClockBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<DigitalClockBlock> CODEC = simpleCodec(DigitalClockBlock::new);

    public DigitalClockBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state != null && state.getValue(FACE) != AttachFace.WALL) state = state.setValue(FACING, state.getValue(FACING).getOpposite());
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        AttachFace face = state.getValue(FACE);
        boolean alongX = state.getValue(FACING).getAxis() == Direction.Axis.X;
        if (face == AttachFace.WALL) {
            return switch (state.getValue(FACING)) {
                case NORTH -> box(3, 4, 14, 13, 11, 16);
                case SOUTH -> box(3, 4, 0, 13, 11, 2);
                case EAST -> box(0, 4, 3, 2, 11, 13);
                default -> box(14, 4, 3, 16, 11, 13);
            };
        }
        double y0 = face == AttachFace.FLOOR ? 0 : 9, y1 = face == AttachFace.FLOOR ? 7 : 16;
        return alongX ? box(6, y0, 3, 10, y1, 13) : box(3, y0, 6, 13, y1, 10);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DigitalClockBlockEntity(pos, state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
