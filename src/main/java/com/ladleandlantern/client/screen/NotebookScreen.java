package com.ladleandlantern.client.screen;

import com.ladleandlantern.core.food.Order;

import com.ladleandlantern.client.integration.RecipeViewers;
import com.ladleandlantern.core.restaurant.Course;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class NotebookScreen extends Screen {
    private static final int BOOK_W = 192, TEXT_X = 36, TEXT_W = 114, INK = 0xFF000000, FADED = 0xFF6B5B45, LINK = 0xFF2255AA;
    private record Link(int x, int y, int w, ItemStack stack) {}

    private final CompoundTag data;
    private final List<CompoundTag> pages = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private int untaken;
    private int page;
    private PageButton forward, back;

    public NotebookScreen(CompoundTag data) {
        super(Component.literal("Order Notebook"));
        this.data = data;
        for (Tag t : data.getList("orders", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            if (o.getBoolean("taken")) pages.add(o);
            else untaken++;
        }
    }

    @Override
    protected void init() {
        int left = (width - BOOK_W) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, 196, 200, 20).build());
        forward = addRenderableWidget(new PageButton(left + 116, 159, true, b -> turn(1), true));
        back = addRenderableWidget(new PageButton(left + 43, 159, false, b -> turn(-1), true));
        updateButtons();
    }

    private void turn(int delta) {
        page = net.minecraft.util.Mth.clamp(page + delta, 0, pageCount() - 1);
        updateButtons();
    }

    private int pageCount() {
        return Math.max(1, pages.size());
    }

    private void updateButtons() {
        forward.visible = page < pageCount() - 1;
        back.visible = page > 0;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BookViewScreen.BOOK_LOCATION, (width - BOOK_W) / 2, 2, 0, 0, BOOK_W, BOOK_W);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        links.clear();
        int left = (width - BOOK_W) / 2, x = left + TEXT_X, y = 32;
        String pageLabel = Component.translatable("book.pageIndicator", page + 1, pageCount()).getString();
        g.drawString(font, pageLabel, left + BOOK_W - 44 - font.width(pageLabel), 18, INK, false);
        ItemStack hovered = ItemStack.EMPTY;

        if (pages.isEmpty()) {
            g.drawString(font, Component.literal(data.getString("name")).withStyle(ChatFormatting.BOLD), x, y, INK, false);
            g.drawWordWrap(font, Component.literal("No orders taken yet. Hold this notebook and right-click a waiting customer."), x, y + 16, TEXT_W, FADED);
        } else {
            CompoundTag o = pages.get(page);
            g.drawString(font, Component.literal(o.getString("label")).withStyle(ChatFormatting.BOLD), x, y, INK, false);
            g.drawString(font, CozyScreen.fit(font, o.getString("customer"), TEXT_W), x, y + 10, FADED, false);
            long secs = o.getLong("secondsLeft");
            String time = secs >= 0 ? String.format("%d:%02d left", secs / 60, secs % 60) : "Running late!";
            g.drawString(font, time, x, y + 20, secs > 60 ? 0xFF2E6B2E : 0xFFAA2222, false);
            int cy = y + 34;
            for (Tag t : o.getList("items", Tag.TAG_COMPOUND)) {
                CompoundTag line = (CompoundTag) t;
                ItemStack stack = CozyScreen.stack(line.getString("id"));
                g.renderItem(stack, x, cy);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= cy && mouseY < cy + 16) hovered = stack;
                g.drawString(font, CozyScreen.fit(font, stack.getHoverName().getString(), TEXT_W - 20), x + 19, cy, INK, false);
                g.drawString(font, Course.byName(line.getString("course")).label, x + 19, cy + 9, FADED, false);
                if (RecipeViewers.available()) {
                    String recipe = "Recipe";
                    int rx = x + TEXT_W - font.width(recipe);
                    boolean hover = mouseX >= rx && mouseX < rx + font.width(recipe) && mouseY >= cy + 9 && mouseY < cy + 18;
                    g.drawString(font, Component.literal(recipe).withStyle(hover ? ChatFormatting.UNDERLINE : ChatFormatting.RESET), rx, cy + 9, LINK, false);
                    links.add(new Link(rx, cy + 9, font.width(recipe), stack));
                }
                cy += 22;
            }
        }
        if (untaken > 0) {
            String waiting = untaken + (untaken == 1 ? " customer is" : " customers are") + " waiting to order";
            g.drawWordWrap(font, Component.literal(waiting), x, 144, TEXT_W, FADED);
        }
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Link l : links) {
            if (mx >= l.x && mx < l.x + l.w && my >= l.y && my < l.y + 9) {
                RecipeViewers.showRecipes(l.stack);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
