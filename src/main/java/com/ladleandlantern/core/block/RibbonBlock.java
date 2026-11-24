package com.ladleandlantern.core.block;

import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RibbonBlock extends WallMountedBlock implements EntityBlock {
    public static final MapCodec<RibbonBlock> CODEC = simpleCodec(RibbonBlock::new);

    public RibbonBlock(Properties props) {
        super(props, null, 3, 1, 13, 16, 1);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RibbonBlockEntity(pos, state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return true;
    }

    public static class RibbonBlockEntity extends BlockEntity {
        public RibbonBlockEntity(BlockPos pos, BlockState state) {
            super(BlockEntityTypeRegistry.RIBBON.get(), pos, state);
        }
    }
}
