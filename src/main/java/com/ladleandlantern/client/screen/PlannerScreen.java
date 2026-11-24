package com.ladleandlantern.client.screen;

import com.ladleandlantern.core.restaurant.Restaurant;

import com.ladleandlantern.core.item.PlannerMode;
import com.ladleandlantern.core.network.PlannerModePayload;
import com.ladleandlantern.core.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class PlannerScreen extends Screen {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int W = 176, H = 42;
    private final PlannerMode current;
    private final boolean linked;

    public PlannerScreen(PlannerMode current, boolean linked) {
        super(Component.literal("Restaurant Planner"));
        this.current = current;
        this.linked = linked;
    }

    private static ItemStack icon(PlannerMode mode) {
        return new ItemStack(switch (mode) {
            case BOUNDS -> Items.MAP;
            case SEAT -> Items.OAK_STAIRS;
            case TABLE -> Items.CRAFTING_TABLE;
            case COUNTER -> Items.SMOOTH_QUARTZ_SLAB;
            case ENTRANCE -> Items.OAK_DOOR;
            case WAITING_LINE -> Items.LEAD;
        });
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - W) / 2, top = (height - H) / 2;
        g.blit(CHEST, left, top, 0, 0, W, 35);
        g.blit(CHEST, left, top + 35, 0, 215, W, 7);
        g.drawString(font, title, left + 8, top + 6, 0xFF404040, false);

        List<FormattedCharSequence> tooltip = null;
        PlannerMode[] modes = PlannerMode.values();
        for (int slot = 0; slot < 9; slot++) {
            int x = left + 8 + slot * 18, y = top + 18;
            boolean hover = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
            ItemStack stack;
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (slot < modes.length) {
                PlannerMode mode = modes[slot];
                stack = icon(mode);
                if (mode == current) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                lines.add(Component.literal(mode.label).withStyle(ChatFormatting.GOLD).getVisualOrderText());
                lines.addAll(font.split(Component.literal(mode.hint).withStyle(ChatFormatting.GRAY), 180));
                lines.add(Component.literal(mode == current ? "Selected" : "Click to use").withStyle(mode == current ? ChatFormatting.GREEN : ChatFormatting.YELLOW).getVisualOrderText());
            } else if (slot == 8) {
                stack = new ItemStack(ItemRegistry.CASHIER.get());
                lines.add(Component.literal(linked ? "Linked to a restaurant" : "Not linked yet").withStyle(linked ? ChatFormatting.GREEN : ChatFormatting.RED).getVisualOrderText());
                if (!linked) lines.add(Component.literal("Sneak-right-click a cash register with the planner.").withStyle(ChatFormatting.GRAY).getVisualOrderText());
            } else {
                continue;
            }
            if (hover) {
                g.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
                tooltip = lines;
            }
            g.renderItem(stack, x, y);
        }
        if (tooltip != null) g.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int left = (width - W) / 2, top = (height - H) / 2;
        PlannerMode[] modes = PlannerMode.values();
        for (int slot = 0; slot < modes.length; slot++) {
            int x = left + 8 + slot * 18, y = top + 18;
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                PacketDistributor.sendToServer(new PlannerModePayload(modes[slot].ordinal()));
                onClose();
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
