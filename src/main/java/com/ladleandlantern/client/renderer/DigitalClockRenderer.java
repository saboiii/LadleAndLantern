package com.ladleandlantern.client.renderer;

import com.ladleandlantern.client.ClientPayloads;

import com.ladleandlantern.core.block.entity.DigitalClockBlockEntity;
import com.ladleandlantern.core.restaurant.Schedule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class DigitalClockRenderer implements BlockEntityRenderer<DigitalClockBlockEntity> {
    private static final int TIME_COLOUR = 0xFF7CFF8A, DAY_COLOUR = 0xFF3FA34D;
    private final Font font;

    public DigitalClockRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(DigitalClockBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        BlockState state = be.getBlockState();
        AttachFace face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        float centreY = face == AttachFace.FLOOR ? 3.5f / 16 : face == AttachFace.WALL ? 7.5f / 16 : 12.5f / 16;
        float frontZ = face == AttachFace.WALL ? 14f / 16 : 6f / 16;
        long dayTime = be.getLevel().getDayTime();

        pose.pushPose();
        pose.translate(0.5, centreY, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING).toYRot()));
        pose.translate(0, 0, frontZ - 0.5 - 0.004);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        drawCentred(pose, buffers, Schedule.clock((int) (dayTime % 24000L)), 0.0165f, -2.8f, TIME_COLOUR);
        String day = Schedule.DAY_NAMES[Schedule.weekday(dayTime / 24000L + ClientPayloads.dayOffset)].toUpperCase();
        if (!ClientPayloads.season.isEmpty()) day += "  " + ClientPayloads.season.toUpperCase();
        drawCentred(pose, buffers, day, 0.008f, 9.5f, DAY_COLOUR);
        pose.popPose();
    }

    private void drawCentred(PoseStack pose, MultiBufferSource buffers, String text, float scale, float yOffset, int colour) {
        pose.pushPose();
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, yOffset - font.lineHeight / 2f, colour, false,
                pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
