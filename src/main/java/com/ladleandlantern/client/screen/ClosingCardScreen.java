package com.ladleandlantern.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ClosingCardScreen extends CozyScreen {
    private final CompoundTag data;
    private final ItemStack money;
    private final ItemStack bestDish;
    private final String bestName;

    public ClosingCardScreen(CompoundTag data) {
        super(Component.literal("Day's takings"));
        this.data = data;
        bookW = 236;
        bookH = 210;
        ItemStack m = stack(data.getString("currencyId"));
        money = m.isEmpty() ? new ItemStack(Items.EMERALD) : m;
        String best = data.getString("best");
        bestDish = stack(best);

        bestName = bestDish.isEmpty() ? best : bestDish.getHoverName().getString();
    }

    @Override
    protected void init() {
        super.init();
        pageTurn();
    }

    @Override
    protected void drawFrame(GuiGraphics g, int mx, int my) {
        book(g, left, top, bookW, bookH, false);
        closeButton(g, mx, my, left + bookW - 9, top - 5);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my) {
        int cx = left + bookW / 2;
        ribbon(g, "Day's takings", cx, top + 12);
        centred(g, fit(data.getString("name"), bookW - 30), cx, top + 34, INK);
        String date = data.getString("date");
        small(g, date, cx - font.width(date) * 3 / 8, top + 45, INK_SOFT);

        int lit = starsFor(data.getInt("average"), data.getInt("orders"));
        int sx = cx - (5 * 14 - 4) / 2;
        for (int i = 0; i < 5; i++) {
            g.pose().pushPose();
            g.pose().translate(sx + i * 14, top + 54, 0);
            g.pose().scale(2, 2, 1);
            star(g, 0, 0, i < lit);
            g.pose().popPose();
        }

        if (data.getInt("orders") > 0) hint(mx, my, sx, top + 54, 5 * 14, 10, data.getInt("average") + "% average score");

        int x0 = left + 12, cardW = (bookW - 24 - 6) / 2, y = top + 70;
        card(g, mx, my, x0, y, cardW, new ItemStack(Items.OAK_DOOR), "Guests", String.valueOf(data.getInt("guests")));
        card(g, mx, my, x0 + cardW + 6, y, cardW, new ItemStack(Items.PLAYER_HEAD), "New faces", String.valueOf(data.getInt("newFaces")));
        y += 25;
        card(g, mx, my, x0, y, cardW, new ItemStack(Items.BOWL), "Orders", String.valueOf(data.getInt("orders")));
        card(g, mx, my, x0 + cardW + 6, y, cardW, new ItemStack(Items.LEATHER_BOOTS), "Walk-outs", String.valueOf(data.getInt("walkouts")));
        y += 25;
        card(g, mx, my, x0, y, cardW, money, "Earnings", String.valueOf(data.getInt("earnings")));
        card(g, mx, my, x0 + cardW + 6, y, cardW, new ItemStack(Items.FLOWER_POT), "Tips", String.valueOf(data.getInt("tips")));
        y += 28;

        inset(g, x0, y, bookW - 24, 28);
        if (bestName.isEmpty()) {
            centred(g, "No dish of the day", cx, y + 10, INK_SOFT);
        } else {
            slot(g, x0 + 5, y + 4, 20, false);
            if (!bestDish.isEmpty()) icon(g, mx, my, bestDish, x0 + 7, y + 6);
            small(g, "Dish of the day", x0 + 31, y + 5, INK_SOFT);
            text(g, fit(bestName, bookW - 24 - 31 - 50), x0 + 31, y + 14, INK);
            int score = data.getInt("bestScore");
            int pillX = x0 + bookW - 24 - 44;
            pill(g, pillX, y + 9, 38, 10, score / 100f, score >= 80 ? MINT : score >= 50 ? PEACH : PINK);
            centred(g, String.valueOf(score), pillX + 19, y + 10, INK);
        }
        y += 34;

        String rank = data.getString("rank");
        int rw = font.width(rank) + 16;
        round(g, cx - rw / 2, y - 3, cx + rw / 2, y + 10, lighten(PAGE), INSET_EDGE);
        centred(g, rank, cx, y, ACCENT);
    }

    private void ribbon(GuiGraphics g, String label, int cx, int y) {
        int w = font.width(label) + 24, x = cx - w / 2;
        int dark = darken(ACCENT);
        g.fill(x - 8, y + 4, x + 2, y + 18, OUTLINE);
        g.fill(x - 7, y + 5, x + 2, y + 17, dark);
        g.fill(x - 7, y + 10, x - 4, y + 12, PAGE);
        g.fill(x + w - 2, y + 4, x + w + 8, y + 18, OUTLINE);
        g.fill(x + w - 2, y + 5, x + w + 7, y + 17, dark);
        g.fill(x + w + 4, y + 10, x + w + 7, y + 12, PAGE);
        round(g, x, y, x + w, y + 16, ACCENT, OUTLINE);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, lighten(ACCENT));
        centred(g, label, cx, y + 4, 0xFFFFF3DE);
    }

    private void card(GuiGraphics g, int mx, int my, int x, int y, int w, ItemStack icon, String label, String value) {
        card(g, x, y, w, 22, icon, label, (vx, vy) -> text(g, value, vx, vy, INK));
        if (over(mx, my, x + 3, y + 3, 16, 16)) hovered = icon;
    }
}
