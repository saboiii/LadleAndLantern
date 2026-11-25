package com.ladleandlantern.core.restaurant;

import com.ladleandlantern.LadleAndLantern;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class RestaurantStore {
    private static final long FLUSH_INTERVAL_MS = 2000;
    private static RestaurantStore instance;

    private final Path file;
    private final Map<UUID, Restaurant> restaurants = new LinkedHashMap<>();
    private final Map<UUID, PlayerProgress> players = new HashMap<>();
    private boolean dirty;
    private long lastFlush;

    private long calendarDay = -1;
    private long lastDayTime = -1;

    private RestaurantStore(Path file) {
        this.file = file;
    }

    public static RestaurantStore get(MinecraftServer server) {
        if (instance == null) {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("data");
            instance = new RestaurantStore(dir.resolve("ladle_and_lantern.dat"));
            instance.load();
        }
        return instance;
    }

    public static void close() {
        if (instance != null) instance.saveNow();
        instance = null;
    }

    public Collection<Restaurant> all() {
        return restaurants.values();
    }

    public Optional<Restaurant> byId(UUID id) {
        return Optional.ofNullable(id == null ? null : restaurants.get(id));
    }

    public Optional<Restaurant> at(ResourceKey<Level> dim, BlockPos pos) {
        return restaurants.values().stream().filter(r -> r.dimension.equals(dim) && r.contains(pos)).findFirst();
    }

    public Optional<Restaurant> byCashier(ResourceKey<Level> dim, BlockPos pos) {
        return restaurants.values().stream().filter(r -> r.dimension.equals(dim) && r.cashiers.contains(pos)).findFirst();
    }

    public Optional<Restaurant> near(ResourceKey<Level> dim, BlockPos pos, int range) {
        return restaurants.values().stream().filter(r -> r.dimension.equals(dim) && r.nearBounds(pos, range)).findFirst();
    }

    public Restaurant create(ResourceKey<Level> dim, BlockPos centre, UUID owner) {
        Restaurant r = new Restaurant(UUID.randomUUID(), dim, centre.offset(-8, -1, -8), centre.offset(8, 6, 8));
        r.owner = owner;
        restaurants.put(r.id, r);
        markDirty();
        return r;
    }

    public long day() {
        return Math.max(0, calendarDay);
    }

    public void setDay(long day) {
        calendarDay = Math.max(0, day);
        markDirty();
    }

    public boolean tickCalendar(long dayTime) {
        if (calendarDay < 0) calendarDay = dayTime / 24000L;
        boolean newDay = false;
        if (lastDayTime >= 0) {
            long days = dayTime / 24000L - lastDayTime / 24000L;
            boolean wrapped = dayTime % 24000L < lastDayTime % 24000L - 1000;
            if (days > 0 || wrapped) {
                calendarDay += Math.max(1, Math.min(days, 7));
                newDay = true;
                markDirty();
            }
        }
        lastDayTime = dayTime;
        return newDay;
    }

    public PlayerProgress progress(UUID player) {
        return players.computeIfAbsent(player, k -> new PlayerProgress());
    }

    public void markDirty() {
        dirty = true;
    }

    public void flushIfDue() {
        long now = System.currentTimeMillis();
        if (dirty && now - lastFlush >= FLUSH_INTERVAL_MS) saveNow();
    }

    public void saveNow() {
        lastFlush = System.currentTimeMillis();
        if (!dirty) return;
        CompoundTag root = new CompoundTag();
        root.putInt("version", SAVE_VERSION);
        ListTag list = new ListTag();
        restaurants.values().forEach(r -> list.add(r.save()));

        unreadable.forEach(t -> list.add(t.copy()));
        root.put("restaurants", list);
        CompoundTag progress = new CompoundTag();
        unreadablePlayers.getAllKeys().forEach(k -> progress.put(k, unreadablePlayers.get(k).copy()));
        players.forEach((id, p) -> progress.put(id.toString(), p.save()));
        root.put("players", progress);
        root.putLong("calendarDay", calendarDay);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                dailyBackup();
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException e) {
            LadleAndLantern.LOGGER.error("Could not save restaurants to {}", file, e);
        }
    }

    private final List<CompoundTag> unreadable = new ArrayList<>();
    private final CompoundTag unreadablePlayers = new CompoundTag();
    private static final int SAVE_VERSION = 1;

    private void load() {
        Path backup = file.resolveSibling(file.getFileName() + ".bak");
        if (Files.exists(file) && !tryLoad(file)) {
            keepCopy(file, "unreadable");
            restaurants.clear();
            players.clear();
            unreadable.clear();
            if (Files.exists(backup)) {
                LadleAndLantern.LOGGER.warn("Main restaurant save unreadable, loading backup {}", backup);
                keepCopy(backup, "backup-used");
                if (tryLoad(backup)) markDirty();
            }
        }
        lastFlush = System.currentTimeMillis();
    }

    private boolean tryLoad(Path path) {
        if (!Files.exists(path)) return false;
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            LadleAndLantern.LOGGER.error("Could not read restaurant save {}", path, e);
            return false;
        }
        if (root.getInt("version") > SAVE_VERSION) {
            LadleAndLantern.LOGGER.warn("Restaurant save {} is from a newer version of Ladle & Lantern; a copy has been kept", path);
            keepCopy(path, "from-newer-version");
        }

        for (Tag e : root.getList("restaurants", Tag.TAG_COMPOUND)) {
            try {
                Restaurant r = Restaurant.load((CompoundTag) e);
                restaurants.put(r.id, r);
            } catch (Exception ex) {
                LadleAndLantern.LOGGER.error("Skipping a restaurant that couldn't be read; it is kept in the save untouched", ex);
                unreadable.add((CompoundTag) e);
            }
        }
        if (root.contains("calendarDay")) calendarDay = root.getLong("calendarDay");
        CompoundTag progress = root.getCompound("players");
        for (String key : progress.getAllKeys()) {
            try {
                players.put(UUID.fromString(key), PlayerProgress.load(progress.getCompound(key)));
            } catch (Exception ex) {
                LadleAndLantern.LOGGER.error("Skipping player progress {} that couldn't be read; it is kept in the save untouched", key, ex);
                if (progress.get(key) != null) unreadablePlayers.put(key, progress.get(key).copy());
            }
        }
        return true;
    }

    private void keepCopy(Path path, String why) {
        try {
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Files.copy(path, path.resolveSibling(file.getFileName() + "." + why + "." + stamp), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LadleAndLantern.LOGGER.error("Could not keep a copy of {}", path, e);
        }
    }

    private void dailyBackup() {
        try {
            String today = java.time.LocalDate.now().toString();
            Path daily = file.resolveSibling(file.getFileName() + ".daily-" + today);
            if (Files.exists(daily)) return;
            Files.copy(file, daily);
            try (var all = Files.list(file.getParent())) {
                List<Path> old = all.filter(p -> p.getFileName().toString().startsWith(file.getFileName() + ".daily-")).sorted().toList();
                for (int i = 0; i < old.size() - 7; i++) Files.deleteIfExists(old.get(i));
            }
        } catch (IOException e) {
            LadleAndLantern.LOGGER.error("Could not write the daily restaurant backup", e);
        }
    }
}
