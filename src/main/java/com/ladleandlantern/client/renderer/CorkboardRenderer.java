package com.ladleandlantern.client.renderer;

import com.ladleandlantern.core.block.CorkboardBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.List;

public class CorkboardRenderer implements BlockEntityRenderer<CorkboardBlock.Board> {
    private final ItemRenderer items;

    public CorkboardRenderer(BlockEntityRendererProvider.Context ctx) {
        items = ctx.getItemRenderer();
    }

    @Override
    public void render(CorkboardBlock.Board be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        List<ItemStack> notes = be.items();
        long seed = be.getBlockPos().asLong();
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - be.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot()));

        pose.translate(0, 0, 0.5 - 1.2 / 16);
        for (int i = 0; i < notes.size(); i++) {
            ItemStack note = notes.get(i);
            if (note.isEmpty()) continue;
            pose.pushPose();
            pose.translate((i % 3 - 1) * 0.28, (1 - i / 3) * 0.27, -0.001 * (i + 1));
            pose.mulPose(Axis.ZP.rotationDegrees((float) (((seed + i * 31L) % 21) - 10)));
            pose.scale(0.3f, 0.3f, 0.3f);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            items.renderStatic(note, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), (int) seed + i);
            pose.popPose();
        }
        pose.popPose();
    }
}
