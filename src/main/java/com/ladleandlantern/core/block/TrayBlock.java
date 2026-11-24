package com.ladleandlantern.core.block;

import com.ladleandlantern.core.block.entity.TrayBlockEntity;

import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TrayBlock extends BaseEntityBlock {
    public static final MapCodec<TrayBlock> CODEC = simpleCodec(TrayBlock::new);
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 1, 15);

    public TrayBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrayBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!FoodRules.isFood(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrayBlockEntity tray) {
            ItemStack copy = player.getAbilities().instabuild ? stack.copy() : stack;
            tray.insert(copy);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && level.getBlockEntity(pos) instanceof TrayBlockEntity tray) {
            sp.openMenu(tray);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel server) {
            RestaurantStore store = RestaurantStore.get(server.getServer());
            store.at(level.dimension(), pos).ifPresent(r -> {
                r.trays.add(pos.immutable());
                r.layoutChanged();
                store.markDirty();
            });
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel server) {
                RestaurantStore store = RestaurantStore.get(server.getServer());
                store.all().forEach(r -> { if (r.trays.remove(pos)) r.layoutChanged(); });
                store.markDirty();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
