package com.ladleandlantern.client.renderer;

import com.ladleandlantern.client.ClientSkins;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.entity.CustomerEntity;
import com.ladleandlantern.core.entity.Emote;
import com.ladleandlantern.core.entity.RestaurantNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

public class NpcRenderer<T extends RestaurantNpc> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private static final ResourceLocation BUBBLES = LadleAndLantern.id("textures/gui/bubbles.png");
    private static final ResourceLocation RATING = LadleAndLantern.id("textures/gui/rating.png");
    private final PlayerModel<T> wide;
    private final PlayerModel<T> slim;

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        wide = model;
        slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = ClientSkins.get(entity.skin()).model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(entity, yaw, partialTick, pose, buffers, light);
        if (!shouldShowName(entity)) return;
        if (entity.ratingKind() != 0) ratingRow(entity, pose, buffers, entity.getBbHeight() + 0.25f, entity.tickCount + partialTick);
        float y = entity.getBbHeight() + 0.75f;
        if (entity instanceof CustomerEntity customer && customer.patience() > CustomerEntity.HIDDEN + 1) {
            patienceBar(customer.patience(), pose, buffers, y, entity.tickCount + partialTick);
            y += 0.2f;
        }
        bubble(entity, pose, buffers, y + 0.05f);
    }

    private static final int OUTLINE = 0xFFB8875A, FILL = 0xFFFFF7E8, TEXT = 0xFF7A5436;
    private static final int BAR_BG = 0xFFF3E6D3, MINT = 0xFF6FCF8A, PEACH = 0xFFFFD39B, PINK = 0xFFFFA8A8;
    private static final int MAX_LINE = 110;

    private Matrix4f face(PoseStack pose, float y) {
        pose.translate(0, y, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(0.025f, -0.025f, 0.025f);
        return pose.last().pose();
    }

    private void patienceBar(float patience, PoseStack pose, MultiBufferSource buffers, float y, float time) {
        pose.pushPose();
        Matrix4f m = face(pose, y);
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        int light = LightTexture.FULL_BRIGHT;
        float w = 16, h = 1.5f;

        rect(vc, m, -w, -h - 1, w, -h, OUTLINE, light);
        rect(vc, m, -w, h, w, h + 1, OUTLINE, light);
        rect(vc, m, -w - 1, -h, -w, h, OUTLINE, light);
        rect(vc, m, w, -h, w + 1, h, OUTLINE, light);
        float p = Math.max(0f, Math.min(1f, patience));
        int colour = patience < 0 ? PINK : p > 0.5f ? MINT : p > 0.2f ? PEACH : PINK;
        if (patience < 0 && ((int) (time / 10)) % 2 == 0) colour = 0xFFFFC4C4;
        float split = patience < 0 ? w : -w + 2 * w * p;
        if (split > -w) rect(vc, m, -w, -h, split, h, colour, light);
        if (split < w) rect(vc, m, split, -h, w, h, BAR_BG, light);
        pose.popPose();
    }

    private void bubble(T entity, PoseStack pose, MultiBufferSource buffers, float y) {
        String text = entity.speech();
        Emote emote = entity.emote();
        if (text.isEmpty() && emote.cell() < 0) return;
        Font font = getFont();
        int pad = 3;

        List<FormattedCharSequence> lines = text.isEmpty() ? List.of() : font.split(Component.literal(text), MAX_LINE);
        if (lines.size() > 4) lines = lines.subList(0, 4);
        int w = text.isEmpty() ? 12 : lines.stream().mapToInt(font::width).max().orElse(0);
        int h = text.isEmpty() ? 12 : lines.size() * 9 - 1;
        float x0 = -w / 2f - pad, x1 = w / 2f + pad, y1 = -6, y0 = y1 - h - pad * 2;
        int light = LightTexture.FULL_BRIGHT;

        pose.pushPose();
        Matrix4f m = face(pose, y);
        VertexConsumer box = buffers.getBuffer(RenderType.textBackground());

        rect(box, m, x0, y0, x1, y1, FILL, light);

        rect(box, m, x0, y0 - 2, x1, y0, OUTLINE, light);
        rect(box, m, x0 - 2, y0, x0, y1, OUTLINE, light);
        rect(box, m, x1, y0, x1 + 2, y1, OUTLINE, light);
        rect(box, m, x0, y1, -3, y1 + 2, OUTLINE, light);
        rect(box, m, 3, y1, x1, y1 + 2, OUTLINE, light);
        rect(box, m, x0 - 1, y0 - 1, x0, y0, OUTLINE, light);
        rect(box, m, x1, y0 - 1, x1 + 1, y0, OUTLINE, light);
        rect(box, m, x0 - 1, y1, x0, y1 + 1, OUTLINE, light);
        rect(box, m, x1, y1, x1 + 1, y1 + 1, OUTLINE, light);

        rect(box, m, -3, y1, 3, y1 + 1, FILL, light);
        rect(box, m, -3, y1 + 1, -2, y1 + 2, OUTLINE, light);
        rect(box, m, -2, y1 + 1, 2, y1 + 2, FILL, light);
        rect(box, m, 2, y1 + 1, 3, y1 + 2, OUTLINE, light);
        rect(box, m, -3, y1 + 2, -1, y1 + 3, OUTLINE, light);
        rect(box, m, -1, y1 + 2, 1, y1 + 3, FILL, light);
        rect(box, m, 1, y1 + 2, 3, y1 + 3, OUTLINE, light);
        rect(box, m, -2, y1 + 3, 2, y1 + 4, OUTLINE, light);
        rect(box, m, -1, y1 + 4, 1, y1 + 5, OUTLINE, light);
        if (!text.isEmpty()) {
            float ty = y0 + pad;
            for (FormattedCharSequence line : lines) {
                font.drawInBatch(line, -font.width(line) / 2f, ty, TEXT, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
                ty += 9;
            }
        } else {
            int cell = emote.cell();
            float u0 = (cell % 4) / 4f, v0 = (cell / 4) / 4f;
            VertexConsumer icon = buffers.getBuffer(RenderType.textPolygonOffset(BUBBLES));
            float ix0 = -6, iy0 = y0 + pad, ix1 = 6, iy1 = y0 + pad + 12;
            icon.addVertex(m, ix0, iy0, 0).setColor(-1).setUv(u0, v0).setLight(light);
            icon.addVertex(m, ix0, iy1, 0).setColor(-1).setUv(u0, v0 + 0.25f).setLight(light);
            icon.addVertex(m, ix1, iy1, 0).setColor(-1).setUv(u0 + 0.25f, v0 + 0.25f).setLight(light);
            icon.addVertex(m, ix1, iy0, 0).setColor(-1).setUv(u0 + 0.25f, v0).setLight(light);
        }
        pose.popPose();
    }

    private void ratingRow(T entity, PoseStack pose, MultiBufferSource buffers, float y, float time) {
        int kind = entity.ratingKind(), filled = entity.ratingFilled(), outOf = entity.ratingOutOf();
        if (outOf == 0) return;
        pose.pushPose();
        Matrix4f m = face(pose, y);
        VertexConsumer vc = buffers.getBuffer(RenderType.text(RATING));
        int light = LightTexture.FULL_BRIGHT;
        float size = 7, gap = 1, x = -(outOf * (size + gap) - gap) / 2f;
        int shining = (int) (time / 6) % (outOf + 8);
        for (int i = 0; i < outOf; i++) {
            boolean full = i < filled;
            int cellX, cellY;
            if (kind == RestaurantNpc.STARS) {
                cellX = !full ? 2 : i == shining ? 1 : 0;
                cellY = 0;
            } else {
                cellX = full ? 0 : 1;
                cellY = 1;
            }
            float u0 = cellX * 8 / 32f, v0 = cellY * 8 / 16f, u1 = u0 + 7 / 32f, v1 = v0 + 7 / 16f;
            float x0 = x + i * (size + gap), x1 = x0 + size;
            vc.addVertex(m, x0, 0, 0).setColor(-1).setUv(u0, v0).setLight(light);
            vc.addVertex(m, x0, size, 0).setColor(-1).setUv(u0, v1).setLight(light);
            vc.addVertex(m, x1, size, 0).setColor(-1).setUv(u1, v1).setLight(light);
            vc.addVertex(m, x1, 0, 0).setColor(-1).setUv(u1, v0).setLight(light);
        }
        pose.popPose();
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int colour, int light) {
        vc.addVertex(m, x0, y0, 0).setColor(colour).setLight(light);
        vc.addVertex(m, x0, y1, 0).setColor(colour).setLight(light);
        vc.addVertex(m, x1, y1, 0).setColor(colour).setLight(light);
        vc.addVertex(m, x1, y0, 0).setColor(colour).setLight(light);
    }

    @Override
    protected void scale(T entity, PoseStack pose, float partialTick) {
        pose.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return ClientSkins.get(entity.skin()).texture();
    }
}
