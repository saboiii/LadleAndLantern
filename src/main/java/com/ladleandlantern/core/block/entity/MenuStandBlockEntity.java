package com.ladleandlantern.core.block.entity;

import com.ladleandlantern.core.block.MenuStandBlock;

import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import com.ladleandlantern.core.registry.ItemRegistry;
import com.ladleandlantern.core.restaurant.Fixture;
import com.ladleandlantern.core.restaurant.Restaurant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class MenuStandBlockEntity extends SmallStorageBlockEntity {
    public MenuStandBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypeRegistry.MENU_STAND.get(), pos, state);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(ItemRegistry.MENU_CARD.get());
    }

    public boolean hasRoom() {
        return items.stream().anyMatch(ItemStack::isEmpty);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(MenuStandBlock.CARDS)) return;
        int want = MenuStandBlock.cardsLevel(count());
        if (state.getValue(MenuStandBlock.CARDS) != want) level.setBlock(worldPosition, state.setValue(MenuStandBlock.CARDS, want), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    @Nullable
    public static BlockPos find(ServerLevel level, Restaurant r) {
        for (BlockPos p : r.fixtures(Fixture.MENU_STAND)) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof MenuStandBlockEntity s && s.count() > 0) return p;
        }
        return null;
    }

    public static boolean putBack(ServerLevel level, Restaurant r, ItemStack card) {
        for (BlockPos p : r.fixtures(Fixture.MENU_STAND)) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof MenuStandBlockEntity s && s.insert(card) && card.isEmpty()) return true;
        }
        return card.isEmpty();
    }
}
