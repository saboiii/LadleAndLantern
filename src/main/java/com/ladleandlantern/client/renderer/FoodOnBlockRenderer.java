package com.ladleandlantern.client.renderer;

import com.ladleandlantern.core.block.ServedPlateBlock;
import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.restaurant.Course;

import com.ladleandlantern.core.block.entity.ServedPlateBlockEntity;
import com.ladleandlantern.core.block.entity.TrayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public final class FoodOnBlockRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final int MAX_LAYERS = 4;
    private final ItemRenderer items;

    public FoodOnBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        com.mojang.blaze3d.vertex.PoseStack.Pose start = pose.last();
        try {
            draw(be, partialTick, pose, buffers, light, overlay);
        } catch (Exception e) {
            while (pose.last() != start && !pose.clear()) pose.popPose();
            com.ladleandlantern.core.util.Guard.report(getClass().getSimpleName(), e);
        }
    }

    private void draw(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int seed = (int) be.getBlockPos().asLong();
        if (be instanceof TrayBlockEntity t) {
            List<ItemStack> stacks = t.items();
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                int layers = Math.min(MAX_LAYERS, stack.getCount());
                for (int layer = 0; layer < layers; layer++) {
                    draw(pose, buffers, light, overlay, stack, 0.22 + (i % 3) * 0.28, 0.075 + layer * 0.03, 0.22 + (i / 3) * 0.28,
                            0.28f, layer * 23f, seed + i * 7 + layer, be);
                }
            }
            return;
        }

        pose.pushPose();
        if (be.getBlockState().hasProperty(com.ladleandlantern.core.block.ServedPlateBlock.FACING)) {
            int quarter = (be.getBlockState().getValue(com.ladleandlantern.core.block.ServedPlateBlock.FACING).get2DDataValue() + 2) % 4;
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-90f * quarter));
            pose.translate(-0.5, 0, -0.5);
        }

        List<ItemStack> food = new java.util.ArrayList<>(), drinks = new java.util.ArrayList<>();
        for (ItemStack s : ((ServedPlateBlockEntity) be).food()) (isDrink(s) ? drinks : food).add(s);
        int n = food.size();
        for (int i = 0; i < n; i++) {
            double x = n == 1 ? 0.5 : 0.36 + (i % 2) * 0.28;
            double z = n <= 2 ? 0.5 : 0.38 + (i / 2) * 0.24;

            draw(pose, buffers, light, overlay, food.get(i), x, 0.06, z, n == 1 ? 0.46f : n == 2 ? 0.32f : 0.26f, i * 37f, seed + i, be);
        }
        for (int i = 0; i < drinks.size(); i++) {
            standUpright(pose, buffers, light, overlay, drinks.get(i), 0.12 + i * 0.13, 0.86 - i * 0.06, seed + 20 + i, be);
        }
        pose.popPose();
    }

    private static boolean isDrink(ItemStack stack) {
        return com.ladleandlantern.core.food.FoodRules.classify(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                == com.ladleandlantern.core.restaurant.Course.DRINK;
    }

    private void standUpright(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ItemStack stack, double x, double z, int seed, BlockEntity be) {
        float scale = 0.32f;
        pose.pushPose();
        pose.translate(x, scale * 0.5, z);
        pose.mulPose(Axis.YP.rotationDegrees(25));
        pose.scale(scale, scale, scale);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), seed);
        pose.popPose();
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ItemStack stack,
                      double x, double y, double z, float scale, float spin, int seed, BlockEntity be) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        pose.scale(scale, scale, scale);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), seed);
        pose.popPose();
    }
}
