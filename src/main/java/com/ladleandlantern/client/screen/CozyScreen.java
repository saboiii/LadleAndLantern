package com.ladleandlantern.client.screen;

import com.ladleandlantern.client.ClientSkins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public abstract class CozyScreen extends Screen {
    protected static final int OUTLINE = 0xFF3D2A1E, COVER = 0xFF9C6644, COVER_LIGHT = 0xFFB87D52, SPINE = 0xFF6B4329;
    protected static final int PAGE = 0xFFF7DCB4, PAGE_EDGE = 0xFFD8A774, PAGE_SHADE = 0xFFEDCB9A;
    protected static final int INSET = 0xFFEFCB9C, INSET_EDGE = 0xFFD29C66, INSET_DARK = 0xFFE3B784;
    protected static final int SLOT = 0xFFF4D6AC, SLOT_EDGE = 0xFFC99260, SLOT_HOVER = 0xFFFBE7C6;
    protected static final int BOARD = 0xFF433A34, BOARD_EDGE = 0xFF2B241F, BOARD_TEXT = 0xFFF6E7CF;
    protected static final int INK = 0xFF5B3A23, INK_SOFT = 0xFF9A6E4A, ACCENT = 0xFFC4553E, GOLD = 0xFFE8B33C;
    protected static final int GOOD = 0xFF5E9A45, BAD = 0xFFC9483F, MINT = 0xFF6FCF8A, PEACH = 0xFFFFC98B, PINK = 0xFFFF9E9E;
    protected static final int TAB = 0xFFC79567, TAB_DARK = 0xFFA97A50;

    private record Region(int x, int y, int w, int h, Runnable action) {}

    private final List<Region> regions = new ArrayList<>();
    protected int bookW = 400, bookH = 236;
    protected int left, top;
    protected ItemStack hovered = ItemStack.EMPTY;
    protected List<FormattedCharSequence> tooltip;

    protected CozyScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        left = (width - bookW) / 2;
        top = (height - bookH) / 2 + 8;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        regions.clear();
        hovered = ItemStack.EMPTY;
        tooltip = null;
        renderBackground(g, mx, my, partialTick);
        drawFrame(g, mx, my);
        drawContent(g, mx, my);

        for (var child : renderables) child.render(g, mx, my, partialTick);
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mx, my);
        else if (tooltip != null) g.renderTooltip(font, tooltip, mx, my);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x66000000);
    }

    protected abstract void drawFrame(GuiGraphics g, int mx, int my);

    protected abstract void drawContent(GuiGraphics g, int mx, int my);

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (Region r : List.copyOf(regions)) {
            if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) {
                click();
                r.action.run();
                return true;
            }
        }

        setFocused(null);
        return false;
    }

    protected void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f));
    }

    protected void pageTurn() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected void region(int x, int y, int w, int h, Runnable action) {
        regions.add(new Region(x, y, w, h, action));
    }

    protected static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected static void round(GuiGraphics g, int x0, int y0, int x1, int y1, int fill, int border) {
        g.fill(x0 + 1, y0, x1 - 1, y1, border);
        g.fill(x0, y0 + 1, x1, y1 - 1, border);
        g.fill(x0 + 2, y0 + 1, x1 - 2, y1 - 1, fill);
        g.fill(x0 + 1, y0 + 2, x1 - 1, y1 - 2, fill);
    }

    protected void book(GuiGraphics g, int x, int y, int w, int h, boolean twoPages) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x55000000);
        round(g, x, y, x + w, y + h, COVER, OUTLINE);
        g.fill(x + 2, y + 2, x + w - 2, y + 4, COVER_LIGHT);
        if (twoPages) {
            int mid = x + w / 2;
            page(g, x + 6, y + 6, mid - 1, y + h - 7);
            page(g, mid + 1, y + 6, x + w - 6, y + h - 7);
            g.fill(mid - 1, y + 4, mid + 1, y + h - 5, SPINE);
            g.fill(mid - 4, y + 7, mid - 1, y + h - 8, PAGE_SHADE);
            g.fill(mid + 1, y + 7, mid + 4, y + h - 8, PAGE_SHADE);
            g.fill(mid - 6, y + h - 4, mid + 6, y + h + 1, OUTLINE);
            g.fill(mid - 5, y + h - 3, mid + 5, y + h, GOLD);
        } else {
            page(g, x + 6, y + 6, x + w - 6, y + h - 7);
        }
        corner(g, x - 1, y + h - 6);
        corner(g, x + w - 5, y + h - 6);
    }

    private void page(GuiGraphics g, int x0, int y0, int x1, int y1) {
        round(g, x0, y0, x1, y1, PAGE, PAGE_EDGE);
        g.fill(x0 + 2, y1 - 3, x1 - 2, y1 - 1, PAGE_SHADE);
        diamond(g, x0 + 5, y0 + 5, PAGE_EDGE);
        diamond(g, x1 - 6, y0 + 5, PAGE_EDGE);
    }

    private static void corner(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 6, y + 6, OUTLINE);
        g.fill(x + 1, y + 1, x + 5, y + 5, GOLD);
    }

    protected static void diamond(GuiGraphics g, int cx, int cy, int colour) {
        g.fill(cx, cy - 1, cx + 1, cy + 2, colour);
        g.fill(cx - 1, cy, cx + 2, cy + 1, colour);
    }

    protected static void inset(GuiGraphics g, int x, int y, int w, int h) {
        round(g, x, y, x + w, y + h, INSET, INSET_EDGE);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, INSET_DARK);
    }

    protected static void board(GuiGraphics g, int x, int y, int w, int h) {
        round(g, x, y, x + w, y + h, BOARD, BOARD_EDGE);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF574D45);
    }

    protected static void slot(GuiGraphics g, int x, int y, int size, boolean lit) {
        round(g, x, y, x + size, y + size, lit ? SLOT_HOVER : SLOT, SLOT_EDGE);
        g.fill(x + 2, y + size - 2, x + size - 2, y + size - 1, INSET_DARK);
    }

    protected void heading(GuiGraphics g, String text, int cx, int y) {
        int w = font.width(text);
        g.drawString(font, text, cx - w / 2, y, ACCENT, false);
        diamond(g, cx - w / 2 - 8, y + 4, PAGE_EDGE);
        diamond(g, cx - w / 2 - 13, y + 4, PAGE_EDGE);
        diamond(g, cx + w / 2 + 7, y + 4, PAGE_EDGE);
        diamond(g, cx + w / 2 + 12, y + 4, PAGE_EDGE);
    }

    protected void text(GuiGraphics g, String s, int x, int y, int colour) {
        g.drawString(font, s, x, y, colour, false);
    }

    protected void centred(GuiGraphics g, String s, int cx, int y, int colour) {
        g.drawString(font, s, cx - font.width(s) / 2, y, colour, false);
    }

    protected void small(GuiGraphics g, String s, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, s, 0, 0, colour, false);
        g.pose().popPose();
    }

    protected String fit(String s, int width) {
        return fit(font, s, width);
    }

    protected static String fit(net.minecraft.client.gui.Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        return font.plainSubstrByWidth(s, width - font.width("..")) + "..";
    }

    public enum Style { PLAIN, PRIMARY, DANGER, SELECTED, QUIET }

    protected int button(GuiGraphics g, int mx, int my, int x, int y, String label, Style style, Runnable action) {
        int w = font.width(label) + 10, h = 13;
        boolean hover = over(mx, my, x, y, w, h);
        int fill = switch (style) {
            case PRIMARY -> 0xFF7DB35E;
            case DANGER -> 0xFFE07A6A;
            case SELECTED -> 0xFFF7DCB4;
            case QUIET -> 0xFFE3B784;
            default -> 0xFFF1C98F;
        };
        int edge = switch (style) {
            case PRIMARY -> 0xFF4E7A35;
            case DANGER -> 0xFF9E3A30;
            case SELECTED -> ACCENT;
            default -> SLOT_EDGE;
        };
        if (hover) fill = lighten(fill);
        round(g, x, y, x + w, y + h, fill, edge);
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, darken(fill));
        int textColour = style == Style.PRIMARY || style == Style.DANGER ? 0xFFFFFFFF : style == Style.SELECTED ? ACCENT : INK;
        g.drawString(font, label, x + 5, y + 3, textColour, false);
        region(x, y, w, h, action);
        return w;
    }

    protected int buttonWidth(String label) {
        return font.width(label) + 10;
    }

    protected void closeButton(GuiGraphics g, int mx, int my, int x, int y) {
        boolean hover = over(mx, my, x, y, 12, 12);
        round(g, x, y, x + 12, y + 12, hover ? 0xFFF06A5E : 0xFFD9534F, OUTLINE);
        for (int i = 0; i < 6; i++) {
            g.fill(x + 3 + i, y + 3 + i, x + 4 + i, y + 4 + i, 0xFFFFFFFF);
            g.fill(x + 8 - i, y + 3 + i, x + 9 - i, y + 4 + i, 0xFFFFFFFF);
        }
        region(x, y, 12, 12, this::onClose);
    }

    protected void tab(GuiGraphics g, int mx, int my, int x, int y, ItemStack icon, String name, boolean open, Runnable action) {
        int w = 22, h = open ? 24 : 20, ty = open ? y - 2 : y + 2;
        round(g, x, ty, x + w, ty + h + 4, open ? PAGE : over(mx, my, x, ty, w, h) ? 0xFFD6A574 : TAB, OUTLINE);
        if (!open) g.fill(x + 2, ty + h, x + w - 2, ty + h + 3, TAB_DARK);
        g.renderItem(icon, x + 3, ty + 3);
        region(x, ty, w, h, action);
        if (over(mx, my, x, ty, w, h)) tooltip = List.of(Component.literal(name).getVisualOrderText());
    }

    protected void searchBar(GuiGraphics g, EditBox box, int x, int y, int w) {
        round(g, x, y, x + w, y + 14, 0xFFFFF3DE, SLOT_EDGE);

        g.fill(x + 4, y + 4, x + 8, y + 5, INK_SOFT);
        g.fill(x + 4, y + 8, x + 8, y + 9, INK_SOFT);
        g.fill(x + 3, y + 5, x + 4, y + 8, INK_SOFT);
        g.fill(x + 8, y + 5, x + 9, y + 8, INK_SOFT);
        g.fill(x + 9, y + 9, x + 11, y + 11, INK_SOFT);
        box.setX(x + 14);
        box.setY(y + 3);
        box.setWidth(w - 18);
        box.visible = true;
    }

    protected static EditBox styledBox(net.minecraft.client.gui.Font font, int maxLength, String hint) {
        EditBox box = new CozyBox(font, hint);
        box.setBordered(false);
        box.setMaxLength(maxLength);
        return box;
    }

    private static class CozyBox extends EditBox {
        private final net.minecraft.client.gui.Font font;
        private final String hint;

        CozyBox(net.minecraft.client.gui.Font font, String hint) {
            super(font, 0, 0, 80, 10, Component.literal(hint));
            this.font = font;
            this.hint = hint;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            if (!isVisible()) return;
            String value = getValue();
            int x = getX(), y = getY(), w = getWidth();
            if (value.isEmpty() && !isFocused()) {
                g.drawString(font, hint, x, y, 0xFFB08B68, false);
                return;
            }

            int cursor = Math.min(getCursorPosition(), value.length());
            int start = 0;
            while (start < cursor && font.width(value.substring(start, cursor)) > w - 2) start++;
            String shown = font.plainSubstrByWidth(value.substring(start), w);
            String picked = getHighlighted();
            if (!picked.isEmpty()) {
                int a = value.startsWith(picked, cursor) ? cursor : cursor - picked.length();
                int from = Math.max(a, start), to = Math.min(a + picked.length(), start + shown.length());
                if (to > from) {
                    int hx = x + font.width(value.substring(start, from));
                    g.fill(hx, y - 1, hx + font.width(value.substring(from, to)), y + 9, 0xFFF2C38A);
                }
            }
            g.drawString(font, shown, x, y, INK, false);
            if (isFocused() && net.minecraft.Util.getMillis() / 300 % 2 == 0) {
                int cx = x + font.width(value.substring(start, cursor));
                g.fill(cx, y - 1, cx + 1, y + 9, INK);
            }
        }
    }

    protected void chips(GuiGraphics g, int mx, int my, int x, int y, int w, String[] labels, int selected, java.util.function.IntConsumer pick) {
        int textW = 0;
        for (String l : labels) textW += font.width(l);
        int gap = 2, spare = w - textW - gap * (labels.length - 1);
        int cx = x;
        for (int i = 0; i < labels.length; i++) {
            int cw = font.width(labels[i]) + spare / labels.length + (i < spare % labels.length ? 1 : 0);
            boolean on = i == selected, hover = over(mx, my, cx, y, cw, 13);
            int fill = on ? PAGE : hover ? lighten(INSET_DARK) : INSET_DARK;
            round(g, cx, y, cx + cw, y + 13, fill, on ? ACCENT : SLOT_EDGE);
            if (!on) g.fill(cx + 2, y + 11, cx + cw - 2, y + 12, darken(fill));
            centred(g, labels[i], cx + cw / 2, y + 3, on ? ACCENT : INK);
            final int index = i;
            region(cx, y, cw, 13, () -> pick.accept(index));
            cx += cw + gap;
        }
    }

    protected void hint(int mx, int my, int x, int y, int w, int h, String... lines) {
        if (over(mx, my, x, y, w, h)) tooltip = java.util.Arrays.stream(lines).map(l -> Component.literal(l).getVisualOrderText()).toList();
    }

    protected static int starsFor(int average, int orders) {
        if (orders == 0) return 0;
        return average >= 90 ? 5 : average >= 80 ? 4 : average >= 65 ? 3 : average >= 50 ? 2 : 1;
    }

    protected static void stars(GuiGraphics g, int x, int y, int lit) {
        for (int i = 0; i < 5; i++) star(g, x + i * 7, y, i < lit);
    }

    protected static int patienceColour(float p) {
        return p > 0.5f ? MINT : p > 0.25f ? PEACH : PINK;
    }

    protected static void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int maxScroll) {
        if (maxScroll <= 0) return;
        round(g, x, y, x + 4, y + h, INSET, INSET_EDGE);
        int thumb = Math.max(10, h / (maxScroll + 1));
        int ty = y + Math.round((h - thumb) * (scroll / (float) maxScroll));
        round(g, x, ty, x + 4, ty + thumb, 0xFFB07A4C, OUTLINE);
    }

    protected static void pill(GuiGraphics g, int x, int y, int w, int h, float progress, int colour) {
        round(g, x, y, x + w, y + h, 0xFFFFF3DE, SLOT_EDGE);
        int fillW = Math.round((w - 2) * net.minecraft.util.Mth.clamp(progress, 0f, 1f));

        if (progress > 0) fillW = Math.max(3, fillW);
        if (fillW > 2) round(g, x + 1, y + 1, x + 1 + fillW, y + h - 1, colour, colour);
    }

    protected void card(GuiGraphics g, int x, int y, int w, int h, ItemStack icon, String label, java.util.function.BiConsumer<Integer, Integer> value) {
        inset(g, x, y, w, h);
        g.renderItem(icon, x + 3, y + 3);
        small(g, label, x + 22, y + h - 19, INK_SOFT);
        value.accept(x + 22, y + h - 11);
    }

    protected void levelAndHearts(GuiGraphics g, int mx, int my, int x, int starsY, int heartsY, int level, int hearts) {
        stars(g, x, starsY, level);
        hint(mx, my, x, starsY - 1, 35, 7, "Level " + level);
        for (int i = 0; i < 5; i++) heart(g, x + i * 7, heartsY, i < hearts);
        hint(mx, my, x, heartsY - 1, 35, 7, "How happy they are working here");
    }

    protected static void photo(GuiGraphics g, String skin, int x, int y) {
        round(g, x, y, x + 40, y + 40, SLOT, SLOT_EDGE);
        face(g, skin, x + 4, y + 4, 32);
    }

    protected void icon(GuiGraphics g, int mx, int my, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
        if (over(mx, my, x, y, 16, 16)) hovered = stack;
    }

    protected static void bigItem(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    protected static void face(GuiGraphics g, String skin, int x, int y, int size) {
        PlayerFaceRenderer.draw(g, ClientSkins.get(skin), x, y, size);
    }

    protected static void star(GuiGraphics g, int x, int y, boolean filled) {
        int c = filled ? GOLD : 0xFFD8B48A;
        g.fill(x + 2, y, x + 3, y + 1, c);
        g.fill(x + 1, y + 1, x + 4, y + 2, c);
        g.fill(x, y + 2, x + 5, y + 3, c);
        g.fill(x + 1, y + 3, x + 4, y + 4, c);
        g.fill(x, y + 4, x + 2, y + 5, c);
        g.fill(x + 3, y + 4, x + 5, y + 5, c);
    }

    protected static void heart(GuiGraphics g, int x, int y, boolean filled) {
        int c = filled ? 0xFFE0564F : 0xFFE3B79B;
        g.fill(x, y + 1, x + 2, y + 3, c);
        g.fill(x + 3, y + 1, x + 5, y + 3, c);
        g.fill(x + 1, y, x + 2, y + 1, c);
        g.fill(x + 3, y, x + 4, y + 1, c);
        g.fill(x + 1, y + 3, x + 4, y + 4, c);
        g.fill(x + 2, y + 4, x + 3, y + 5, c);
    }

    protected static ItemStack stack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    protected static int lighten(int c) {
        int r = Math.min(255, (c >> 16 & 0xFF) + 18), gr = Math.min(255, (c >> 8 & 0xFF) + 18), b = Math.min(255, (c & 0xFF) + 18);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    protected static int darken(int c) {
        int r = (c >> 16 & 0xFF) * 4 / 5, gr = (c >> 8 & 0xFF) * 4 / 5, b = (c & 0xFF) * 4 / 5;
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }
}
