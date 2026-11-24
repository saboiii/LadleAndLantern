package com.ladleandlantern.core.block.entity;

import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import com.ladleandlantern.core.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class BagRackBlockEntity extends SmallStorageBlockEntity {
    public BagRackBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypeRegistry.BAG_RACK.get(), pos, state);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(ItemRegistry.TO_GO_BAG.get());
    }
}
