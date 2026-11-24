package com.ladleandlantern.client.screen;

import com.ladleandlantern.core.restaurant.Rank;
import com.ladleandlantern.core.restaurant.Restaurant;
import com.ladleandlantern.core.shift.Wages;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.client.integration.RecipeViewers;
import com.ladleandlantern.core.network.CashierActionPayload;
import com.ladleandlantern.core.network.ServerScreens;
import com.ladleandlantern.core.registry.ItemRegistry;
import com.ladleandlantern.core.restaurant.Course;
import com.ladleandlantern.core.restaurant.GameCalendar;
import com.ladleandlantern.core.restaurant.GameCalendar.Holiday;
import com.ladleandlantern.core.restaurant.GameCalendar.Season;
import com.ladleandlantern.core.restaurant.Schedule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CashierScreen extends CozyScreen {
    private enum Tab {
        OVERVIEW("Overview", ItemRegistry.CASHIER), FLOOR("Floor plan", ItemRegistry.PLANNER), MENU("Menu", ItemRegistry.MENU_CARD),
        CALENDAR("Calendar", () -> Items.CLOCK), STAFF("Staff", () -> Items.NAME_TAG), REGULARS("Regulars", ItemRegistry.THANK_YOU_NOTE);
        final String label;
        final Supplier<? extends Item> icon;
        Tab(String label, Supplier<? extends Item> icon) { this.label = label; this.icon = icon; }
    }

    private static final String[] WEEK_LETTERS = {"M", "T", "W", "T", "F", "S", "S"};
    private static final int[] STATUS = {0xFFD8C2A4, 0xFF9CC7E8, 0xFFFFD86B, 0xFFFFB070, 0xFF6FCF8A};
    private static final String[] STATUS_NAMES = {"Free", "Coming", "Ready", "Waiting", "Eating"};
    private static final String[] STATUS_LONG = {"Free", "On the way", "Ready to order", "Waiting for food", "Eating"};
    private static final int CRITIC = 0xFF8E6BBF;
    private static List<ItemStack> allFood;

    private CompoundTag data;
    private BlockPos cashier;
    private Tab tab = Tab.OVERVIEW;
    private EditBox nameBox, cuisineBox, menuSearch, pickSearch, guestSearch;
    private int ticks;

    private int floor;
    private int floorScroll;
    private final Map<String, Float> shownPatience = new HashMap<>();
    private boolean addingDishes;
    private int courseFilter;
    private int menuScroll;
    private String selectedDish;
    private int calendarSeason = -1;
    private String slot;
    private boolean showHours;
    private int pickScroll;
    private int selectedStaff = -1;
    private int staffScroll;
    private int confirmFire = -1;
    private int guestFilter;
    private int guestScroll;
    private String selectedGuest;

    public CashierScreen(CompoundTag data) {
        super(Component.literal("Cash register"));
        this.data = data;
        this.cashier = BlockPos.of(data.getLong("cashier"));
    }

    public void update(CompoundTag fresh) {
        if (fresh.getBoolean("_live")) {
            for (String key : data.getAllKeys()) if (!fresh.contains(key)) fresh.put(key, data.get(key));
        } else if (!fresh.contains("profiles") && data.contains("profiles")) {
            fresh.put("profiles", data.get("profiles"));
        }
        data = fresh;
        cashier = BlockPos.of(fresh.getLong("cashier"));
        if (nameBox != null && !nameBox.isFocused()) nameBox.setValue(fresh.getString("name"));
        if (cuisineBox != null && !cuisineBox.isFocused()) cuisineBox.setValue(fresh.getString("cuisine"));
    }

    @Override
    protected void init() {
        super.init();
        nameBox = addRenderableWidget(styledBox(font, 32, "Restaurant name"));
        nameBox.setValue(data.getString("name"));
        cuisineBox = addRenderableWidget(styledBox(font, 32, "Cuisine"));
        cuisineBox.setValue(data.getString("cuisine"));
        menuSearch = addRenderableWidget(styledBox(font, 40, "Search dishes"));
        menuSearch.setResponder(s -> menuScroll = 0);
        pickSearch = addRenderableWidget(styledBox(font, 40, "Search any food"));
        pickSearch.setResponder(s -> pickScroll = 0);
        guestSearch = addRenderableWidget(styledBox(font, 32, "Search names"));
        guestSearch.setResponder(s -> guestScroll = 0);
        if (slot == null) slot = ServerScreens.slotKey(currentSeason());
        if (calendarSeason < 0) calendarSeason = currentSeason().ordinal();
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 20 == 0 && (tab == Tab.OVERVIEW || tab == Tab.FLOOR)) {
            CompoundTag live = new CompoundTag();
            live.putBoolean("_live", true);
            send("view", live);
        }
    }

    private void send(String action, CompoundTag args) {
        args.putBoolean("_profiles", tab == Tab.REGULARS);
        PacketDistributor.sendToServer(new CashierActionPayload(cashier, action, args));
    }

    private void send(String action, String key, String value) {
        CompoundTag t = new CompoundTag();
        t.putString(key, value);
        send(action, t);
    }

    private void open(Tab next) {
        if (next == tab) return;
        tab = next;
        pageTurn();
        setFocused(null);
        if (next == Tab.REGULARS) send("view", new CompoundTag());
    }

    private int mid() { return left + bookW / 2; }
    private int lx() { return left + 14; }
    private int rx() { return mid() + 9; }
    private int pageW() { return bookW / 2 - 23; }
    private int bottom() { return top + bookH - 14; }

    @Override
    protected void drawFrame(GuiGraphics g, int mx, int my) {
        int x = left + 14;
        for (Tab t : Tab.values()) {
            if (t != tab) tab(g, mx, my, x, top - 20, new ItemStack(t.icon.get()), t.label, false, () -> open(t));
            x += 25;
        }
        book(g, left, top, bookW, bookH, true);
        x = left + 14;
        for (Tab t : Tab.values()) {
            if (t == tab) tab(g, mx, my, x, top - 20, new ItemStack(t.icon.get()), t.label, true, () -> {});
            x += 25;
        }
        closeButton(g, mx, my, left + bookW - 9, top - 5);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my) {
        for (EditBox b : List.of(nameBox, cuisineBox, menuSearch, pickSearch, guestSearch)) b.visible = false;
        switch (tab) {
            case OVERVIEW -> drawOverview(g, mx, my);
            case FLOOR -> drawFloor(g, mx, my);
            case MENU -> drawMenu(g, mx, my);
            case CALENDAR -> drawCalendar(g, mx, my);
            case STAFF -> drawStaff(g, mx, my);
            case REGULARS -> drawRegulars(g, mx, my);
        }
    }

    private void drawOverview(GuiGraphics g, int mx, int my) {
        int x = lx(), w = pageW(), y = top + 12;
        heading(g, "Restaurant", x + w / 2, y);
        y += 16;
        inset(g, x, y, w, 50);
        small(g, "NAME", x + 6, y + 5, INK_SOFT);
        textField(g, nameBox, x + 6, y + 11, w - 52);
        small(g, "CUISINE", x + 6, y + 28, INK_SOFT);
        textField(g, cuisineBox, x + 6, y + 34, w - 52);
        button(g, mx, my, x + w - 40, y + 19, "Save", Style.PRIMARY, () -> {
            send("rename", "v", nameBox.getValue());
            send("cuisine", "v", cuisineBox.getValue());
        });
        y += 56;

        String holidays = data.getString("holidays");
        int boardH = holidays.isEmpty() ? 31 : 43;
        board(g, x, y, w, boardH);
        boolean open = data.getBoolean("open");
        round(g, x + 6, y + 6, x + 12, y + 12, open ? MINT : PINK, BOARD_EDGE);
        g.drawString(font, open ? "Open" : "Closed", x + 16, y + 5, BOARD_TEXT, false);
        String clock = data.getString("weekday") + " " + data.getString("clock");
        g.drawString(font, clock, x + w - 6 - font.width(clock), y + 5, BOARD_TEXT, false);
        g.drawString(font, data.getString("season"), x + 6, y + 18, 0xFFE8C896, false);
        if (!holidays.isEmpty()) g.drawString(font, fit(holidays, w - 12), x + 6, y + 30, PEACH, false);
        y += boardH + 6;

        String special = data.getString("special");
        slot(g, x, y, 22, false);
        if (!special.isEmpty()) icon(g, mx, my, stack(special), x + 3, y + 3);
        text(g, "Today's special", x + 28, y + 3, INK);
        small(g, special.isEmpty() ? "None" : fit(stack(special).getHoverName().getString(), 140), x + 28, y + 14, INK_SOFT);
        if (special.isEmpty()) hint(mx, my, x, y, w, 22, "Set one with a Specials Board");
        y += 30;

        ListTag checklist = data.getList("checklist", Tag.TAG_STRING);
        for (int i = 0; i < checklist.size() && y < bottom() - 8; i++) {
            round(g, x, y, x + 7, y + 7, 0xFFFFF3DE, SLOT_EDGE);
            small(g, fit(checklist.getString(i), (int) ((w - 12) / 0.75f)), x + 10, y + 1, BAD);
            y += 10;
        }

        x = rx();
        y = top + 12;
        heading(g, "Today", x + w / 2, y);
        y += 16;
        inset(g, x, y, w, 30);
        text(g, fit("Rank " + data.getInt("rank") + ": " + data.getString("title"), w - 12), x + 6, y + 5, ACCENT);
        int ribbons = Math.min(5, data.getInt("ribbons"));
        int ribbonW = ribbons * 10;
        for (int i = 0; i < ribbons; i++) bigItem(g, new ItemStack(ItemRegistry.BLUE_RIBBON.get()), x + w - 6 - ribbonW + i * 10, y + 15, 0.6f);
        if (ribbons > 0) hint(mx, my, x + w - 6 - ribbonW, y + 15, ribbonW, 10, ribbons + " blue ribbon" + (ribbons == 1 ? "" : "s"));
        long points = data.getLong("points");
        int from = data.getInt("rankFrom"), to = data.getInt("rankTo");
        float progress = to < 0 ? 1f : (points - from) / (float) Math.max(1, to - from);
        int pillW = w - 12 - (ribbons > 0 ? ribbonW + 4 : 0);
        pill(g, x + 6, y + 17, pillW, 7, progress, MINT);
        hint(mx, my, x + 6, y + 17, pillW, 7, to < 0 ? "Top rank" : (to - points) + " points to the next rank");
        y += 36;

        inset(g, x, y, w, 23);
        g.renderItem(new ItemStack(Items.PLAYER_HEAD), x + 3, y + 3);
        small(g, "Guests", x + 22, y + 4, INK_SOFT);
        int arrivals = data.getInt("arrivals"), quota = Math.max(1, data.getInt("quota"));
        pill(g, x + 22, y + 12, w - 28, 7, arrivals / (float) quota, PEACH);
        hint(mx, my, x, y, w, 23, arrivals + " of about " + quota + " today");
        y += 26;

        int half = w / 2 - 2;
        card(g, x, y, half, 23, new ItemStack(ItemRegistry.ORDER_NOTEBOOK.get()), "Orders", (vx, vy) -> text(g, String.valueOf(data.getInt("shiftOrders")), vx, vy, INK));
        int avg = data.getInt("shiftAverage"), orders = data.getInt("shiftOrders");
        card(g, x + half + 4, y, half, 23, new ItemStack(Items.NETHER_STAR), "Average", (vx, vy) -> stars(g, vx, vy + 1, starsFor(avg, orders)));
        hint(mx, my, x + half + 4, y, half, 23, orders == 0 ? "Nothing served yet" : avg + "% average score");
        y += 26;
        card(g, x, y, half, 23, new ItemStack(ItemRegistry.THANK_YOU_NOTE.get()), "Regulars", (vx, vy) -> text(g, String.valueOf(data.getInt("regulars")), vx, vy, INK));
        card(g, x + half + 4, y, half, 23, moneyIcon(), "In the till", (vx, vy) -> text(g, String.valueOf(data.getInt("till")), vx, vy, INK));
    }

    private ItemStack moneyIcon() {
        String c = data.getString("currencyId");
        ItemStack s = c.isEmpty() ? ItemStack.EMPTY : stack(c);
        return s.isEmpty() ? new ItemStack(Items.DIAMOND) : s;
    }

    private void textField(GuiGraphics g, EditBox box, int x, int y, int w) {
        round(g, x, y, x + w, y + 13, 0xFFFFF3DE, SLOT_EDGE);
        box.setX(x + 4);
        box.setY(y + 3);
        box.setWidth(w - 8);
        box.visible = true;
    }

    private void drawFloor(GuiGraphics g, int mx, int my) {
        int x = lx(), w = pageW();
        heading(g, "Floor plan", x + w / 2, top + 12);
        int[] floors = data.getIntArray("floors");
        if (floors.length == 0) floors = new int[]{0};
        floor = Math.min(floor, floors.length - 1);
        int cx = x;
        if (floors.length > 1) {
            for (int i = 0; i < floors.length; i++) {
                final int f = i;
                cx += button(g, mx, my, cx, top + 26, i == 0 ? "Ground" : "Floor " + i, i == floor ? Style.SELECTED : Style.QUIET, () -> floor = f) + 2;
            }
        }
        int mapTop = floors.length > 1 ? top + 44 : top + 28;
        int mapBottom = drawMap(g, mx, my, x, mapTop, w, bottom() - mapTop - 14, floors);
        legend(g, x + w / 2, Math.min(mapBottom + 7, bottom() - 8));

        x = rx();
        heading(g, "Waiting", x + w / 2, top + 12);
        ListTag orders = data.getList("orders", Tag.TAG_COMPOUND);
        int areaTop = top + 28, cardH = 35;
        int rows = Math.max(1, (bottom() - areaTop) / (cardH + 3));
        floorScroll = Mth.clamp(floorScroll, 0, Math.max(0, orders.size() - rows));
        if (orders.isEmpty()) centred(g, "Nobody's waiting", x + w / 2, areaTop + 20, INK_SOFT);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < orders.size(); i++) {
            CompoundTag o = orders.getCompound(i);
            String key = o.getString("label") + "/" + o.getString("customer");
            seen.add(key);

            float target = o.getFloat("patience");
            float shown = shownPatience.getOrDefault(key, target);
            shownPatience.put(key, shown + (target - shown) * 0.08f);
        }
        shownPatience.keySet().retainAll(seen);
        int cardW = orders.size() > rows ? w - 6 : w;
        for (int i = floorScroll; i < orders.size() && i < floorScroll + rows; i++) {
            CompoundTag o = orders.getCompound(i);
            int y = areaTop + (i - floorScroll) * (cardH + 3);
            inset(g, x, y, cardW, cardH);
            text(g, fit(o.getString("label"), cardW - 12), x + 5, y + 4, INK);
            float p = shownPatience.getOrDefault(o.getString("label") + "/" + o.getString("customer"), o.getFloat("patience"));
            pill(g, x + 5, y + 14, cardW - 10, 5, p, patienceColour(p));
            long secs = o.getLong("secondsLeft");
            hint(mx, my, x + 5, y + 13, cardW - 10, 7, secs < 0 ? "Running late" : secs < 60 ? "Under a minute left" : "About " + (secs + 30) / 60 + " min left");
            if (!o.getBoolean("taken")) {
                bigItem(g, new ItemStack(ItemRegistry.ORDER_NOTEBOOK.get()), x + 5, y + 20, 0.8f);
                g.fill(x + 4, y + 20, x + 19, y + 33, 0x88EFCB9C);
                hint(mx, my, x + 4, y + 20, 14, 13, "Hasn't ordered yet");
                continue;
            }
            ListTag items = o.getList("items", Tag.TAG_COMPOUND);
            for (int k = 0; k < items.size() && k < 10; k++) {
                ItemStack s = stack(items.getCompound(k).getString("id"));
                bigItem(g, s, x + 5 + k * 14, y + 20, 0.8f);
                if (over(mx, my, x + 5 + k * 14, y + 20, 13, 13)) hovered = s;
            }
        }
        scrollbar(g, x + w - 4, areaTop, rows * (cardH + 3) - 3, floorScroll, Math.max(0, orders.size() - rows));
    }

    private void legend(GuiGraphics g, int cx, int y) {
        int total = 0;
        for (String n : STATUS_NAMES) total += 8 + (int) (font.width(n) * 0.75f) + 5;
        int lx = cx - (total - 5) / 2;
        for (int i = 0; i < STATUS_NAMES.length; i++) {
            round(g, lx, y, lx + 6, y + 6, STATUS[i], darken(STATUS[i]));
            small(g, STATUS_NAMES[i], lx + 8, y + 1, INK_SOFT);
            lx += 8 + (int) (font.width(STATUS_NAMES[i]) * 0.75f) + 5;
        }
    }

    private int drawMap(GuiGraphics g, int mx, int my, int ax, int ay, int aw, int ah, int[] floors) {
        BlockPos min = BlockPos.of(data.getLong("min")), max = BlockPos.of(data.getLong("max"));
        int lowY = floors[floor] - 1, highY = floor + 1 < floors.length ? floors[floor + 1] - 1 : max.getY() + 1;
        int cols = max.getX() - min.getX() + 1, rows = max.getZ() - min.getZ() + 1;

        int cell = Math.max(3, Math.min((aw - 6) / cols, (ah - 6) / (rows + 1)));
        int ox = ax + (aw - cell * cols) / 2, oy = ay + 3 + cell;
        round(g, ox - 3, oy - cell - 3, ox + cell * cols + 3, oy + cell * rows + 3, 0xFF6B4329, OUTLINE);
        for (int c = 0; c < cols; c++) {
            tile(g, TILE_WALL, ox + c * cell, oy - cell, cell);
            for (int r = 0; r < rows; r++) tile(g, (r + c / 3) % 2 == 0 ? TILE_PLANK_A : TILE_PLANK_B, ox + c * cell, oy + r * cell, cell);
        }
        final int lo = lowY, hi = highY, cellSize = cell, oxx = ox, oyy = oy;
        java.util.function.Function<BlockPos, int[]> at = p -> {
            if (p.getY() < lo || p.getY() >= hi) return null;
            if (p.getX() < min.getX() || p.getX() > max.getX() || p.getZ() < min.getZ() || p.getZ() > max.getZ()) return null;
            return new int[]{oxx + (p.getX() - min.getX()) * cellSize, oyy + (p.getZ() - min.getZ()) * cellSize};
        };
        for (long l : data.getLongArray("counter")) mark(g, mx, my, at.apply(BlockPos.of(l)), TILE_COUNTER, cell, "Counter");
        for (long l : data.getLongArray("trays")) mark(g, mx, my, at.apply(BlockPos.of(l)), TILE_TRAY, cell, "Tray");

        ListTag seats = data.getList("seats", Tag.TAG_COMPOUND);
        java.util.Set<Long> tables = new java.util.HashSet<>();
        for (Tag e : seats) for (long l : ((CompoundTag) e).getLongArray("table")) tables.add(l);
        int line = Math.max(1, cell / 16);
        for (long l : tables) {
            BlockPos t = BlockPos.of(l);
            int[] p = at.apply(t);
            if (p == null) continue;
            boolean front = !tables.contains(t.south().asLong());
            tile(g, front ? TILE_TABLE_FRONT : TILE_TABLE_TOP, p[0], p[1], cell);
            if (!tables.contains(t.north().asLong())) g.fill(p[0], p[1], p[0] + cell, p[1] + line, 0xFF3B2416);
            if (!tables.contains(t.west().asLong())) g.fill(p[0], p[1], p[0] + line, p[1] + (front ? cell * 14 / 16 : cell), 0xFF3B2416);
            if (!tables.contains(t.east().asLong())) g.fill(p[0] + cell - line, p[1], p[0] + cell, p[1] + (front ? cell * 14 / 16 : cell), 0xFF3B2416);
        }

        for (long l : data.getLongArray("line")) mark(g, mx, my, at.apply(BlockPos.of(l).below()), TILE_STEPS, cell, "Waiting line");
        if (data.contains("entrance")) mark(g, mx, my, at.apply(BlockPos.of(data.getLong("entrance"))), TILE_MAT, cell, "Entrance");

        for (long l : data.getLongArray("cashiers")) mark(g, mx, my, at.apply(BlockPos.of(l)), TILE_TILL, cell, "Cash register");

        for (Tag e : data.getList("fixtures", Tag.TAG_COMPOUND)) {
            CompoundTag f = (CompoundTag) e;
            int[] p = at.apply(BlockPos.of(f.getLong("pos")));
            if (p == null) continue;
            ItemStack icon = fixtureIcon(f.getString("kind"));
            if (cell >= 8) bigItem(g, icon, p[0], p[1], cell / 16f);
            else g.fill(p[0], p[1], p[0] + cell, p[1] + cell, 0xFFE8D8B8);
            if (over(mx, my, p[0], p[1], cell, cell)) tooltip = List.of(icon.getHoverName().getVisualOrderText());
        }

        for (Tag e : seats) {
            CompoundTag s = (CompoundTag) e;
            BlockPos seatPos = BlockPos.of(s.getLong("pos"));
            int[] p = at.apply(seatPos);
            if (p == null) continue;
            int back = TILE_CHAIR_BACK_NORTH;
            long[] own = s.getLongArray("table");
            if (own.length > 0) {
                BlockPos t = BlockPos.of(own[0]);
                for (long l : own) if (BlockPos.of(l).distSqr(seatPos) < t.distSqr(seatPos)) t = BlockPos.of(l);
                int dx = t.getX() - seatPos.getX(), dz = t.getZ() - seatPos.getZ();
                if (Math.abs(dx) > Math.abs(dz)) back = dx > 0 ? TILE_CHAIR_BACK_WEST : TILE_CHAIR_BACK_EAST;
                else back = dz > 0 ? TILE_CHAIR_BACK_NORTH : TILE_CHAIR_BACK_SOUTH;
            }
            tile(g, back, p[0], p[1], cell);
            int status = Mth.clamp(s.getInt("status"), 0, 4);
            int dot = Math.max(2, cell / 3);
            round(g, p[0] + cell - dot, p[1] - 1, p[0] + cell + 1, p[1] + dot, STATUS[status], OUTLINE);
            if (over(mx, my, p[0], p[1], cell, cell)) {
                tooltip = List.of(Component.literal("Table " + s.getString("label") + ": " + STATUS_LONG[status]).getVisualOrderText());
            }
        }
        return oy + cell * rows + 3;
    }

    private static final net.minecraft.resources.ResourceLocation FLOOR_PLAN =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ladle_and_lantern", "textures/gui/floor_plan.png");
    private static final int TILE_WALL = 0, TILE_PLANK_A = 1, TILE_PLANK_B = 2, TILE_COUNTER = 3, TILE_TABLE_TOP = 4, TILE_TABLE_FRONT = 5,
            TILE_CHAIR_BACK_NORTH = 6, TILE_CHAIR_BACK_EAST = 7, TILE_CHAIR_BACK_SOUTH = 8, TILE_CHAIR_BACK_WEST = 9,
            TILE_TRAY = 10, TILE_TILL = 11, TILE_MAT = 12, TILE_STEPS = 13, TILE_COUNT = 14;

    private void mark(GuiGraphics g, int mx, int my, int[] p, int index, int cell, String name) {
        if (p == null) return;
        tile(g, index, p[0], p[1], cell);
        hint(mx, my, p[0], p[1], cell, cell, name);
    }

    private static void tile(GuiGraphics g, int index, int x, int y, int size) {
        g.blit(FLOOR_PLAN, x, y, size, size, index * 16, 0, 16, 16, TILE_COUNT * 16, 16);
    }

    private static ItemStack fixtureIcon(String kind) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(LadleAndLantern.id(kind.toLowerCase(Locale.ROOT)))
                .orElse(ItemRegistry.MENU_STAND.get()));
    }

    private void drawMenu(GuiGraphics g, int mx, int my) {
        int x = lx(), w = pageW();
        heading(g, "Menu", x + w / 2, top + 12);
        ListTag menu = data.getList("menu", Tag.TAG_COMPOUND);
        Map<String, Course> onMenu = new LinkedHashMap<>();
        for (int i = 0; i < menu.size(); i++) onMenu.put(menu.getCompound(i).getString("id"), Course.byName(menu.getCompound(i).getString("course")));

        chips(g, mx, my, x, top + 26, w, new String[]{"On the menu (" + onMenu.size() + ")", "+ Add dishes"}, addingDishes ? 1 : 0, i -> {
            addingDishes = i == 1;
            menuScroll = 0;
        });
        searchBar(g, menuSearch, x, top + 43, w);
        chips(g, mx, my, x, top + 61, w, new String[]{"All", "Drinks", "Mains", "Sides", "Puds"}, courseFilter, i -> {
            courseFilter = i;
            menuScroll = 0;
        });

        String query = menuSearch.getValue().toLowerCase(Locale.ROOT);
        Course only = courseFilter == 0 ? null : Course.values()[courseFilter - 1];
        List<ItemStack> items = new ArrayList<>();
        if (addingDishes) {
            for (ItemStack s : allFood()) {
                if (matches(s, query) && (only == null || FoodRules.classify(BuiltInRegistries.ITEM.getKey(s.getItem())) == only)) items.add(s);
            }
        } else {
            onMenu.forEach((id, course) -> {
                ItemStack s = stack(id);
                if (matches(s, query) && (only == null || course == only)) items.add(s);
            });
        }
        int gridTop = top + 79;
        menuScroll = grid(g, mx, my, x, gridTop, w, 8, bottom() - gridTop, items, menuScroll,
                s -> selectedDish = BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),
                s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(selectedDish),
                s -> addingDishes && onMenu.containsKey(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
        if (items.isEmpty()) centred(g, query.isEmpty() && !addingDishes ? "Nothing here yet" : "No matches", x + w / 2, gridTop + 30, INK_SOFT);

        drawDishPage(g, mx, my, onMenu);
    }

    private void drawDishPage(GuiGraphics g, int mx, int my, Map<String, Course> onMenu) {
        int x = rx(), w = pageW();
        if (selectedDish == null) {
            heading(g, "Your menu", x + w / 2, top + 12);
            int y = top + 28;
            String[] plural = {"Drinks", "Mains", "Sides", "Puddings"};
            for (Course c : Course.values()) {
                long n = onMenu.values().stream().filter(v -> v == c).count();
                countRow(g, mx, my, x, y, w, plural[c.ordinal()], n, () -> {
                    courseFilter = c.ordinal() + 1;
                    addingDishes = false;
                    menuScroll = 0;
                });
                y += 24;
            }
            return;
        }
        ItemStack s = stack(selectedDish);
        heading(g, "Dish", x + w / 2, top + 12);
        inset(g, x + w / 2 - 26, top + 26, 52, 52);
        bigItem(g, s, x + w / 2 - 20, top + 32, 2.5f);
        centred(g, fit(s.getHoverName().getString(), w), x + w / 2, top + 84, INK);
        Course course = onMenu.get(selectedDish);
        boolean listed = course != null;
        Set<String> today = new HashSet<>();
        data.getList("today", Tag.TAG_STRING).forEach(t -> today.add(t.getAsString()));
        boolean served = today.contains(selectedDish);
        String status = !listed ? "Not on the menu" : served ? "Served today" : "Not served today";
        small(g, status, x + w / 2 - (int) (font.width(status) * 0.375f), top + 96, listed && served ? GOOD : INK_SOFT);

        int y = top + 110;
        if (listed) {
            text(g, "Course", x, y + 3, INK_SOFT);
            button(g, mx, my, x + 46, y, course.label + "  >", Style.PLAIN, () -> send("menu_course", "id", selectedDish));
            y += 18;
        }
        List<String> when = new ArrayList<>();
        CompoundTag slots = data.getCompound("slots");
        for (String key : slots.getAllKeys()) {
            ListTag ids = slots.getList(key, Tag.TAG_STRING);
            for (int i = 0; i < ids.size(); i++) if (ids.getString(i).equals(selectedDish)) when.add(slotName(key));
        }
        text(g, "When", x, y + 2, INK_SOFT);
        text(g, fit(when.isEmpty() ? "All year" : String.join(", ", when), w - 46), x + 46, y + 2, INK);
        hint(mx, my, x, y, w, 12, "Add it to a season or holiday in the Calendar");

        int by = bottom() - 14, bx = x;
        if (listed) bx += button(g, mx, my, bx, by, "Remove", Style.DANGER, () -> send("menu_remove", "id", selectedDish)) + 4;
        else bx += button(g, mx, my, bx, by, "Add to menu", Style.PRIMARY, () -> send("menu_add", "id", selectedDish)) + 4;
        if (RecipeViewers.available()) bx += button(g, mx, my, bx, by, "Recipe", Style.PLAIN, () -> RecipeViewers.showRecipes(s)) + 4;
        button(g, mx, my, x + w - buttonWidth("Back"), by, "Back", Style.QUIET, () -> selectedDish = null);
    }

    private void countRow(GuiGraphics g, int mx, int my, int x, int y, int w, String label, long n, Runnable action) {
        boolean hover = over(mx, my, x, y, w, 20);
        round(g, x, y, x + w, y + 20, hover ? lighten(INSET) : INSET, INSET_EDGE);
        text(g, label, x + 6, y + 6, INK);
        text(g, String.valueOf(n), x + w - 8 - font.width(String.valueOf(n)), y + 6, ACCENT);
        region(x, y, w, 20, action);
    }

    private static boolean matches(ItemStack s, String query) {
        return query.isEmpty() || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || BuiltInRegistries.ITEM.getKey(s.getItem()).toString().contains(query);
    }

    private static List<ItemStack> allFood() {
        if (allFood == null) {
            allFood = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack s = item.getDefaultInstance();
                if (FoodRules.isFood(s)) allFood.add(s);
            }
        }
        return allFood;
    }

    private interface CellDrawer<T> {
        void draw(T item, int x, int y, int cell, int pad, boolean hover);
    }

    private int grid(GuiGraphics g, int mx, int my, int x, int y, int w, int cols, int h, List<ItemStack> items, int scroll,
            Consumer<ItemStack> onClick, java.util.function.Predicate<ItemStack> selected, java.util.function.Predicate<ItemStack> marked) {
        return grid(g, mx, my, x, y, w, cols, h, items, scroll, onClick, selected, marked,
                (s, sx, sy, cell, pad, hover) -> icon(g, mx, my, s, sx + pad, sy + pad));
    }

    private <T> int grid(GuiGraphics g, int mx, int my, int x, int y, int w, int cols, int h, List<T> items, int scroll,
            Consumer<T> onClick, java.util.function.Predicate<T> selected, java.util.function.Predicate<T> marked, CellDrawer<T> draw) {
        int cell = (w - 6) / cols, rows = Math.max(1, h / cell);
        int pad = (cell - 1 - 16) / 2;
        int maxScroll = Math.max(0, (items.size() + cols - 1) / cols - rows);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        for (int i = 0; i < cols * rows; i++) {
            int sx = x + (i % cols) * cell, sy = y + (i / cols) * cell;
            int index = scroll * cols + i;
            boolean has = index < items.size();
            boolean hover = has && over(mx, my, sx, sy, cell - 1, cell - 1);
            slot(g, sx, sy, cell - 1, hover || has && selected.test(items.get(index)));
            if (!has) continue;
            T s = items.get(index);
            draw.draw(s, sx, sy, cell, pad, hover);
            if (marked.test(s)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(sx + cell - 8, sy + 1, sx + cell - 3, sy + 6, GOOD);
                g.fill(sx + cell - 7, sy + 3, sx + cell - 6, sy + 4, 0xFFFFFFFF);
                g.fill(sx + cell - 6, sy + 4, sx + cell - 5, sy + 5, 0xFFFFFFFF);
                g.fill(sx + cell - 5, sy + 2, sx + cell - 4, sy + 4, 0xFFFFFFFF);
                g.pose().popPose();
            }
            region(sx, sy, cell - 1, cell - 1, () -> onClick.accept(s));
        }
        scrollbar(g, x + w - 4, y, rows * cell - 1, scroll, maxScroll);
        return scroll;
    }

    private Season currentSeason() {
        int length = Math.max(1, data.getInt("seasonLength"));
        return GameCalendar.season(data.getLong("dayAbs"), length);
    }

    private String slotName(String key) {
        return switch (ServerScreens.parseSlot(key)) {
            case Season s -> s.label();
            case Holiday h -> h.label;
            case null, default -> key;
        };
    }

    private record Event(String name, int in, boolean holiday) {}

    private void drawCalendar(GuiGraphics g, int mx, int my) {
        int x = lx(), w = pageW();
        int length = Math.max(1, data.getInt("seasonLength"));
        long today = data.getLong("dayAbs");
        int dayOfYear = (int) Math.floorMod(today, length * 4L);
        long yearStart = today - dayOfYear;
        Season season = Season.values()[calendarSeason];
        heading(g, season.label(), x + w / 2, top + 12);
        button(g, mx, my, x + 2, top + 9, "<", Style.QUIET, () -> calendarSeason = (calendarSeason + 3) % 4);
        button(g, mx, my, x + w - 16, top + 9, ">", Style.QUIET, () -> calendarSeason = (calendarSeason + 1) % 4);

        int cw = w / 7, ch = 21, gx = x + (w - cw * 7) / 2, gy = top + 38;
        int criticDay = data.getInt("criticDay");
        for (int i = 0; i < 7; i++) centred(g, WEEK_LETTERS[i], gx + i * cw + cw / 2, gy - 10, i == criticDay ? CRITIC : INK_SOFT);
        long first = yearStart + (long) calendarSeason * length;
        int lead = Schedule.weekday(first);
        int mask = data.getInt("days");
        Map<Integer, List<String>> events = new HashMap<>();
        Map<Integer, Integer> marks = new HashMap<>();
        for (Holiday h : Holiday.values()) {
            int d = h.dayOfYear(length) - calendarSeason * length;
            if (d >= 0 && d < length) {
                events.computeIfAbsent(d, k -> new ArrayList<>()).add(h.label);
                marks.merge(d, 1, (a, b) -> a | b);
            }
        }
        ListTag staff = data.getList("staff", Tag.TAG_COMPOUND);
        for (int i = 0; i < staff.size(); i++) {
            int b = staff.getCompound(i).getInt("birthday"), d = b - calendarSeason * length;
            if (b >= 0 && d >= 0 && d < length) {
                events.computeIfAbsent(d, k -> new ArrayList<>()).add(staff.getCompound(i).getString("name") + "'s birthday");
                marks.merge(d, 2, (a, b2) -> a | b2);
            }
        }
        for (int d = 0; d < length; d++) {
            int cell = lead + d;
            int cx = gx + (cell % 7) * cw, cy = gy + (cell / 7) * ch;
            long abs = first + d;
            int weekday = Schedule.weekday(abs);
            boolean isToday = abs == today, past = abs < today, closed = (mask & (1 << weekday)) == 0;
            boolean hover = over(mx, my, cx, cy, cw - 1, ch - 1);
            int fill = isToday ? 0xFFE85C5C : past ? 0xFFE2D8CC : closed ? INSET_DARK : hover ? SLOT_HOVER : SLOT;
            int edge = isToday ? 0xFF9E3A30 : past ? 0xFFC4B6A6 : SLOT_EDGE;
            round(g, cx, cy, cx + cw - 1, cy + ch - 1, fill, edge);
            String num = String.valueOf(d + 1);
            text(g, num, cx + cw - 3 - font.width(num), cy + ch - 10, isToday ? 0xFFFFFFFF : past ? 0xFFB3A595 : closed ? INK_SOFT : INK);
            int m = marks.getOrDefault(d, 0);
            if ((m & 1) != 0) star(g, cx + 2, cy + 2, !past);
            if ((m & 2) != 0) heart(g, cx + ((m & 1) != 0 ? 8 : 2), cy + 2, !past);
            if (weekday == criticDay) g.fill(cx + 2, cy + ch - 5, cx + 5, cy + ch - 3, past ? 0xFFC4B6A6 : CRITIC);
            final int day = d;
            if (hover) {
                List<String> lines = new ArrayList<>();
                lines.add(Schedule.DAY_NAMES[weekday] + ", " + season.label() + " " + (d + 1) + (isToday ? " (today)" : ""));
                lines.addAll(events.getOrDefault(d, List.of()));
                if (weekday == criticDay) lines.add("Critics may visit");
                if (closed) lines.add("Closed");
                tooltip = lines.stream().map(l -> Component.literal(l).getVisualOrderText()).toList();
            }
            region(cx, cy, cw - 1, ch - 1, () -> {
                Holiday h = Arrays.stream(Holiday.values()).filter(hh -> hh.dayOfYear(length) == calendarSeason * length + day).findFirst().orElse(null);
                slot = h != null ? ServerScreens.slotKey(h) : ServerScreens.slotKey(season);
                showHours = false;
                pickScroll = 0;
            });
        }
        int weeks = (lead + length + 6) / 7;
        int ly = gy + weeks * ch + 4;
        int lx = gx;
        star(g, lx, ly + 1, true);
        small(g, "Holiday", lx + 7, ly + 1, INK_SOFT);
        lx += 7 + (int) (font.width("Holiday") * 0.75f) + 8;
        heart(g, lx, ly + 1, true);
        small(g, "Birthday", lx + 7, ly + 1, INK_SOFT);
        lx += 7 + (int) (font.width("Birthday") * 0.75f) + 8;
        g.fill(lx, ly + 2, lx + 3, ly + 4, CRITIC);
        small(g, "Critics", lx + 5, ly + 1, INK_SOFT);
        lx += 5 + (int) (font.width("Critics") * 0.75f) + 8;
        round(g, lx, ly, lx + 6, ly + 6, INSET_DARK, SLOT_EDGE);
        small(g, "Closed", lx + 8, ly + 1, INK_SOFT);

        List<Event> next = upcoming(length, dayOfYear, staff);
        int by = ly + 14, bh = 15 + next.size() * 11;
        inset(g, x, by, w, bh);
        small(g, "COMING UP", x + 6, by + 5, INK_SOFT);
        for (int i = 0; i < next.size(); i++) {
            Event e = next.get(i);
            int ey = by + 15 + i * 11;
            if (e.holiday) star(g, x + 6, ey + 1, true);
            else heart(g, x + 6, ey + 1, true);
            String when = e.in == 0 ? "today" : e.in == 1 ? "tomorrow" : "in " + e.in + " days";
            text(g, fit(e.name, w - 26 - font.width(when)), x + 15, ey, INK);
            text(g, when, x + w - 6 - font.width(when), ey, i == 0 ? ACCENT : INK_SOFT);
        }

        drawCalendarRight(g, mx, my);
    }

    private List<Event> upcoming(int length, int dayOfYear, ListTag staff) {
        int year = length * 4;
        List<Event> all = new ArrayList<>();
        for (Holiday h : Holiday.values()) all.add(new Event(h.label, Math.floorMod(h.dayOfYear(length) - dayOfYear, year), true));
        for (int i = 0; i < staff.size(); i++) {
            int b = staff.getCompound(i).getInt("birthday");
            if (b >= 0) all.add(new Event(staff.getCompound(i).getString("name") + "'s birthday", Math.floorMod(b - dayOfYear, year), false));
        }
        all.sort(Comparator.comparingInt(Event::in));
        return all.subList(0, Math.min(3, all.size()));
    }

    private void drawCalendarRight(GuiGraphics g, int mx, int my) {
        int x = rx(), w = pageW();
        chips(g, mx, my, x + 14, top + 9, w - 28, new String[]{"Dishes", "Opening hours"}, showHours ? 1 : 0, i -> showHours = i == 1);
        if (showHours) {
            drawHours(g, mx, my, x, w);
            return;
        }
        boolean season = slot.startsWith("S:");

        int slotsShown = season ? 5 : 3;
        ListTag items = data.getCompound("slots").getList(slot, Tag.TAG_STRING);
        Set<String> active = new HashSet<>();
        data.getList("activeSlots", Tag.TAG_STRING).forEach(t -> active.add(t.getAsString()));
        String title = slotName(slot);
        text(g, title, x, top + 30, ACCENT);
        if (active.contains(slot)) {
            int px = x + font.width(title) + 5;
            round(g, px, top + 29, px + (int) (font.width("on now") * 0.75f) + 8, top + 39, MINT, darken(MINT));
            small(g, "on now", px + 4, top + 31, 0xFF2F4D20);
        }
        hint(mx, my, x, top + 28, w, 12, season ? "Served every day this season" : "Served on the day only");
        slotsShown = Math.max(slotsShown, items.size() + 1);
        int perRow = 6, slotRows = (slotsShown + perRow - 1) / perRow;
        for (int i = 0; i < slotsShown; i++) {
            int sx = x + (i % perRow) * 26, sy = top + 42 + (i / perRow) * 26;
            boolean has = i < items.size();
            boolean hover = has && over(mx, my, sx, sy, 22, 22);
            slot(g, sx, sy, 22, hover);
            if (!has) continue;
            String id = items.getString(i);
            ItemStack s = stack(id);
            g.renderItem(s, sx + 3, sy + 3);
            if (hover) {
                tooltip = List.of(s.getHoverName().getVisualOrderText(), Component.literal("Click to take off").withColor(0xFFA8998A).getVisualOrderText());
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                round(g, sx + 14, sy - 3, sx + 23, sy + 6, 0xFFE07A6A, 0xFF9E3A30);
                text(g, "x", sx + 16, sy - 3, 0xFFFFFFFF);
                g.pose().popPose();
            }
            CompoundTag args = new CompoundTag();
            args.putString("slot", slot);
            args.putString("id", id);
            region(sx, sy, 22, 22, () -> send("slot_remove", args));
        }
        int below = top + 42 + slotRows * 26 + 2;
        searchBar(g, pickSearch, x, below, w);
        String query = pickSearch.getValue().toLowerCase(Locale.ROOT);
        List<ItemStack> matches = allFood().stream().filter(s -> matches(s, query)).toList();
        pickScroll = grid(g, mx, my, x, below + 18, w, 8, bottom() - (below + 18), matches, pickScroll, s -> {
            CompoundTag args = new CompoundTag();
            args.putString("slot", slot);
            args.putString("id", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            send("slot_add", args);
        }, s -> false, s -> {
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            for (int i = 0; i < items.size(); i++) if (items.getString(i).equals(id)) return true;
            return false;
        });
    }

    private void drawHours(GuiGraphics g, int mx, int my, int x, int w) {
        int y = top + 30;
        int mask = data.getInt("days");
        int bw = (w - 6) / 7;
        for (int d = 0; d < 7; d++) {
            boolean on = (mask & (1 << d)) != 0;
            CompoundTag args = new CompoundTag();
            args.putInt("v", d);
            int bxx = x + d * (bw + 1);
            boolean hover = over(mx, my, bxx, y, bw, 14);
            round(g, bxx, y, bxx + bw, y + 14, on ? (hover ? 0xFFB3DCA0 : 0xFF9FD08A) : hover ? SLOT_HOVER : INSET_DARK, on ? 0xFF4E7A35 : SLOT_EDGE);
            centred(g, Schedule.DAY_NAMES[d].substring(0, 2), bxx + bw / 2, y + 3, on ? 0xFF2F4D20 : INK_SOFT);
            region(bxx, y, bw, 14, () -> send("day", args));
            if (hover) tooltip = List.of(Component.literal(on ? "Open on " + Schedule.DAY_NAMES[d] : "Closed on " + Schedule.DAY_NAMES[d]).getVisualOrderText());
        }
        y += 24;

        int open = data.getInt("openTime"), close = data.getInt("closeTime");
        int barW = w - 2;
        round(g, x, y, x + barW + 2, y + 12, 0xFF5A6E8C, OUTLINE);
        g.fill(x + 1, y + 1, x + 1 + barW / 2, y + 11, 0xFFFFE6A8);
        int a = x + 1 + (int) (barW * (long) open / 24000L), b = x + 1 + (int) (barW * (long) close / 24000L);
        if (b >= a) {
            g.fill(a, y + 3, b, y + 9, MINT);
        } else {
            g.fill(a, y + 3, x + 1 + barW, y + 9, MINT);
            g.fill(x + 1, y + 3, b, y + 9, MINT);
        }
        small(g, "06:00", x, y + 14, INK_SOFT);
        small(g, "noon", x + barW / 4 - 6, y + 14, INK_SOFT);
        small(g, "18:00", x + barW / 2 - 8, y + 14, INK_SOFT);
        small(g, "midnight", x + barW * 3 / 4 - 12, y + 14, INK_SOFT);
        y += 30;
        timeRow(g, mx, my, x, y, "Opens", "open", open);
        timeRow(g, mx, my, x, y + 20, "Closes", "close", close);
        hint(mx, my, x, y + 20, 36, 14, "Anyone waiting leaves at closing.", "Anyone eating gets to finish.");
    }

    private void timeRow(GuiGraphics g, int mx, int my, int x, int y, String label, String which, int ticks) {
        text(g, label, x, y + 3, INK);
        int bx = x + 44;
        int[] deltas = {-1000, -250, 0, 250, 1000};
        String[] labels = {"<<", "<", null, ">", ">>"};
        String[] hints = {"An hour earlier", "15 minutes earlier", null, "15 minutes later", "An hour later"};
        for (int i = 0; i < deltas.length; i++) {
            if (labels[i] == null) {
                inset(g, bx, y, 36, 14);
                centred(g, Schedule.clock(ticks), bx + 18, y + 3, INK);
                bx += 38;
                continue;
            }
            CompoundTag args = new CompoundTag();
            args.putString("which", which);
            args.putInt("v", deltas[i]);
            int bw = button(g, mx, my, bx, y, labels[i], Style.PLAIN, () -> send("time", args));
            hint(mx, my, bx, y, bw, 13, hints[i]);
            bx += bw + 2;
        }
    }

    private void drawStaff(GuiGraphics g, int mx, int my) {
        int x = lx(), w = pageW();
        ListTag staff = data.getList("staff", Tag.TAG_COMPOUND);
        heading(g, "Staff " + staff.size() + "/8", x + w / 2, top + 12);
        int areaTop = top + 28, cardH = 26, rows = Math.max(1, (bottom() - areaTop - 20) / (cardH + 2));
        staffScroll = Mth.clamp(staffScroll, 0, Math.max(0, staff.size() - rows));
        if (staff.isEmpty()) centred(g, "No staff yet", x + w / 2, areaTop + 20, INK_SOFT);
        int cardW = staff.size() > rows ? w - 6 : w;
        for (int i = staffScroll; i < staff.size() && i < staffScroll + rows; i++) {
            CompoundTag s = staff.getCompound(i);
            int y = areaTop + (i - staffScroll) * (cardH + 2);
            boolean sel = i == selectedStaff, hover = over(mx, my, x, y, cardW, cardH);
            round(g, x, y, x + cardW, y + cardH, sel ? SLOT_HOVER : hover ? 0xFFF6D8AE : INSET, sel ? ACCENT : INSET_EDGE);
            round(g, x + 3, y + 3, x + 23, y + 23, SLOT, SLOT_EDGE);
            face(g, s.getString("skin"), x + 5, y + 5, 16);
            text(g, fit(s.getString("name"), cardW - 70), x + 28, y + 4, INK);
            String title = s.getString("title");
            small(g, title, x + 28, y + 15, INK_SOFT);
            int lx = x + 28 + (int) (font.width(title) * 0.75f) + 4;
            stars(g, lx, y + 15, s.getInt("level"));
            hint(mx, my, lx, y + 14, 35, 7, "Level " + s.getInt("level"));
            for (int h = 0; h < 5; h++) heart(g, x + cardW - 36 + h * 6, y + 5, h < s.getInt("hearts"));
            if (s.getBoolean("owed")) {
                round(g, x + cardW - 13, y + 13, x + cardW - 4, y + 22, 0xFFE07A6A, 0xFF9E3A30);
                text(g, "!", x + cardW - 9, y + 14, 0xFFFFFFFF);
                hint(mx, my, x + cardW - 13, y + 13, 9, 9, "Owed wages");
            } else if (s.getInt("birthdayIn") == 0) {
                bigItem(g, new ItemStack(Items.CAKE), x + cardW - 14, y + 12, 0.6f);
                hint(mx, my, x + cardW - 14, y + 12, 10, 10, "Birthday today");
            }
            final int index = i;
            region(x, y, cardW, cardH, () -> {
                selectedStaff = index;
                confirmFire = -1;
            });
        }
        scrollbar(g, x + w - 4, areaTop, rows * (cardH + 2) - 2, staffScroll, Math.max(0, staff.size() - rows));
        int hire = data.getInt("hireCost");
        int by = bottom() - 14, bx = x;
        String costLine = "Costs " + hire + " " + data.getString("currency");
        bx += button(g, mx, my, bx, by, "+ Waiter", Style.PRIMARY, () -> send("hire", "role", "WAITER"));
        hint(mx, my, x, by, bx - x, 13, "Hire a waiter", costLine);
        int cx = bx + 3;
        bx = cx + button(g, mx, my, cx, by, "+ Cashier", Style.PRIMARY, () -> send("hire", "role", "CASHIER"));
        hint(mx, my, cx, by, bx - cx, 13, "Hire a cashier", costLine);
        bigItem(g, moneyIcon(), bx + 5, by + 2, 0.6f);
        text(g, String.valueOf(hire), bx + 16, by + 3, INK_SOFT);

        drawStaffPage(g, mx, my, staff);
    }

    private void drawStaffPage(GuiGraphics g, int mx, int my, ListTag staff) {
        int x = rx(), w = pageW();
        int length = Math.max(1, data.getInt("seasonLength"));
        if (selectedStaff >= staff.size()) selectedStaff = -1;
        if (selectedStaff < 0) {
            heading(g, "Wages", x + w / 2, top + 12);
            wageBoard(g, mx, my, x, top + 28, w);
            return;
        }
        CompoundTag s = staff.getCompound(selectedStaff);
        heading(g, s.getString("name"), x + w / 2, top + 12);
        photo(g, s.getString("skin"), x, top + 26);
        text(g, s.getString("title"), x + 46, top + 28, INK);
        levelAndHearts(g, mx, my, x + 46, top + 41, top + 53, s.getInt("level"), s.getInt("hearts"));
        int y = top + 72;
        int bday = s.getInt("birthday");
        String when = bday < 0 ? "Not known yet" : Season.values()[Math.min(3, bday / length)].label() + " " + (bday % length + 1)
                + (s.getInt("birthdayIn") == 0 ? ", today" : "");
        infoRow(g, x, y, "Wage", s.getInt("wage") + " a day");
        infoRow(g, x, y + 12, "Birthday", when);
        text(g, "Loves", x, y + 25, INK_SOFT);
        ItemStack cake = stack(s.getString("cake"));
        icon(g, mx, my, cake, x + 46, y + 21);
        text(g, fit(cake.getHoverName().getString(), pageW() - 64), x + 64, y + 25, INK);
        if (s.getBoolean("helps")) small(g, "Helps in the kitchen", x, y + 41, GOOD);
        int by = y + 52;
        CompoundTag args = new CompoundTag();
        args.putInt("index", selectedStaff);
        int bx = x;
        if (s.getInt("level") < 5) {
            int bw = button(g, mx, my, bx, by, "Upgrade " + s.getInt("upgrade"), Style.PRIMARY, () -> send("upgrade", args));
            hint(mx, my, bx, by, bw, 13, "Faster and steadier, costs " + s.getInt("upgrade") + " " + data.getString("currency"));
            bx += bw + 3;
        }
        boolean confirming = confirmFire == selectedStaff;
        button(g, mx, my, bx, by, confirming ? "Really let go?" : "Let go", Style.DANGER, () -> {
            if (confirming) {
                send("dismiss", args);
                selectedStaff = -1;
                confirmFire = -1;
            } else {
                confirmFire = selectedStaff;
            }
        });
        int boardH = wageBoardHeight();
        wageBoard(g, mx, my, x, bottom() - boardH, w);
    }

    private int wageBoardHeight() {
        return data.getInt("wagesDue") > 0 || data.getInt("wageFund") > 0 ? 44 : 30;
    }

    private void wageBoard(GuiGraphics g, int mx, int my, int x, int y, int w) {
        int h = wageBoardHeight();
        board(g, x, y, w, h);
        g.renderItem(moneyIcon(), x + 5, y + 6);
        g.drawString(font, "Wage fund: " + data.getInt("wageFund"), x + 24, y + 5, BOARD_TEXT, false);
        int due = data.getInt("wagesDue");
        g.drawString(font, due > 0 ? "Due today: " + due : "Everyone's paid", x + 24, y + 16, due > 0 ? PEACH : 0xFFA8E6B8, false);
        hint(mx, my, x, y, w, 26, "Paid out at closing.", "Sneak-right-click a till with " + data.getString("currency") + " to add.");
        int bx = x + 5;
        if (due > 0) bx += button(g, mx, my, bx, y + 28, "Pay now", Style.PRIMARY, () -> send("pay", new CompoundTag())) + 3;
        if (data.getInt("wageFund") > 0) button(g, mx, my, bx, y + 28, "Withdraw", Style.PLAIN, () -> send("withdraw", new CompoundTag()));
    }

    private void infoRow(GuiGraphics g, int x, int y, String label, String value) {
        text(g, label, x, y, INK_SOFT);
        text(g, fit(value, pageW() - 46), x + 46, y, INK);
    }

    private static final String[] GUEST_FILTERS = {"All", "Regular", "Critic", "Closer", "Kid"};

    private boolean guestMatches(CompoundTag p, int filter) {
        int type = p.getInt("type");
        boolean kid = p.getBoolean("kid");
        return switch (filter) {
            case 1 -> type == 0 && !kid;
            case 2 -> type == 1;
            case 3 -> type == 2;
            case 4 -> kid;
            default -> true;
        };
    }

    private void drawRegulars(GuiGraphics g, int mx, int my) {
        int x = lx(), w = pageW();
        heading(g, "Regulars", x + w / 2, top + 12);
        searchBar(g, guestSearch, x, top + 26, w);
        chips(g, mx, my, x, top + 44, w, GUEST_FILTERS, guestFilter, i -> {
            guestFilter = i;
            guestScroll = 0;
        });
        if (!data.contains("profiles")) {
            centred(g, "Opening the guest book...", x + w / 2, top + 90, INK_SOFT);
            drawGuestPage(g, mx, my, null, new ListTag());
            return;
        }
        ListTag profiles = data.getList("profiles", Tag.TAG_COMPOUND);
        String query = guestSearch.getValue().toLowerCase(Locale.ROOT);
        List<CompoundTag> shown = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            CompoundTag p = profiles.getCompound(i);
            if (guestMatches(p, guestFilter) && (query.isEmpty() || p.getString("name").toLowerCase(Locale.ROOT).contains(query))) shown.add(p);
        }
        int gy = top + 62;
        guestScroll = grid(g, mx, my, x, gy, w, 7, bottom() - gy, shown, guestScroll,
                p -> selectedGuest = p.getString("name"), p -> p.getString("name").equals(selectedGuest), p -> false,
                (p, sx, sy, size, pad, hover) -> {
                    face(g, p.getString("skin"), sx + pad, sy + pad - 1, 16);
                    for (int st = 0; st < Math.min(5, p.getInt("stars")); st++) g.fill(sx + pad + st * 3, sy + size - 4, sx + pad + 2 + st * 3, sy + size - 3, GOLD);
                    if (hover) tooltip = List.of(Component.literal(p.getString("name")).getVisualOrderText());
                });
        CompoundTag selected = null;
        for (CompoundTag p : shown) if (p.getString("name").equals(selectedGuest)) selected = p;
        if (shown.isEmpty()) centred(g, profiles.isEmpty() ? "Nobody's visited yet" : "No matches", x + w / 2, gy + 30, INK_SOFT);
        drawGuestPage(g, mx, my, selected, profiles);
    }

    private void drawGuestPage(GuiGraphics g, int mx, int my, CompoundTag p, ListTag profiles) {
        int x = rx(), w = pageW();
        if (p == null) {
            heading(g, "Guest book", x + w / 2, top + 12);
            String[] names = {"Regulars", "Critics", "Closers", "Little ones"};
            int y = top + 28;
            for (int f = 1; f <= 4; f++) {
                int n = 0;
                for (int i = 0; i < profiles.size(); i++) if (guestMatches(profiles.getCompound(i), f)) n++;
                final int filter = f;
                countRow(g, mx, my, x, y, w, names[f - 1], n, () -> {
                    guestFilter = filter;
                    guestScroll = 0;
                });
                y += 24;
            }
            return;
        }
        int type = p.getInt("type");
        String kind = p.getBoolean("kid") ? "Little one" : type == 1 ? "Food critic" : type == 2 ? "Closer" : "Regular";
        heading(g, p.getString("name"), x + w / 2, top + 12);
        photo(g, p.getString("skin"), x, top + 26);
        text(g, kind, x + 46, top + 28, type == 1 ? 0xFFB8860B : type == 2 ? CRITIC : INK);
        stars(g, x + 46, top + 41, p.getInt("stars"));
        hint(mx, my, x + 46, top + 40, 35, 7, "Grows when you serve them well");
        int visits = p.getInt("visits");
        small(g, visits + " visit" + (visits == 1 ? "" : "s"), x + 46, top + 53, INK_SOFT);

        text(g, "Favourites", x, top + 76, ACCENT);
        ListTag favs = p.getList("favs", Tag.TAG_COMPOUND);
        for (int i = 0; i < favs.size() && i < 6; i++) {
            CompoundTag fav = favs.getCompound(i);
            int sx = x + i * 26, sy = top + 88;
            slot(g, sx, sy, 22, false);
            ItemStack s = stack(fav.getString("id"));
            g.renderItem(s, sx + 3, sy + 3);
            boolean onToday = fav.getBoolean("today");
            if (!onToday) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(sx + 1, sy + 1, sx + 21, sy + 21, 0x99F7DCB4);
                g.pose().popPose();
            }
            if (over(mx, my, sx, sy, 22, 22)) {
                tooltip = onToday ? List.of(s.getHoverName().getVisualOrderText())
                        : List.of(s.getHoverName().getVisualOrderText(), Component.literal("Not on today's menu").withColor(0xFFA8998A).getVisualOrderText());
            }
        }
        if (favs.isEmpty()) small(g, "Still deciding", x, top + 90, INK_SOFT);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : 1;
        boolean leftPage = mx < mid();
        switch (tab) {
            case MENU -> { if (leftPage) menuScroll += step; }
            case CALENDAR -> { if (!leftPage) pickScroll += step; }
            case FLOOR -> { if (!leftPage) floorScroll += step; }
            case STAFF -> { if (leftPage) staffScroll += step; }
            case REGULARS -> { if (leftPage) guestScroll += step; }
            default -> {}
        }
        return true;
    }
}
