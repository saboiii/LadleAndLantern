package com.ladleandlantern.core.block;

import com.ladleandlantern.core.block.entity.ServedPlateBlockEntity;
import com.ladleandlantern.core.registry.BlockRegistry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ServedPlateBlock extends BaseEntityBlock {
    public static final MapCodec<ServedPlateBlock> CODEC = simpleCodec(ServedPlateBlock::new);

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE_NS = box(0.5, 0, 3, 15.5, 1.4, 13);
    private static final VoxelShape SHAPE_EW = box(3, 0, 0.5, 13, 1.4, 15.5);

    public ServedPlateBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public static BlockState facing(BlockPos plate, BlockPos seat) {
        int dx = seat.getX() - plate.getX(), dz = seat.getZ() - plate.getZ();
        net.minecraft.core.Direction d = Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
                : (dz > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
        return com.ladleandlantern.core.registry.BlockRegistry.SERVED_PLATE.get().defaultBlockState().setValue(FACING, d);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == net.minecraft.core.Direction.Axis.X ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServedPlateBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ServedPlateBlockEntity plate) {
            ItemStack food = plate.food().isEmpty() ? ItemStack.EMPTY : plate.removeAt(plate.food().size() - 1);
            if (!food.isEmpty() && !player.addItem(food)) player.drop(food, false);
            if (plate.food().isEmpty()) level.removeBlock(pos, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void consume(Level level, BlockPos pos, ItemStack dish) {
        if (!(level.getBlockEntity(pos) instanceof ServedPlateBlockEntity plate)) return;
        for (int i = 0; i < plate.food().size(); i++) {
            if (ItemStack.isSameItem(plate.food().get(i), dish)) {
                plate.removeAt(i);
                break;
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ServedPlateBlockEntity plate) {
            plate.food().forEach(f -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, f));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
