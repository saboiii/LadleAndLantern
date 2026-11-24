package com.ladleandlantern.core.block.entity;

import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class TrayBlockEntity extends SmallStorageBlockEntity {
    public TrayBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypeRegistry.TRAY.get(), pos, state);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return FoodRules.isFood(stack);
    }

    public List<ResourceLocation> missing(List<ResourceLocation> needed) {
        List<ResourceLocation> have = new ArrayList<>();
        for (ItemStack s : items) for (int i = 0; i < s.getCount(); i++) have.add(BuiltInRegistries.ITEM.getKey(s.getItem()));
        List<ResourceLocation> missing = new ArrayList<>();
        for (ResourceLocation id : needed) if (!have.remove(id)) missing.add(id);
        return missing;
    }

    public boolean containsAll(List<ResourceLocation> needed) {
        return missing(needed).isEmpty();
    }

    public List<ItemStack> extract(List<ResourceLocation> needed) {
        List<ItemStack> out = new ArrayList<>();
        for (ResourceLocation id : needed) {
            for (ItemStack s : items) {
                if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(id)) {
                    out.add(s.split(1));
                    break;
                }
            }
        }
        setChanged();
        return out;
    }
}
