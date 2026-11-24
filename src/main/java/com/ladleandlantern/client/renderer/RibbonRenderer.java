package com.ladleandlantern.client.renderer;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.block.RibbonBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

public class RibbonRenderer implements BlockEntityRenderer<RibbonBlock.RibbonBlockEntity> {
    private static final ResourceLocation TEXTURE = LadleAndLantern.id("textures/item/blue_ribbon.png");

    public RibbonRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(RibbonBlock.RibbonBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        long pos = be.getBlockPos().asLong();
        float time = (be.getLevel().getGameTime() + partialTick + (pos % 100)) * 0.06f;

        float wave = (float) Math.sin(time) * 0.7f + (float) Math.sin(time * 2.3f) * 0.3f;
        float sway = 4f + 3.5f * (wave + 1f);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - be.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot()));

        pose.translate(0, 15.5 / 16.0, 7.2 / 16.0);
        pose.mulPose(Axis.XP.rotationDegrees(sway));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f m = pose.last().pose();
        float w = 6f / 16, h = 15f / 16;
        vertex(vc, pose, m, -w, 0, 1, 0, light, overlay);
        vertex(vc, pose, m, w, 0, 0, 0, light, overlay);
        vertex(vc, pose, m, w, -h, 0, 1, light, overlay);
        vertex(vc, pose, m, -w, -h, 1, 1, light, overlay);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, Matrix4f m, float x, float y, float u, float v, int light, int overlay) {
        vc.addVertex(m, x, y, 0).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(pose.last(), 0, 0, -1);
    }
}
