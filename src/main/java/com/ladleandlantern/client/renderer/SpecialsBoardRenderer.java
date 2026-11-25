package com.ladleandlantern.client.renderer;

import com.ladleandlantern.core.block.SpecialsBoardBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class SpecialsBoardRenderer implements BlockEntityRenderer<SpecialsBoardBlock.BoardEntity> {
    private static final float TILT = 22.5f;
    private final ItemRenderer items;
    private final Font font;

    public SpecialsBoardRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
        this.font = ctx.getFont();
    }

    @Override
    public void render(SpecialsBoardBlock.BoardEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        com.mojang.blaze3d.vertex.PoseStack.Pose start = pose.last();
        try {
            draw(be, partialTick, pose, buffers, light, overlay);
        } catch (Exception e) {
            while (pose.last() != start && !pose.clear()) pose.popPose();
            com.ladleandlantern.core.util.Guard.report(getClass().getSimpleName(), e);
        }
    }

    private void draw(SpecialsBoardBlock.BoardEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack dish = be.dish();
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - be.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot() + side * 180));

            pose.translate(0, 0, 3.5 / 16 - 0.5);
            pose.mulPose(Axis.XP.rotationDegrees(TILT));
            pose.translate(0, 0, -0.07);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            text(pose, buffers, "TODAY'S", 0.86f, 0.0085f);
            text(pose, buffers, "SPECIAL", 0.78f, 0.0085f);
            if (!dish.isEmpty()) {
                pose.pushPose();
                pose.translate(0, 0.47, 0.01);
                pose.scale(0.34f, 0.34f, 0.01f);
                items.renderStatic(dish, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, overlay, pose, buffers, be.getLevel(), 0);
                pose.popPose();
                String name = dish.getHoverName().getString();
                text(pose, buffers, name.length() > 18 ? name.substring(0, 17) + "." : name, 0.2f, 0.0065f);
            } else {
                text(pose, buffers, "ask inside", 0.47f, 0.007f);
            }
            pose.popPose();
        }
    }

    private void text(PoseStack pose, MultiBufferSource buffers, String s, float y, float scale) {
        pose.pushPose();
        pose.translate(0, y, 0.012);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(s, -font.width(s) / 2f, 0, 0xFFF2F2E6, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
