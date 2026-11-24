package com.ladleandlantern.core.block;

import com.ladleandlantern.core.registry.SoundEventRegistry;

import com.ladleandlantern.core.restaurant.Fixture;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class WaterStationBlock extends FixtureBlock {
    public static final int MAX_GLASSES = 4;
    public static final IntegerProperty GLASSES = IntegerProperty.create("glasses", 0, MAX_GLASSES);
    public static final MapCodec<WaterStationBlock> CODEC = simpleCodec(WaterStationBlock::new);

    public WaterStationBlock(Properties props) {
        super(props, Fixture.WATER_STATION, box(1, 0, 3, 15, 10, 13));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GLASSES, MAX_GLASSES));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GLASSES);
    }

    public static int glasses(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(GLASSES) ? state.getValue(GLASSES) : 0;
    }

    public static boolean useGlass(ServerLevel level, BlockPos pos) {
        int left = glasses(level, pos);
        if (left <= 0) return false;
        level.setBlock(pos, level.getBlockState(pos).setValue(GLASSES, left - 1), Block.UPDATE_ALL);
        level.playSound(null, pos, com.ladleandlantern.core.registry.SoundEventRegistry.WATER_POUR.get(), SoundSource.BLOCKS, 0.6f, 1f);
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.GLASS_BOTTLE) || state.getValue(GLASSES) >= MAX_GLASSES) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(GLASSES, state.getValue(GLASSES) + 1), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.6f, 1.3f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
