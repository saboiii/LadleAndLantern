package com.ladleandlantern.core.shift;

import com.ladleandlantern.core.block.CashierBlock;
import com.ladleandlantern.core.network.OpenScreenPayload;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.block.Fixtures;
import com.ladleandlantern.core.entity.CustomerEntity;
import com.ladleandlantern.core.entity.RestaurantCat;
import com.ladleandlantern.core.entity.StaffEntity;
import com.ladleandlantern.core.food.Names;
import com.ladleandlantern.core.food.Order;
import com.ladleandlantern.core.food.OrderGenerator;
import com.ladleandlantern.core.restaurant.*;
import com.ladleandlantern.core.registry.BlockRegistry;
import com.ladleandlantern.core.world.Weather;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.*;

public final class ShiftManager {
    private static final float TAKEOUT_CHANCE = 0.35f;
    private static final float WET_TAKEOUT_CHANCE = 0.1f;
    private static final float RETURNING_CHANCE = 0.6f;
    private static final float KID_CHANCE = 0.08f;
    private static final float GROUP_CHANCE = 0.6f;
    private static final int MAX_PROFILES = 80;

    public static final int CRITIC_DAY = 4;

    private static final int[][] RUSHES = {{5500, 7500}, {11500, 13500}};

    private ShiftManager() {}

    public static void tick(ServerLevel level, Restaurant r) {
        if (r.cashiers.isEmpty() || !level.isLoaded(r.cashiers.get(0))) return;
        RestaurantStore store = RestaurantStore.get(level.getServer());
        long day = store.day();
        boolean open = r.schedule.isOpen(day, level.getDayTime());
        if (open && !r.shiftOpen) openShift(level, r, day);
        else if (!open && r.shiftOpen) closeShift(level, r);
        List<CustomerEntity> present = CustomerEntity.inRestaurant(level, r);
        if (level.getGameTime() % 100 == 0) Fixtures.updateStands(level, r, present, day);
        if (!open) {
            if (r.ritualPending) tryClosingRitual(level, r, present);
            StaffDinner.tick(level, r);
            return;
        }
        if (level.getGameTime() % 600 == 0) dropMissingBlocks(level, r);
        seatWaitingLine(level, r, present);
        RestaurantCat.maybeVisit(level, r);
        StaffEntity.kitchenHelp(level, r, present);
        if (--r.spawnCooldown <= 0) {
            boolean spawned = r.shiftArrivals < dailyQuota(r) && trySpawn(level, r, present, day);
            r.spawnCooldown = spawned ? spawnInterval(level, r) : 8;
        }
    }

    public static int dailyQuota(Restaurant r) {
        return Math.min(Config.BASE_PER_DAY.get() + 3 * r.bestRank, Math.max(8, r.seats.size() * 8));
    }

    public static boolean rushHour(long dayTime) {
        int t = (int) (dayTime % 24000L);
        for (int[] rush : RUSHES) if (t >= rush[0] && t < rush[1]) return true;
        return false;
    }

    private static int spawnInterval(ServerLevel level, Restaurant r) {
        int shiftSeconds = Math.floorMod(r.schedule.closeTime - r.schedule.openTime, 24000) / 20;
        int even = shiftSeconds / Math.max(1, dailyQuota(r));
        int interval = (int) (even * (0.75 + Math.random() * 0.5));
        if (rushHour(level.getDayTime())) interval /= 2;
        return Math.max(12, interval);
    }

    private static void dropMissingBlocks(ServerLevel level, Restaurant r) {
        boolean changed = r.seats.removeIf(s -> level.isLoaded(s.pos) && level.getBlockState(s.pos).isAir());
        for (Seat s : r.seats) changed |= s.table.removeIf(t -> level.isLoaded(t) && level.getBlockState(t).isAir());
        changed |= r.counter.removeIf(c -> level.isLoaded(c) && level.getBlockState(c).isAir());
        changed |= r.fixtures.keySet().removeIf(p -> level.isLoaded(p) && level.getBlockState(p).isAir());
        if (changed) {
            r.layoutChanged();
            RestaurantStore.get(level.getServer()).markDirty();
        }
    }

    public static int capacity(int rank) {
        return Math.min(Config.MAX_CUSTOMERS.get(), 3 + 2 * rank);
    }

