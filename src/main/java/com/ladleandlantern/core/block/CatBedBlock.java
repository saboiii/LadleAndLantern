package com.ladleandlantern.core.block;

import com.ladleandlantern.core.restaurant.Fixture;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class CatBedBlock extends FixtureBlock {
    public static final MapCodec<CatBedBlock> CODEC = simpleCodec(CatBedBlock::new);

    public CatBedBlock(Properties props) {
        super(props, Fixture.CAT_BED, box(1, 0, 1, 15, 3, 15));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
