package com.ladleandlantern.core.restaurant;

import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.restaurant.GameCalendar.Holiday;
import com.ladleandlantern.core.restaurant.GameCalendar.Season;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class Restaurant {
    public static final int MAX_SIZE = 96;
    public static final int SIGN_RANGE = 5;
    public static final int MAX_WAITING_LINE = 5;

    public final UUID id;
    public final ResourceKey<Level> dimension;
    public UUID owner;
    public String name = "My Restaurant";
    public String cuisine = "Homestyle";
    public BlockPos min;
    public BlockPos max;

    private transient BoundingBox bounds;

    public final List<BlockPos> cashiers = new ArrayList<>();
    public final List<Seat> seats = new ArrayList<>();
    public final Set<BlockPos> counter = new LinkedHashSet<>();
    public final Set<BlockPos> trays = new LinkedHashSet<>();

    public final Set<BlockPos> dirtyTables = new LinkedHashSet<>();

    public final Set<BlockPos> tableCards = new LinkedHashSet<>();
    public BlockPos entrance;
    public BlockPos signboard;

    public final List<BlockPos> waitingLine = new ArrayList<>();

    public final Map<ResourceLocation, Course> menu = new LinkedHashMap<>();
    public final EnumMap<Season, List<ResourceLocation>> seasonal = new EnumMap<>(Season.class);
    public final EnumMap<Holiday, List<ResourceLocation>> holidays = new EnumMap<>(Holiday.class);
    public ResourceLocation special;
    public final Schedule schedule = new Schedule();

    public final Map<UUID, CustomerProfile> customers = new LinkedHashMap<>();
    public final List<StaffRecord> staff = new ArrayList<>();

    public boolean shiftOpen;
    public int shiftOrders;
    public int shiftScoreTotal;
    public int shiftWalkouts;
    public int ticketCounter;

    public int bestRank = 1;

    public int till;

    public int shiftArrivals;

    public int shiftEarnings;
    public int shiftTips;
    public int shiftNewFaces;
    public int shiftBestScore;
    public String shiftBestDish = "";

    public transient int shiftTipCount;

    public boolean ritualPending;
    public int shiftCritics;
    public int shiftClosers;

    public int wageFund;

    public transient boolean coupleToday;
    public boolean catToday;

    public final Map<String, Long> remarks = new HashMap<>();

    public final Map<BlockPos, Fixture> fixtures = new LinkedHashMap<>();

    public transient int spawnCooldown;
    private transient int layoutVersion;
    private transient int labelsVersion = -1;
    private transient Map<BlockPos, String> labelsCache;
    private transient int blockedVersion = -1;
    private transient it.unimi.dsi.fastutil.longs.LongOpenHashSet blockedCache;

    public Restaurant(UUID id, ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
        this.id = id;
        this.dimension = dimension;
        setBounds(min, max);
        for (Season s : Season.values()) seasonal.put(s, new ArrayList<>());
        for (Holiday h : Holiday.values()) holidays.put(h, new ArrayList<>());
    }

    public void setBounds(BlockPos a, BlockPos b) {
        layoutVersion++;
        bounds = BoundingBox.fromCorners(a, b);
        min = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        max = new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    public static boolean validSize(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) < MAX_SIZE && Math.abs(a.getY() - b.getY()) < MAX_SIZE
                && Math.abs(a.getZ() - b.getZ()) < MAX_SIZE;
    }

    public boolean contains(BlockPos p) {
        return bounds.isInside(p);
    }

    public boolean nearBounds(BlockPos p, int range) {
        return bounds.inflatedBy(range).isInside(p);
    }

    public Vec3 centre() {
        return box().getCenter();
    }

    public AABB box() {
        return new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1, 1, 1));
    }

    public AABB searchBox() {
        return box().inflate(32);
    }

    public Optional<Seat> seatAt(BlockPos p) {
        return seats.stream().filter(s -> s.pos.equals(p)).findFirst();
    }

    public void layoutChanged() {
        layoutVersion++;
    }

    public it.unimi.dsi.fastutil.longs.LongOpenHashSet noWalk() {
        if (blockedVersion != layoutVersion || blockedCache == null) {
            it.unimi.dsi.fastutil.longs.LongOpenHashSet set = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
            for (Seat s : seats) {
                set.add(s.pos.asLong());
                s.table.forEach(t -> set.add(t.asLong()));
            }
            counter.forEach(c -> set.add(c.asLong()));
            cashiers.forEach(c -> set.add(c.asLong()));
            trays.forEach(t -> set.add(t.asLong()));
            fixtures.keySet().forEach(f -> set.add(f.asLong()));
            blockedCache = set;
            blockedVersion = layoutVersion;
        }
        return blockedCache;
    }

    public List<BlockPos> fixtures(Fixture kind) {
        List<BlockPos> out = new ArrayList<>();
        fixtures.forEach((p, k) -> { if (k == kind) out.add(p); });
        return out;
    }

    public boolean seatDirty(Seat seat) {
        if (dirtyTables.isEmpty()) return false;
        for (BlockPos t : seat.table) if (dirtyTables.contains(t.above())) return true;
        return false;
    }

    public List<List<BlockPos>> tableGroups() {
        Map<String, List<BlockPos>> groups = new LinkedHashMap<>();
        seatLabels().forEach((pos, label) -> groups.computeIfAbsent(label.replaceAll("[A-Z]$", ""), k -> new ArrayList<>()).add(pos));
        return new ArrayList<>(groups.values());
    }

    public void removeBlock(BlockPos p) {
        layoutChanged();
        seats.removeIf(s -> s.pos.equals(p));
        seats.forEach(s -> s.table.remove(p));
        counter.remove(p);
        trays.remove(p);
        cashiers.remove(p);
        waitingLine.remove(p);
        if (p.equals(entrance)) entrance = null;
        if (p.equals(signboard)) signboard = null;
        fixtures.remove(p);
    }

    public Map<BlockPos, String> seatLabels() {
        if (labelsVersion == layoutVersion && labelsCache != null) return labelsCache;
        labelsCache = computeSeatLabels();
        labelsVersion = layoutVersion;
        return labelsCache;
    }

    private Map<BlockPos, String> computeSeatLabels() {
        int n = seats.size();
        int[] group = new int[n];
        for (int i = 0; i < n; i++) group[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!Collections.disjoint(seats.get(i).table, seats.get(j).table)) union(group, i, j);
            }
        }
        Map<Integer, Integer> tableNumbers = new LinkedHashMap<>();
        Map<Integer, Integer> sizes = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(group, i);
            tableNumbers.putIfAbsent(root, tableNumbers.size() + 1);
            sizes.merge(root, 1, Integer::sum);
        }
        Map<Integer, Integer> letters = new HashMap<>();
        Map<BlockPos, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(group, i);
            String label = String.valueOf(tableNumbers.get(root));
            if (sizes.get(root) > 1) label += (char) ('A' + letters.merge(root, 1, Integer::sum) - 1);
            labels.put(seats.get(i).pos, label);
        }
        return labels;
    }

    private static int find(int[] g, int i) {
        while (g[i] != i) i = g[i] = g[g[i]];
        return i;
    }

    private static void union(int[] g, int a, int b) {
        g[find(g, a)] = find(g, b);
    }

    public List<Integer> floors() {
        TreeSet<Integer> ys = new TreeSet<>();
        seats.forEach(s -> ys.add(s.pos.getY()));
        cashiers.forEach(c -> ys.add(c.getY()));
        List<Integer> floors = new ArrayList<>();
        for (int y : ys) if (floors.isEmpty() || y - floors.get(floors.size() - 1) >= 3) floors.add(y);
        if (floors.isEmpty()) floors.add(min.getY());
        return floors;
    }

    public Map<ResourceLocation, Course> available(long day, int seasonLength) {
        List<ResourceLocation> today = seasonalToday(day, seasonLength);
        Set<ResourceLocation> restricted = new HashSet<>();
        seasonal.values().forEach(restricted::addAll);
        holidays.values().forEach(restricted::addAll);
        Map<ResourceLocation, Course> items = new LinkedHashMap<>();
        menu.forEach((id, course) -> { if (!restricted.contains(id) || today.contains(id)) items.put(id, course); });
        today.forEach(id -> items.putIfAbsent(id, menu.containsKey(id) ? menu.get(id) : FoodRules.classify(id)));
        return items;
    }

    public void menuChanged(@javax.annotation.Nullable ResourceLocation added, net.minecraft.util.RandomSource random) {
        List<ResourceLocation> all = catalogue();
        for (CustomerProfile p : customers.values()) {
            if (added != null && random.nextFloat() < 0.25f && !p.favourites.contains(added)) {
                if (!p.favourites.isEmpty()) p.favourites.remove(random.nextInt(p.favourites.size()));
                p.favourites.add(added);
            }
            p.refreshFavourites(all, random);
        }
    }

    public List<ResourceLocation> catalogue() {
        LinkedHashSet<ResourceLocation> all = new LinkedHashSet<>(menu.keySet());
        seasonal.values().forEach(all::addAll);
        holidays.values().forEach(all::addAll);
        return new ArrayList<>(all);
    }

    public List<ResourceLocation> seasonalToday(long day, int seasonLength) {
        List<ResourceLocation> list = new ArrayList<>(seasonal.get(GameCalendar.season(day, seasonLength)));
        for (Holiday h : GameCalendar.holidays(day, seasonLength)) list.addAll(holidays.get(h));
        return list;
    }

    public Optional<StaffRecord> staffRecord(UUID entityId) {
        return staff.stream().filter(s -> s.entityId.equals(entityId)).findFirst();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("dim", dimension.location().toString());
        if (owner != null) t.putUUID("owner", owner);
        t.putString("name", name);
        t.putString("cuisine", cuisine);
        t.putLong("min", min.asLong());
        t.putLong("max", max.asLong());
        t.put("cashiers", posList(cashiers));
        ListTag seatList = new ListTag();
        seats.forEach(s -> seatList.add(s.save()));
        t.put("seats", seatList);
        t.put("counter", posList(counter));
        t.put("trays", posList(trays));
        t.put("dirtyTables", posList(dirtyTables));
        t.put("tableCards", posList(tableCards));
        if (entrance != null) t.putLong("entrance", entrance.asLong());
        if (signboard != null) t.putLong("signboard", signboard.asLong());
        t.put("waitingLine", posList(waitingLine));

        CompoundTag menuTag = new CompoundTag();
        menu.forEach((k, v) -> menuTag.putString(k.toString(), v.name()));
        t.put("menu", menuTag);
        CompoundTag seasonTag = new CompoundTag();
        seasonal.forEach((k, v) -> seasonTag.put(k.name(), idList(v)));
        t.put("seasonal", seasonTag);
        CompoundTag holidayTag = new CompoundTag();
        holidays.forEach((k, v) -> holidayTag.put(k.name(), idList(v)));
        t.put("holidays", holidayTag);
        if (special != null) t.putString("special", special.toString());
        t.put("schedule", schedule.save());

        ListTag profiles = new ListTag();
        customers.values().forEach(p -> profiles.add(p.save()));
        t.put("customers", profiles);
        ListTag staffList = new ListTag();
        staff.forEach(s -> staffList.add(s.save()));
        t.put("staff", staffList);

        t.putBoolean("shiftOpen", shiftOpen);
        t.putInt("shiftOrders", shiftOrders);
        t.putInt("shiftScore", shiftScoreTotal);
        t.putInt("shiftWalkouts", shiftWalkouts);
        t.putInt("ticket", ticketCounter);
        t.putInt("bestRank", bestRank);
        t.putInt("till", till);
        t.putInt("arrivals", shiftArrivals);
        t.putInt("earnings", shiftEarnings);
        t.putInt("tips", shiftTips);
        t.putInt("newFaces", shiftNewFaces);
        t.putInt("bestScore", shiftBestScore);
        t.putString("bestDish", shiftBestDish);
        t.putBoolean("ritual", ritualPending);
        t.putInt("critics", shiftCritics);
        t.putInt("closers", shiftClosers);
        t.putInt("wageFund", wageFund);
        t.putBoolean("catToday", catToday);
        CompoundTag remarkTag = new CompoundTag();
        remarks.forEach(remarkTag::putLong);
        t.put("remarks", remarkTag);
        ListTag fixtureList = new ListTag();
        fixtures.forEach((p, k) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", p.asLong());
            e.putString("kind", k.name());
            fixtureList.add(e);
        });
        t.put("fixtures", fixtureList);
        return t;
    }

    public static Restaurant load(CompoundTag t) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim")));
        Restaurant r = new Restaurant(t.getUUID("id"), dim, BlockPos.of(t.getLong("min")), BlockPos.of(t.getLong("max")));
        if (t.hasUUID("owner")) r.owner = t.getUUID("owner");
        r.name = t.getString("name");
        r.cuisine = t.getString("cuisine");
        r.cashiers.addAll(readPos(t.getList("cashiers", Tag.TAG_LONG)));
        for (Tag e : t.getList("seats", Tag.TAG_COMPOUND)) r.seats.add(Seat.load((CompoundTag) e));
        r.counter.addAll(readPos(t.getList("counter", Tag.TAG_LONG)));
        r.trays.addAll(readPos(t.getList("trays", Tag.TAG_LONG)));
        r.dirtyTables.addAll(readPos(t.getList("dirtyTables", Tag.TAG_LONG)));
        r.tableCards.addAll(readPos(t.getList("tableCards", Tag.TAG_LONG)));
        if (t.contains("entrance")) r.entrance = BlockPos.of(t.getLong("entrance"));
        if (t.contains("signboard")) r.signboard = BlockPos.of(t.getLong("signboard"));
        r.waitingLine.addAll(readPos(t.getList("waitingLine", Tag.TAG_LONG)));

        CompoundTag menuTag = t.getCompound("menu");
        for (String key : menuTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) r.menu.put(id, Course.byName(menuTag.getString(key)));
        }
        CompoundTag seasonTag = t.getCompound("seasonal");
        for (Season s : Season.values()) r.seasonal.get(s).addAll(readIds(seasonTag.getList(s.name(), Tag.TAG_STRING)));
        CompoundTag holidayTag = t.getCompound("holidays");
        for (Holiday h : Holiday.values()) r.holidays.get(h).addAll(readIds(holidayTag.getList(h.name(), Tag.TAG_STRING)));
        if (t.contains("special")) r.special = ResourceLocation.tryParse(t.getString("special"));
        r.schedule.load(t.getCompound("schedule"));

        for (Tag e : t.getList("customers", Tag.TAG_COMPOUND)) {
            CustomerProfile p = CustomerProfile.load((CompoundTag) e);
            r.customers.put(p.id, p);
        }
        for (Tag e : t.getList("staff", Tag.TAG_COMPOUND)) r.staff.add(StaffRecord.load((CompoundTag) e));

        r.shiftOpen = t.getBoolean("shiftOpen");
        r.shiftOrders = t.getInt("shiftOrders");
        r.shiftScoreTotal = t.getInt("shiftScore");
        r.shiftWalkouts = t.getInt("shiftWalkouts");
        r.ticketCounter = t.getInt("ticket");
        r.bestRank = Math.max(1, t.getInt("bestRank"));
        r.till = t.getInt("till");
        r.shiftArrivals = t.getInt("arrivals");
        r.shiftEarnings = t.getInt("earnings");
        r.shiftTips = t.getInt("tips");
        r.shiftNewFaces = t.getInt("newFaces");
        r.shiftBestScore = t.getInt("bestScore");
        r.shiftBestDish = t.getString("bestDish");
        r.ritualPending = t.getBoolean("ritual");
        r.shiftCritics = t.getInt("critics");
        r.shiftClosers = t.getInt("closers");
        r.wageFund = t.getInt("wageFund");
        r.catToday = t.getBoolean("catToday");
        CompoundTag remarkTag = t.getCompound("remarks");
        for (String k : remarkTag.getAllKeys()) r.remarks.put(k, remarkTag.getLong(k));
        for (Tag e : t.getList("fixtures", Tag.TAG_COMPOUND)) {
            Fixture kind = Fixture.byName(((CompoundTag) e).getString("kind"));
            if (kind != null) r.fixtures.put(BlockPos.of(((CompoundTag) e).getLong("pos")), kind);
        }
        return r;
    }

    private static ListTag posList(Collection<BlockPos> positions) {
        ListTag list = new ListTag();
        positions.forEach(p -> list.add(LongTag.valueOf(p.asLong())));
        return list;
    }

    private static List<BlockPos> readPos(ListTag list) {
        List<BlockPos> out = new ArrayList<>();
        for (Tag e : list) out.add(BlockPos.of(((LongTag) e).getAsLong()));
        return out;
    }

    private static ListTag idList(List<ResourceLocation> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    private static List<ResourceLocation> readIds(ListTag list) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Tag e : list) {
            ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) out.add(id);
        }
        return out;
    }
}
