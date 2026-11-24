package com.ladleandlantern.core.item;

import com.ladleandlantern.core.food.FoodRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class ToGoBagItem extends BundleItem {
    public ToGoBagItem(Properties props) {
        super(props);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack bag, Slot slot, ClickAction action, Player player) {
        ItemStack other = slot.getItem();
        if (!other.isEmpty() && !FoodRules.isFood(other)) return false;
        return super.overrideStackedOnOther(bag, slot, action, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack bag, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (!other.isEmpty() && !FoodRules.isFood(other)) return false;
        return super.overrideOtherStackedOnMe(bag, other, slot, action, player, access);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    public static List<ItemStack> contents(ItemStack bag) {
        List<ItemStack> out = new ArrayList<>();
        BundleContents contents = bag.get(DataComponents.BUNDLE_CONTENTS);
        if (contents != null) contents.items().forEach(s -> out.add(s.copy()));
        return out;
    }
}
