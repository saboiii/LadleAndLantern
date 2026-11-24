package com.ladleandlantern.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ReceiptScreen extends CozyScreen {
    private static final int PAPER = 0xFFFFFBF2, PAPER_EDGE = 0xFFE3D4BC, DASH = 0xFFCDBBA0, TICK = 0xFF5FA85A;
    private final CompoundTag data;
    private final ListTag items;

    public ReceiptScreen(CompoundTag data) {
        super(Component.literal("Receipt"));
        this.data = data;
        this.items = data.getList("items", Tag.TAG_COMPOUND);
        bookW = 156;
        bookH = 112 + Math.min(items.size(), 8) * 18;
    }

    @Override
    protected void drawFrame(GuiGraphics g, int mx, int my) {
        int x0 = left, y0 = top, x1 = left + bookW, y1 = top + bookH;

        g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x40000000);
        g.fill(x0, y0 + 3, x1, y1 - 3, PAPER);
        g.fill(x0, y0 + 3, x0 + 1, y1 - 3, PAPER_EDGE);
        g.fill(x1 - 1, y0 + 3, x1, y1 - 3, PAPER_EDGE);
        for (int x = x0; x < x1; x += 6) {
            for (int i = 0; i < 3; i++) {
                g.fill(x + i, y0 + 3 - i, x + 6 - i, y0 + 4 - i, PAPER);
                g.fill(x + i, y1 - 4 + i, x + 6 - i, y1 - 3 + i, PAPER);
            }
        }
        closeButton(g, mx, my, x1 - 6, y0 - 4);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my) {
        int x = left + 10, w = bookW - 20, cx = left + bookW / 2, y = top + 10;
        centred(g, fit(data.getString("restaurant"), w), cx, y, ACCENT);
        y += 11;
        String sub = data.getString("label") + ", " + data.getString("clock");
        small(g, sub, cx - (int) (font.width(sub) * 0.375f), y, INK_SOFT);
        y += 10;
        dashes(g, x, y, w);
        y += 6;

        round(g, x, y, x + 20, y + 20, SLOT, SLOT_EDGE);
        face(g, data.getString("skin"), x + 2, y + 2, 16);
        text(g, fit(data.getString("name"), w - 64), x + 25, y + 2, INK);
        small(g, data.getString("kind"), x + 25, y + 13, INK_SOFT);
        int stars = data.getInt("stars"), max = Math.max(1, data.getInt("maxStars"));
        for (int i = 0; i < max; i++) star(g, x + w - max * 7 + i * 7, y + 3, i < stars);
        y += 26;
        dashes(g, x, y, w);
        y += 6;

        for (int i = 0; i < items.size() && i < 8; i++) {
            CompoundTag line = items.getCompound(i);
            ItemStack stack = stack(line.getString("id"));
            icon(g, mx, my, stack, x, y);
            boolean served = line.getBoolean("served");
            text(g, fit(stack.getHoverName().getString(), w - 34), x + 20, y + 4, served ? INK_SOFT : INK);
            if (served) tick(g, x + w - 9, y + 4);
            else round(g, x + w - 9, y + 4, x + w - 1, y + 12, 0xFFFFFFFF, DASH);
            y += 18;
        }
        if (items.size() > 8) {
            small(g, "and " + (items.size() - 8) + " more", x + 20, y - 2, INK_SOFT);
        }
        dashes(g, x, y, w);
        y += 6;

        text(g, data.getString("status"), x, y, INK);
        float patience = data.getFloat("patience");
        if (patience >= 0) {
            pill(g, x + w - 52, y + 1, 52, 6, patience, patienceColour(patience));
            hint(mx, my, x + w - 52, y, 52, 8, "Patience");
        }
        y += 14;
        String thanks = "Thank you for visiting";
        small(g, thanks, cx - (int) (font.width(thanks) * 0.375f), y, INK_SOFT);
    }

    private static void dashes(GuiGraphics g, int x, int y, int w) {
        for (int i = 0; i < w; i += 5) g.fill(x + i, y, x + Math.min(w, i + 3), y + 1, DASH);
    }

    private static void tick(GuiGraphics g, int x, int y) {
        g.fill(x, y + 4, x + 2, y + 6, TICK);
        g.fill(x + 2, y + 5, x + 3, y + 8, TICK);
        g.fill(x + 3, y + 3, x + 5, y + 6, TICK);
        g.fill(x + 5, y + 1, x + 7, y + 4, TICK);
        g.fill(x + 7, y, x + 8, y + 2, TICK);
    }
}
