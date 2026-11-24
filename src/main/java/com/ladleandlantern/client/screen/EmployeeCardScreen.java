package com.ladleandlantern.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class EmployeeCardScreen extends CozyScreen {
    private final CompoundTag data;

    public EmployeeCardScreen(CompoundTag data) {
        super(Component.literal("Employee card"));
        this.data = data;
        bookW = 200;
        bookH = 162;
    }

    @Override
    protected void drawFrame(GuiGraphics g, int mx, int my) {
        int x0 = left, y0 = top, x1 = left + bookW, y1 = top + bookH;
        g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x40000000);
        round(g, x0, y0, x1, y1, PAGE, OUTLINE);

        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 18, ACCENT);
        g.fill(x0 + 1, y0 + 18, x1 - 1, y0 + 19, darken(ACCENT));
        closeButton(g, mx, my, x1 - 6, y0 - 4);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my) {
        int x = left + 10, w = bookW - 20;
        centred(g, fit(data.getString("restaurant") + " staff", w), left + bookW / 2, top + 5, 0xFFFFF3DE);

        int py = top + 26;
        photo(g, data.getString("skin"), x, py);
        int tx = x + 48;
        text(g, fit(data.getString("name"), w - 48), tx, py + 2, INK);
        small(g, data.getString("title"), tx, py + 13, INK_SOFT);
        levelAndHearts(g, mx, my, tx, py + 22, py + 31, data.getInt("level"), data.getInt("hearts"));

        int y = py + 48;

        text(g, "Wage", x, y, INK_SOFT);
        String amount = String.valueOf(data.getInt("wage"));
        text(g, amount, x + 46, y, INK);
        int after = x + 46 + font.width(amount) + 2;
        ItemStack money = stack(data.getString("currencyId"));
        if (!money.isEmpty()) {
            bigItem(g, money, after, y - 1, 0.6f);
            after += 12;
        }
        text(g, "a day", after, y, INK);
        y += 12;
        row(g, x, y, "Pay", data.getString("pay"));
        y += 12;
        row(g, x, y, "Birthday", data.getString("birthday"));
        y += 12;
        text(g, "Loves", x, y, INK_SOFT);
        ItemStack cake = stack(data.getString("cake"));
        bigItem(g, cake, x + 46, y - 2, 0.7f);
        text(g, fit(cake.getHoverName().getString(), w - 60), x + 58, y, INK);
        y += 16;

        inset(g, x, y, w, 26);
        text(g, fit(data.getString("status"), w - 12), x + 6, y + 4, data.getBoolean("owed") ? BAD : INK);
        String why = data.getString("why");
        if (!why.isEmpty()) small(g, fit(why, (int) ((w - 12) / 0.75f)), x + 6, y + 15, INK_SOFT);
    }

    private void row(GuiGraphics g, int x, int y, String label, String value) {
        text(g, label, x, y, INK_SOFT);
        text(g, fit(value, bookW - 20 - 46), x + 46, y, value.startsWith("Owed") ? BAD : INK);
    }
}
