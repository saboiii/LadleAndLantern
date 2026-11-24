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
        root.putInt("version", 1);
        ListTag list = new ListTag();
        restaurants.values().forEach(r -> list.add(r.save()));
        root.put("restaurants", list);
        CompoundTag progress = new CompoundTag();
        players.forEach((id, p) -> progress.put(id.toString(), p.save()));
        root.put("players", progress);
        root.putLong("calendarDay", calendarDay);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
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

    private void load() {
        Path backup = file.resolveSibling(file.getFileName() + ".bak");
        if (!tryLoad(file) && Files.exists(backup)) {
            LadleAndLantern.LOGGER.warn("Main restaurant save unreadable, loading backup {}", backup);
            restaurants.clear();
            players.clear();
            if (tryLoad(backup)) markDirty();
        }
        lastFlush = System.currentTimeMillis();
    }

    private boolean tryLoad(Path path) {
        if (!Files.exists(path)) return false;
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            for (Tag e : root.getList("restaurants", Tag.TAG_COMPOUND)) {
                Restaurant r = Restaurant.load((CompoundTag) e);
                restaurants.put(r.id, r);
            }
            if (root.contains("calendarDay")) calendarDay = root.getLong("calendarDay");
            CompoundTag progress = root.getCompound("players");
            for (String key : progress.getAllKeys()) {
                players.put(UUID.fromString(key), PlayerProgress.load(progress.getCompound(key)));
            }
            return true;
        } catch (Exception e) {
            LadleAndLantern.LOGGER.error("Could not read restaurant save {}", path, e);
            return false;
        }
    }
}
