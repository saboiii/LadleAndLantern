package com.ladleandlantern.core.block;

import com.ladleandlantern.core.restaurant.Fixture;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class StandBlock extends FixtureBlock {
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 3);
    public static final MapCodec<StandBlock> UMBRELLA_CODEC = simpleCodec(StandBlock::umbrella);
    public static final MapCodec<StandBlock> COAT_CODEC = simpleCodec(StandBlock::coat);
    private final boolean umbrellas;

    private StandBlock(Properties props, boolean umbrellas) {
        super(props, umbrellas ? Fixture.UMBRELLA_STAND : Fixture.COAT_RACK,
                umbrellas ? box(3, 0, 3, 13, 14, 13) : box(4, 0, 4, 12, 16, 12));
        this.umbrellas = umbrellas;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FILL, 0));
    }

    public static StandBlock umbrella(Properties props) {
        return new StandBlock(props, true);
    }

    public static StandBlock coat(Properties props) {
        return new StandBlock(props, false);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return umbrellas ? UMBRELLA_CODEC : COAT_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILL);
    }
}
