package com.ladleandlantern.core.block;

import com.ladleandlantern.core.item.MenuCardItem;
import com.ladleandlantern.core.registry.ItemRegistry;

import com.ladleandlantern.core.restaurant.RestaurantStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TableCrumbsBlock extends Block {
    public static final BooleanProperty DIRTY = BooleanProperty.create("dirty");
    public static final BooleanProperty MENU = BooleanProperty.create("menu");

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1.2, 14);

    public TableCrumbsBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(DIRTY, true).setValue(MENU, false).setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRTY, MENU, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        if (level instanceof ServerLevel server) {
            RestaurantStore store = RestaurantStore.get(server.getServer());
            var r = store.at(level.dimension(), pos).orElse(null);
            boolean card = state.getValue(MENU) || r != null && r.tableCards.contains(pos);
            if (card) {
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(com.ladleandlantern.core.registry.ItemRegistry.MENU_CARD.get());
                if (r != null) com.ladleandlantern.core.item.MenuCardItem.write(stack, r, store.day());
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, stack);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            RestaurantStore store = RestaurantStore.get(server.getServer());

            store.all().forEach(r -> {
                boolean changed = r.dirtyTables.remove(pos) | r.tableCards.remove(pos);
                if (changed) store.markDirty();
            });
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
