package com.ladleandlantern.core.block;

import com.ladleandlantern.core.block.entity.MenuStandBlockEntity;

import com.ladleandlantern.core.registry.ItemRegistry;
import com.ladleandlantern.core.restaurant.Fixture;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class MenuStandBlock extends FixtureBlock implements EntityBlock {
    public static final MapCodec<MenuStandBlock> CODEC = simpleCodec(MenuStandBlock::new);

    public static final net.minecraft.world.level.block.state.properties.IntegerProperty CARDS =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("cards", 0, 3);

    public MenuStandBlock(Properties props) {
        super(props, Fixture.MENU_STAND, box(3, 0, 3, 13, 15, 13));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CARDS, 0));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CARDS, Finish.PROPERTY);
    }

    public static int cardsLevel(int count) {
        return count <= 0 ? 0 : count == 1 ? 1 : count < 8 ? 2 : 3;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MenuStandBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        Finish finish = Finish.fromDye(stack);
        if (finish != null) return refinish(stack, state, level, pos, player, finish);
        if (!stack.is(ItemRegistry.MENU_CARD.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MenuStandBlockEntity stand) stand.insert(stack);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    static ItemInteractionResult refinish(ItemStack dye, BlockState state, Level level, BlockPos pos, Player player, Finish finish) {
        if (state.getValue(Finish.PROPERTY) == finish) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(Finish.PROPERTY, finish), 3);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.DYE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
            if (!player.getAbilities().instabuild) dye.shrink(1);
            player.displayClientMessage(Component.literal(finish.label + " finish"), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof MenuStandBlockEntity stand) {
            sp.openMenu(stand);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
