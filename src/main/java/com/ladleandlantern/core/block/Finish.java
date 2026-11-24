package com.ladleandlantern.core.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;

public enum Finish implements StringRepresentable {
    COTTAGE("cottage", "Cottage"), RUSTIC("rustic", "Rustic"), MODERN("modern", "Modern");

    public static final EnumProperty<Finish> PROPERTY = EnumProperty.create("finish", Finish.class);

    private final String name;
    public final String label;

    Finish(String name, String label) {
        this.name = name;
        this.label = label;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Nullable
    public static Finish fromDye(ItemStack stack) {
        if (!(stack.getItem() instanceof DyeItem dye)) return null;
        DyeColor c = dye.getDyeColor();
        return switch (c) {
            case BROWN, BLACK, RED, ORANGE -> RUSTIC;
            case WHITE, GRAY, LIGHT_GRAY, CYAN, BLUE -> MODERN;
            default -> COTTAGE;
        };
    }
}