    public static List<BlockPos> freeSeats(ServerLevel level, Restaurant r, List<CustomerEntity> present) {
        List<BlockPos> free = new ArrayList<>();
        for (Seat s : r.seats) {
            if (s.table.isEmpty() || r.seatDirty(s)) continue;
            boolean taken = false;
            for (CustomerEntity c : present) {
                if (c.occupies(s.pos)) {
                    taken = true;
                    break;
                }
            }

            if (!taken && playerSittingOn(level, s.pos)) taken = true;
            if (!taken) free.add(s.pos);
        }
        return free;
    }

    public static boolean playerSittingOn(ServerLevel level, BlockPos seat) {
        return level.isLoaded(seat) && !level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                new net.minecraft.world.phys.AABB(seat).inflate(0.2, 0.8, 0.2), net.minecraft.world.entity.Entity::isPassenger).isEmpty();
    }

    private static void openShift(ServerLevel level, Restaurant r, long day) {
        r.shiftOpen = true;
        r.shiftOrders = 0;
        r.shiftScoreTotal = 0;
        r.shiftWalkouts = 0;
        r.shiftArrivals = 0;
        r.shiftEarnings = 0;
        r.shiftTips = 0;
        r.shiftNewFaces = 0;
        r.shiftBestScore = 0;
        r.shiftBestDish = "";
        r.shiftTipCount = 0;
        r.ritualPending = false;
        r.shiftCritics = 0;
        r.catToday = false;
        r.coupleToday = false;
        r.shiftClosers = 0;
        r.spawnCooldown = 5;
        StaffDinner.stop(level, r);
        for (StaffRecord s : r.staff) if (level.getEntity(s.entityId) instanceof StaffEntity e) e.wakeUp();
        Wages.checkUnpaid(level, r, day);
        Fixtures.refillWater(level, r);
        int seasonLength = Config.SEASON_LENGTH.get();
        List<String> news = new ArrayList<>();
        news.add("Now open until " + Schedule.clock(r.schedule.closeTime));
        GameCalendar.holidays(day, seasonLength).forEach(h -> news.add("Happy " + h.label));
        r.staff.stream().filter(s -> s.birthdayToday(day, seasonLength)).forEach(s -> news.add("It's " + s.name + "'s birthday"));
        if (Schedule.weekday(day) == CRITIC_DAY && r.bestRank >= 2) news.add("Critics may visit today");
        banner(level, r, Component.literal(r.name).withStyle(ChatFormatting.GOLD), Component.literal(String.join(", ", news)));
        RestaurantStore.get(level.getServer()).markDirty();
    }

    private static void closeShift(ServerLevel level, Restaurant r) {
        r.shiftOpen = false;
        r.ritualPending = true;
        for (CustomerEntity c : CustomerEntity.inRestaurant(level, r)) {
            if (c.state().waiting()) c.walkOut(r, true);
            else if (c.state() == CustomerEntity.State.ARRIVING || c.state() == CustomerEntity.State.WAITING_LINE) c.walkOut(r, false);
        }
        RestaurantCat.sendHome(level, r);
        handOverTill(level, r);
        Wages.payFromFund(level, r, RestaurantStore.get(level.getServer()).day());
        int average = r.shiftOrders == 0 ? 0 : Math.round(r.shiftScoreTotal / (float) r.shiftOrders);
        banner(level, r, Component.literal("Closing Time").withStyle(ChatFormatting.GOLD),
                Component.literal(r.shiftOrders + " orders, " + average + "% average"
                        + (r.shiftWalkouts > 0 ? ", " + r.shiftWalkouts + " walked out" : "")));
        RestaurantStore.get(level.getServer()).markDirty();
    }

    private static void markLeftovers(ServerLevel level, Restaurant r) {
        for (Seat seat : r.seats) {
            for (BlockPos table : seat.table) {
                BlockPos top = table.above();
                if (!level.isLoaded(top)) continue;
                var state = level.getBlockState(top);
                if (state.is(BlockRegistry.SERVED_PLATE.get()) || state.is(BlockRegistry.TABLE_CRUMBS.get())) r.dirtyTables.add(top.immutable());
            }
        }
    }

    private static void tryClosingRitual(ServerLevel level, Restaurant r, List<CustomerEntity> present) {
        if (!present.isEmpty()) return;

        if (level.getGameTime() % 20 == 0) markLeftovers(level, r);
        boolean waiter = r.staff.stream().anyMatch(s -> s.role == com.ladleandlantern.core.restaurant.StaffRole.WAITER
                && level.getEntity(s.entityId) instanceof com.ladleandlantern.core.entity.StaffEntity);
        if (!r.dirtyTables.isEmpty() && waiter && level.getDayTime() % 24000L < 14000) return;
        List<ServerPlayer> near = nearbyPlayers(level, r, 24);
        if (near.isEmpty()) return;
        r.ritualPending = false;
        RestaurantStore store = RestaurantStore.get(level.getServer());
        int seasonLength = Config.SEASON_LENGTH.get();
        CompoundTag card = new CompoundTag();
        card.putString("name", r.name);
        card.putString("date", Schedule.DAY_NAMES[Schedule.weekday(store.day())] + ", "
                + GameCalendar.season(store.day(), seasonLength).label() + " day " + GameCalendar.dayOfSeason(store.day(), seasonLength));
        card.putInt("orders", r.shiftOrders);
        card.putInt("average", r.shiftOrders == 0 ? 0 : Math.round(r.shiftScoreTotal / (float) r.shiftOrders));
        card.putInt("walkouts", r.shiftWalkouts);
        card.putInt("earnings", r.shiftEarnings);
        card.putInt("tips", r.shiftTips);
        card.putInt("newFaces", r.shiftNewFaces);
        card.putInt("guests", r.shiftArrivals);
        card.putString("best", r.shiftBestDish);
        card.putInt("bestScore", r.shiftBestScore);
        card.putString("currency", new net.minecraft.world.item.ItemStack(Config.currency()).getHoverName().getString());
        card.putString("currencyId", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Config.currency()).toString());
        for (ServerPlayer p : near) {
            CompoundTag mine = card.copy();
            mine.putString("rank", Rank.title(store.progress(p.getUUID()).rank()));
            PacketDistributor.sendToPlayer(p, new com.ladleandlantern.core.network.OpenScreenPayload(com.ladleandlantern.core.network.OpenScreenPayload.CLOSING_CARD, mine));
        }

        if (StaffDinner.timeForDinner(level)) StaffDinner.start(level, r);
        store.markDirty();
    }

    public static List<ServerPlayer> nearbyPlayers(ServerLevel level, Restaurant r, int range) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (r.box().inflate(range).contains(p.position())) out.add(p);
        return out;
    }

    private static void handOverTill(ServerLevel level, Restaurant r) {
        if (r.till <= 0) return;
        ServerPlayer to = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : nearbyPlayers(level, r, 24)) {
            double d = p.distanceToSqr(r.centre());
            if (d < best) { best = d; to = p; }
        }
        if (to == null) return;
        for (StaffRecord s : r.staff) {
            if (level.getEntity(s.entityId) instanceof StaffEntity staff && staff.handOverTakings(level, r, to)) return;
        }
    }

    public static void banner(ServerLevel level, Restaurant r, Component title, Component subtitle) {
        for (ServerPlayer p : nearbyPlayers(level, r, 24)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.sendSystemMessage(title.copy().append(Component.literal("  " + subtitle.getString()).withStyle(ChatFormatting.WHITE)));
            level.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8f, 1.4f);
        }
    }

    private static void seatWaitingLine(ServerLevel level, Restaurant r, List<CustomerEntity> present) {
        List<CustomerEntity> line = present.stream().filter(c -> c.state() == CustomerEntity.State.WAITING_LINE)
                .sorted(Comparator.comparingLong(CustomerEntity::arrivalTick)).toList();
        if (line.isEmpty()) return;
        List<BlockPos> free = new ArrayList<>(freeSeats(level, r, present));
        for (CustomerEntity c : line) {
            if (c.takeout()) {
                BlockPos till = shortestQueue(level, r, present);
                if (till != null) c.assignTill(till);
            } else if (!free.isEmpty()) {
                c.assignSeat(free.remove(0));
            }
        }
    }

    private static boolean trySpawn(ServerLevel level, Restaurant r, List<CustomerEntity> present, long day) {
        if (r.entrance == null || !level.isLoaded(r.entrance)) return false;
        int seasonLength = Config.SEASON_LENGTH.get();
        if (r.available(day, seasonLength).isEmpty()) return false;
        long inside = present.stream().filter(c -> !c.state().leaving()).count();
        if (inside >= capacity(r.bestRank)) return turnAway(level, r, present);

        RandomSource random = level.random;
        List<BlockPos> freeSeats = freeSeats(level, r, present);
        long lineLength = present.stream().filter(c -> c.state() == CustomerEntity.State.WAITING_LINE).count();
        boolean wet = Weather.wet(level, r);
        boolean warming = wet || Weather.cold(level, r, day);
        BlockPos at = outsidePoint(level, r, random, 10, 14);
        if (at == null) at = r.entrance;

        int room = (int) Math.max(0, capacity(r.bestRank) - inside);
        if (!r.coupleToday && lineLength == 0 && room >= 2
                && (random.nextFloat() < 0.25f || r.schedule.progress(level.getDayTime()) > 0.5f)) {
            BlockPos[] pair = facingPair(r, freeSeats, random);
            if (pair != null) {
                List<CustomerEntity> party = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    CustomerProfile profile = pickProfile(level, r, present, random, false);
                    profile.refreshFavourites(r.catalogue(), random);
                    List<Order.Line> lines = OrderGenerator.generate(r, profile, day, seasonLength, random, new OrderGenerator.Mood(warming, false));
                    if (lines.isEmpty()) break;
                    party.add(CustomerEntity.spawn(level, r, profile, false, pair[i], null, lines, at.offset(i, 0, 0), false));
                    r.shiftArrivals++;
                }
                if (party.size() == 2 && party.get(0) != null && party.get(1) != null) {
                    CustomerEntity.pairUp(party.get(0), party.get(1));
                    r.coupleToday = true;
                }
                RestaurantStore.get(level.getServer()).markDirty();
                if (!party.isEmpty()) return true;
            }
        }

        if (lineLength == 0 && rushHour(level.getDayTime()) && random.nextFloat() < GROUP_CHANCE) {
            List<BlockPos> table = groupTable(r, freeSeats, random);
            int size = table == null ? 0 : Math.min(Math.min(table.size(), 2 + random.nextInt(3)), room);
            if (size >= 2) {
                boolean withKid = random.nextFloat() < 0.35f;
                for (int i = 0; i < size; i++) {
                    boolean kid = withKid && i == size - 1;
                    CustomerProfile profile = pickProfile(level, r, present, random, kid);
                    profile.refreshFavourites(r.catalogue(), random);
                    List<Order.Line> lines = OrderGenerator.generate(r, profile, day, seasonLength, random, new OrderGenerator.Mood(warming, kid));
                    if (lines.isEmpty()) continue;
                    CustomerEntity.spawn(level, r, profile, false, table.get(i), null, lines, at.offset(i % 2, 0, i / 2), kid);
                    r.shiftArrivals++;
                }
                RestaurantStore.get(level.getServer()).markDirty();
                return true;
            }
        }

        boolean lineHasRoom = lineLength < r.waitingLine.size();
        boolean canDineIn = (freeSeats.isEmpty() || lineLength > 0) ? lineHasRoom : true;
        BlockPos cashier = shortestQueue(level, r, present);

        boolean takeout = !r.cashiers.isEmpty() && (!canDineIn || random.nextFloat() < (wet ? WET_TAKEOUT_CHANCE : TAKEOUT_CHANCE));
        if (takeout && cashier == null && !lineHasRoom) takeout = false;
        if (!takeout && !canDineIn) return turnAway(level, r, present);

        boolean kid = !takeout && random.nextFloat() < KID_CHANCE;
        CustomerProfile profile = pickProfile(level, r, present, random, kid);
        profile.refreshFavourites(r.catalogue(), random);
        List<Order.Line> lines = OrderGenerator.generate(r, profile, day, seasonLength, random, new OrderGenerator.Mood(warming, kid));
        if (lines.isEmpty()) return false;

        BlockPos seat = takeout || lineLength > 0 || freeSeats.isEmpty() ? null : freeSeats.get(random.nextInt(freeSeats.size()));
        CustomerEntity.spawn(level, r, profile, takeout, seat, takeout ? cashier : null, lines, at, kid);
        r.shiftArrivals++;
        RestaurantStore.get(level.getServer()).markDirty();
        return true;
    }

    private static boolean turnAway(ServerLevel level, Restaurant r, List<CustomerEntity> present) {
        if (level.random.nextFloat() > 0.3f) return false;
        BlockPos at = outsidePoint(level, r, level.random, 10, 14);
        if (at == null) return false;
        CustomerEntity.turnAway(level, r, pickProfile(level, r, present, level.random, false), at);
        return true;
    }

    @Nullable
    private static BlockPos[] facingPair(Restaurant r, List<BlockPos> free, RandomSource random) {
        List<BlockPos[]> options = new ArrayList<>();
        for (BlockPos a : free) {
            Seat sa = r.seatAt(a).orElse(null);
            if (sa == null) continue;
            for (BlockPos b : free) {
                if (a.equals(b) || a.asLong() > b.asLong() || a.getY() != b.getY()) continue;
                Seat sb = r.seatAt(b).orElse(null);
                if (sb == null) continue;
                int dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
                if (dx != 0 && dz != 0) continue;
                int dist = Math.abs(dx + dz);
                if (dist < 2 || dist > 4) continue;
                BlockPos step = new BlockPos(Integer.signum(dx), 0, Integer.signum(dz));
                boolean allTable = true;
                for (int i = 1; i < dist && allTable; i++) {
                    BlockPos between = a.offset(step.getX() * i, 0, step.getZ() * i);
                    allTable = sa.table.contains(between) || sb.table.contains(between);
                }
                if (allTable) options.add(new BlockPos[]{a, b});
            }
        }
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }

    @Nullable
    private static List<BlockPos> groupTable(Restaurant r, List<BlockPos> free, RandomSource random) {
        List<List<BlockPos>> options = new ArrayList<>();
        for (List<BlockPos> table : r.tableGroups()) {
            List<BlockPos> open = table.stream().filter(free::contains).toList();
            if (open.size() >= 2) options.add(open);
        }
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }

    @Nullable
    public static BlockPos outsidePoint(ServerLevel level, Restaurant r, RandomSource random, int minDist, int maxDist) {
        BlockPos door = r.entrance;
        if (door == null) return null;
        Vec3 away = Vec3.atCenterOf(door).subtract(r.centre()).multiply(1, 0, 1);
        double base = away.lengthSqr() < 0.01 ? random.nextDouble() * Math.PI * 2 : Math.atan2(away.z, away.x);
        for (int tries = 0; tries < 16; tries++) {
            double spread = tries < 6 ? Math.PI / 2 : Math.PI * 2;
            double angle = base + (random.nextDouble() - 0.5) * spread;
            int dist = minDist + random.nextInt(Math.max(1, maxDist - minDist + 1));
            int x = door.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = door.getZ() + (int) Math.round(Math.sin(angle) * dist);
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.isLoaded(ground) || r.nearBounds(ground, 3) || Math.abs(ground.getY() - door.getY()) > 2) continue;
            if (level.getFluidState(ground.below()).isEmpty() && level.getBlockState(ground).isAir() && level.getBlockState(ground.above()).isAir()) return ground;
        }
        return null;
    }

    @Nullable
    private static BlockPos shortestQueue(ServerLevel level, Restaurant r, List<CustomerEntity> present) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos cashier : r.cashiers) {
            long count = present.stream().filter(c -> c.takeout() && cashier.equals(c.cashierPos()) && !c.state().leaving()).count();
            if (count >= com.ladleandlantern.core.block.CashierBlock.queueSpots(level, r, cashier).size()) continue;
            double score = count - (StaffEntity.staffedTill(level, r, cashier) ? 0.5 : 0);
            if (score < bestScore) {
                best = cashier;
                bestScore = score;
            }
        }
        return best;
    }

    private static CustomerProfile pickProfile(ServerLevel level, Restaurant r, List<CustomerEntity> present, RandomSource random, boolean kid) {
        float progress = r.schedule.progress(level.getDayTime());
        CustomerType type = CustomerType.REGULAR;
        long day = RestaurantStore.get(level.getServer()).day();

        if (!kid && r.shiftClosers == 0 && progress > 0.8f && random.nextFloat() < 0.5f) type = CustomerType.CLOSER;
        else if (!kid && r.bestRank >= 2 && Schedule.weekday(day) == CRITIC_DAY && r.shiftCritics < 2 && random.nextFloat() < 0.25f) type = CustomerType.CRITIC;
        if (type == CustomerType.CLOSER) r.shiftClosers++;
        if (type == CustomerType.CRITIC) r.shiftCritics++;

        Set<UUID> here = new HashSet<>();
        present.forEach(c -> here.add(c.profileId()));
        final CustomerType wanted = type;
        List<CustomerProfile> candidates = r.customers.values().stream()
                .filter(p -> p.type == wanted && p.kid == kid && !here.contains(p.id)).toList();
        boolean reuse = !candidates.isEmpty() && (random.nextFloat() < RETURNING_CHANCE || r.customers.size() >= MAX_PROFILES);
        if (reuse) return candidates.get(random.nextInt(candidates.size()));

        String name = kid ? Names.kid(random) : switch (type) {
            case CRITIC -> Names.critic(random);
            case CLOSER -> Names.closer(random);
            default -> Names.regular(random);
        };
        CustomerProfile p = new CustomerProfile(UUID.randomUUID(), name, Names.skin(random), type, 3 + random.nextInt(3));
        p.kid = kid;
        r.customers.put(p.id, p);
        return p;
    }
}
