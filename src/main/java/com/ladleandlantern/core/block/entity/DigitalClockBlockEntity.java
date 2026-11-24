package com.ladleandlantern.core.block.entity;

import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DigitalClockBlockEntity extends BlockEntity {
    public DigitalClockBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypeRegistry.DIGITAL_CLOCK.get(), pos, state);
    }
}
