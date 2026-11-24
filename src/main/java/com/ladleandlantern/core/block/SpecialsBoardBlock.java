package com.ladleandlantern.core.block;

import com.ladleandlantern.core.block.entity.SyncedBlockEntity;

import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import com.ladleandlantern.core.restaurant.Restaurant;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SpecialsBoardBlock extends FacingBlock implements EntityBlock {
    public static final MapCodec<SpecialsBoardBlock> CODEC = simpleCodec(SpecialsBoardBlock::new);
    private static final VoxelShape NS = box(1, 0, 3, 15, 15, 13);
    private static final VoxelShape EW = box(3, 0, 1, 13, 15, 15);

    public SpecialsBoardBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoardEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!FoodRules.isFood(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof BoardEntity board) {
            RestaurantStore store = RestaurantStore.get(server.getServer());
            Restaurant r = store.near(level.dimension(), pos, Restaurant.SIGN_RANGE).orElse(null);
            if (r == null) {
                player.displayClientMessage(Component.literal("Put the board within " + Restaurant.SIGN_RANGE + " blocks of your restaurant."), true);
            } else {
                r.special = BuiltInRegistries.ITEM.getKey(stack.getItem());
                r.signboard = pos.immutable();
                board.setDish(stack.copyWithCount(1));
                store.markDirty();
                player.displayClientMessage(Component.literal("Today's special: " + stack.getHoverName().getString()).withStyle(ChatFormatting.GOLD), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof BoardEntity board) {
            board.setDish(ItemStack.EMPTY);
            RestaurantStore store = RestaurantStore.get(server.getServer());
            store.near(level.dimension(), pos, Restaurant.SIGN_RANGE).filter(r -> pos.equals(r.signboard)).ifPresent(r -> {
                r.special = null;
                store.markDirty();
            });
            player.displayClientMessage(Component.literal("Special cleared."), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static class BoardEntity extends SyncedBlockEntity {
        private ItemStack dish = ItemStack.EMPTY;

        public BoardEntity(BlockPos pos, BlockState state) {
            super(BlockEntityTypeRegistry.SPECIALS_BOARD.get(), pos, state);
        }

        public ItemStack dish() {
            return dish;
        }

        public void setDish(ItemStack stack) {
            dish = stack;
            sync();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (!dish.isEmpty()) tag.put("dish", dish.save(registries));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            dish = tag.contains("dish") ? ItemStack.parseOptional(registries, tag.getCompound("dish")) : ItemStack.EMPTY;
        }
    }
}
