package com.ladleandlantern.client.overlay;


import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.block.CorkboardBlock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;

public final class CorkboardHud {
    private static final int PAPER = 0xFFFFF7E8, EDGE = 0xFFB8875A, INK = 0xFF5B3A23, SOFT = 0xFF9A6E4A;

    private CorkboardHud() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LadleAndLantern.id("corkboard_note"), CorkboardHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || mc.options.hideGui || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockEntity(pos) instanceof CorkboardBlock.Board board)) return;
        ItemStack note = noteAt(board, hit.getLocation());
        if (note.isEmpty()) return;
        List<Component> lines = new ArrayList<>();
        ItemLore lore = note.get(DataComponents.LORE);
        if (lore != null) lines.addAll(lore.lines());
        if (lines.isEmpty()) lines.add(note.getHoverName());
        draw(g, mc.font, lines, g.guiWidth() / 2 + 12, g.guiHeight() / 2 - 8);
    }

    private static ItemStack noteAt(CorkboardBlock.Board board, Vec3 at) {
        BlockPos pos = board.getBlockPos();
        float angle = (float) Math.toRadians(180 - board.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot());
        double dx = at.x - (pos.getX() + 0.5), dy = at.y - (pos.getY() + 0.5), dz = at.z - (pos.getZ() + 0.5);
        double localX = dx * Math.cos(angle) - dz * Math.sin(angle);
        int col = (int) Math.round(localX / 0.28) + 1, row = 1 - (int) Math.round(dy / 0.27);
        if (col < 0 || col > 2 || row < 0 || row > 2) return ItemStack.EMPTY;
        List<ItemStack> notes = board.items();
        int i = row * 3 + col;
        return i < notes.size() ? notes.get(i) : ItemStack.EMPTY;
    }

    private static void draw(GuiGraphics g, Font font, List<Component> lines, int x, int y) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Component plain = Component.literal(lines.get(i).getString().trim());
            wrapped.addAll(font.split(plain, 150));
        }
        int w = wrapped.stream().mapToInt(font::width).max().orElse(0) + 10;
        int h = wrapped.size() * 10 + 6;
        x = Math.min(x, g.guiWidth() - w - 4);
        g.fill(x + 1, y, x + w - 1, y + h, EDGE);
        g.fill(x, y + 1, x + w, y + h - 1, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PAPER);
        int ty = y + 4;
        for (int i = 0; i < wrapped.size(); i++) {
            boolean signature = i == wrapped.size() - 1 && wrapped.size() > 1;
            g.drawString(font, wrapped.get(i), x + 5, ty, signature ? SOFT : INK, false);
            ty += 10;
        }
    }
}
