package com.ladleandlantern.core.block.entity;

import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class ServedPlateBlockEntity extends SyncedBlockEntity {
    public static final int CAPACITY = 4;
    private final List<ItemStack> food = new ArrayList<>();

    public ServedPlateBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypeRegistry.SERVED_PLATE.get(), pos, state);
    }

    public List<ItemStack> food() {
        return food;
    }

    public boolean hasRoom() {
        return food.size() < CAPACITY;
    }

    public void add(ItemStack stack) {
        if (hasRoom()) food.add(stack.copyWithCount(1));
        sync();
    }

    public ItemStack removeAt(int index) {
        ItemStack out = index < food.size() ? food.remove(index) : ItemStack.EMPTY;
        sync();
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        food.forEach(s -> list.add(s.save(registries)));
        tag.put("dishes", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        food.clear();
        for (Tag e : tag.getList("dishes", Tag.TAG_COMPOUND)) ItemStack.parse(registries, e).ifPresent(food::add);
    }
}
