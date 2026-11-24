package com.ladleandlantern.core.block;

import com.ladleandlantern.core.block.entity.SmallStorageBlockEntity;

import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import com.ladleandlantern.core.registry.ItemRegistry;
import com.ladleandlantern.core.restaurant.Fixture;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CorkboardBlock extends WallMountedBlock implements EntityBlock {
    public static final MapCodec<CorkboardBlock> CODEC = simpleCodec(CorkboardBlock::new);

    public CorkboardBlock(Properties props) {
        super(props, Fixture.CORKBOARD, 0, 1, 16, 15, 1);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Board(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ItemRegistry.THANK_YOU_NOTE.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof Board board) {
            ItemStack one = stack.copyWithCount(1);
            if (board.insert(one)) {
                stack.shrink(1);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.2f);
            } else {
                player.displayClientMessage(Component.literal("The board's full. Maybe it's time for a second one."), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof Board board) {
            if (sp.isShiftKeyDown() || board.isEmpty()) {
                sp.openMenu(board);
            } else {
                java.util.List<ItemStack> notes = board.items().stream().filter(s -> !s.isEmpty()).toList();
                ItemStack note = notes.get(level.random.nextInt(notes.size()));
                var lore = note.get(DataComponents.LORE);
                if (lore != null) lore.lines().forEach(sp::sendSystemMessage);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    public static class Board extends SmallStorageBlockEntity {
        public Board(BlockPos pos, BlockState state) {
            super(BlockEntityTypeRegistry.CORKBOARD.get(), pos, state);
        }

        @Override
        public boolean accepts(ItemStack stack) {
            return stack.is(ItemRegistry.THANK_YOU_NOTE.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
