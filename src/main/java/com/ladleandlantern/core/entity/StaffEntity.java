package com.ladleandlantern.core.entity;

import com.ladleandlantern.core.block.TableCrumbsBlock;
import com.ladleandlantern.core.block.entity.ServedPlateBlockEntity;
import com.ladleandlantern.core.network.OpenScreenPayload;
import com.ladleandlantern.core.registry.BlockRegistry;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.block.entity.BagRackBlockEntity;
import com.ladleandlantern.core.block.CashierBlock;
import com.ladleandlantern.core.block.entity.MenuStandBlockEntity;
import com.ladleandlantern.core.block.entity.TrayBlockEntity;
import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.item.MenuCardItem;
import com.ladleandlantern.core.registry.EntityTypeRegistry;
import com.ladleandlantern.core.registry.ItemRegistry;
import com.ladleandlantern.core.registry.SoundEventRegistry;
import com.ladleandlantern.core.restaurant.*;
import com.ladleandlantern.core.food.TableService;
import net.minecraft.world.phys.Vec3;
import com.ladleandlantern.core.shift.StaffDinner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.*;

public class StaffEntity extends RestaurantNpc {
    private enum Job { IDLE, FETCH_MENU, GIVE_MENU, TAKE_ORDER, TO_TRAY, TO_CUSTOMER, TO_RACK, CLEAN, RETURN_CARDS, PICK_UP }

    private static final double REACH = 2.5;
    private static final int CASHIER_RANGE = 3;

    private static final int CALLOUT_COOLDOWN = 10 * 20;

    private static final double FAR_REACH = 5;

    private StaffRole role = StaffRole.WAITER;
    private BlockPos home;
    private Job job = Job.IDLE;
    private BlockPos targetTray;
    private BlockPos targetRack;

    private UUID targetItem;

    private BlockPos targetTable;
    private UUID targetCustomer;
    private final List<ItemStack> carrying = new ArrayList<>();
    private int jobTicks;

    private long busyUntil;
    private Runnable whenDone;
    private long slackUntil;
    private long nextStroll;
    private final Map<UUID, Long> lastCallout = new HashMap<>();

    private BlockPos dinnerSeat;
    private int mealTicks;
    private boolean hadMeal;

    private UUID lookTarget;

    private BlockPos bed;
    private boolean bedtime;

    private String missingNote;
    private long noBedUntil;

    private long standUpAt = -1;

    private long leaveTableAt;

    private long lastDayTime = -1, lastGameTime = -1;

    private int bedStuck;
    private double bedDistance = Double.MAX_VALUE;

    private net.minecraft.world.entity.Entity glance;
    private long glanceUntil;
    private long speakerUntil;
    private net.minecraft.world.entity.Entity headFocus;

    public StaffEntity(EntityType<? extends StaffEntity> type, Level level) {
        super(type, level);
    }

    public static StaffEntity spawn(ServerLevel level, Restaurant r, StaffRecord record, BlockPos cashier) {
        StaffEntity s = new StaffEntity(EntityTypeRegistry.STAFF.get(), level);
        s.restaurantId = r.id;
        s.role = record.role;
        s.home = cashier;
        s.setSkin(record.skin);
        BlockPos at = standingSpot(level, r, cashier);
        s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        record.entityId = s.getUUID();
        level.addFreshEntity(s);
        return s;
    }

    private static BlockPos standingSpot(ServerLevel level, Restaurant r, BlockPos cashier) {
        return cashier.relative(CashierBlock.customerSide(level, cashier, r.entrance).getOpposite());
    }

    public static boolean staffedTill(ServerLevel level, Restaurant r, BlockPos till) {
        for (StaffRecord s : r.staff) {
            if (s.role == StaffRole.CASHIER && level.getEntity(s.entityId) instanceof StaffEntity e && till.equals(e.home)) return true;
        }
        return false;
    }

    public static void clearCaches() {
        MISSING_CALLED.clear();
    }

    public boolean slacking() {
        return level().getGameTime() < slackUntil;
    }

    @Override
    public boolean solidToOthers() {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB pathBounds() {
        Restaurant r = restaurant().orElse(null);
        if (r == null) return null;
        net.minecraft.world.phys.AABB box = r.box().inflate(1, 0, 1).expandTowards(0, 1, 0);
        if (bedtime && bed != null) box = box.minmax(new net.minecraft.world.phys.AABB(bed).inflate(2));
        return box;
    }

    @Override
    protected BlockPos returnPoint() {
        Restaurant r = restaurant().orElse(null);
        if (r == null || home == null || !(level() instanceof ServerLevel level)) return null;
        return standingSpot(level, r, home);
    }

    @Override
    protected boolean canSit() {
        return dinnerSeat != null;
    }

    public boolean seated() {
        return dinnerSeat != null && isPassenger();
    }

    private boolean busy() {
        return level().getGameTime() < busyUntil;
    }

    private void startBusy(int ticks, Runnable done) {
        busyUntil = level().getGameTime() + ticks;
        whenDone = done;
    }

    private static int duration(int base, StaffRecord record) {
        return (int) (base * (1.0 - 0.1 * (record.level - 1)));
    }

    @Override
    protected void customServerAiStep() {
        try {
            serverStep();
        } catch (Exception e) {
            com.ladleandlantern.core.util.Guard.report(getClass().getSimpleName() + " tick", e);

            reset();
        }
    }

    private void serverStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        checkClock(level);
        if (slacking()) {
            dance();
            return;
        }
        if (dinnerSeat != null) {
            if (standUpAt >= 0 && level.getGameTime() >= standUpAt) {
                standUpAt = -1;
                sitForDinner(null);
                leaveTableAt = level.getGameTime() + 15 + random.nextInt(30);
            } else {
                tickDinner(level);
            }
            return;
        }
        if (isSleeping()) {
            if (tickCount % 100 == 0 && shouldWake(level)) wakeUp();
            return;
        }
        if (bedtime) {
            tickBedtime(level);
            return;
        }
        if (busy()) {
            if (job == Job.CLEAN && tickCount % 6 == 0) brushStroke(level);
            if (tickCount % 20 == 0 && job == Job.TAKE_ORDER) {
                level.playSound(null, blockPosition(), SoundEvents.VILLAGER_WORK_LIBRARIAN, SoundSource.NEUTRAL, 0.7f, 1f + random.nextFloat() * 0.2f);
            }
            return;
        }
        if (whenDone != null) {
            Runnable done = whenDone;
            whenDone = null;
            done.run();
        }
        if (getNavigation().isDone()) tickGlance();
        if (missingNote != null && emote() == Emote.NONE) emote(Emote.ALERT, 40);
        if (tickCount % 10 != 0) return;
        Restaurant r = restaurant().orElse(null);
        StaffRecord record = r == null ? null : r.staffRecord(getUUID()).orElse(null);
        if (record == null) {
            dropCarried();
            discard();
            return;
        }
        record.ensureDetails(random, Config.SEASON_LENGTH.get());
        if (!record.skin.equals(skin())) setSkin(record.skin);

        boolean paid = record.unpaidDays == 0;
        refreshName(record, paid);
        if (home == null || !r.cashiers.contains(home)) {
            home = r.cashiers.isEmpty() ? blockPosition() : r.cashiers.stream()
                    .filter(c -> role != StaffRole.CASHIER || !staffedTill(level, r, c)).findFirst().orElse(r.cashiers.get(0));
        }
        if (StaffDinner.bedtimeFor(level, r) && level.getGameTime() > noBedUntil) {
            stowCarried(level, r);
            goToBed(0);
            return;
        }
        if (!paid && tickCount % 1200 == 0) speak("I wasn't paid yesterday...", 80);
        maybeStartSlacking(record);

        double speed = 0.62 + 0.025 * (record.level - 1);
        if (role == StaffRole.WAITER) tickWaiter(level, r, record, speed);
        else tickCashier(level, r, record);
    }

    private void refreshName(StaffRecord record, boolean paid) {
        MutableComponent name = Component.literal(record.title() + " " + record.name).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" (Lv " + record.level + ")").withStyle(ChatFormatting.GRAY));
        setRating(HEARTS, record.hearts(), 5);
        if (!paid) name.append(Component.literal(" owed wages").withStyle(ChatFormatting.RED));
        setCustomName(name);
    }

    private boolean takenByOther(ServerLevel level, Restaurant r, java.util.function.Predicate<StaffEntity> doing) {
        for (StaffRecord s : r.staff) {
            if (level.getEntity(s.entityId) instanceof StaffEntity other && other != this && other.isAlive() && doing.test(other)) return true;
        }
        return false;
    }

    private boolean free(ServerLevel level, Restaurant r, CustomerEntity c) {
        return !takenByOther(level, r, s -> c.getUUID().equals(s.targetCustomer));
    }

    private boolean inReach(BlockPos pos) {
        if (near(pos, REACH)) return true;
        return getNavigation().isDone() && distanceToSqr(Vec3.atCenterOf(pos)) <= FAR_REACH * FAR_REACH;
    }

    private void maybeStartSlacking(StaffRecord record) {
        float chancePerSecond = (StaffRecord.MAX_LEVEL + 1 - record.level) * 0.0002f;
        if (job == Job.IDLE && random.nextFloat() < chancePerSecond) {
            slackUntil = level().getGameTime() + 600 + random.nextInt(1200);
            getNavigation().stop();
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    private void dance() {
        emote(Emote.MUSIC, 25);
        if (tickCount % 10 == 0) {
            swing(tickCount % 20 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            float yaw = getYRot() + 45f;
            setYRot(yaw);
            setYBodyRot(yaw);
            setYHeadRot(yaw);
        }
    }

    private void tickWaiter(ServerLevel level, Restaurant r, StaffRecord record, double speed) {
        if (tickCount % 20 == 0) {
            jobTicks++;
            callOutMissing(level, r, CustomerEntity.inRestaurant(level, r), false);
        }

        if (job == Job.IDLE && carrying.stream().anyMatch(st -> !st.is(ItemRegistry.MENU_CARD.get()))) {
            List<ItemStack> other = carrying.stream().filter(st -> !st.is(ItemRegistry.MENU_CARD.get())).toList();
            putFoodBack(level, r, other);
            carrying.removeAll(other);
        }
        switch (job) {
            case IDLE -> pickWaiterJob(level, r);
            case FETCH_MENU -> {
                CustomerEntity c = customer(level);
                BlockPos stand = MenuStandBlockEntity.find(level, r);
                if (c == null || c.state() != CustomerEntity.State.WANTS_MENU || stand == null) {
                    reset();
                } else if (inReach(stand)) {
                    ItemStack card = level.getBlockEntity(stand) instanceof MenuStandBlockEntity s ? s.takeOne() : ItemStack.EMPTY;

                    if (MenuCardItem.isWritten(card)) MenuCardItem.write(card, r, store().day());
                    if (!card.isEmpty()) level.playSound(null, stand, SoundEventRegistry.MENU_CARD.get(), SoundSource.NEUTRAL, 0.6f, 1.1f);
                    if (!MenuCardItem.isWritten(card)) {
                        if (!card.isEmpty() && level.getBlockEntity(stand) instanceof MenuStandBlockEntity s) s.insert(card);
                        sayOnce(level, r, c, "Our menu cards are blank! Write today's menu on them at the cash register.");
                        reset();
                    } else {
                        carrying.add(card);
                        setItemSlot(EquipmentSlot.MAINHAND, card.copy());
                        job = Job.GIVE_MENU;
                        jobTicks = 0;
                    }
                } else {
                    walkTo(stand, speed);
                }
            }
            case GIVE_MENU -> {
                CustomerEntity c = customer(level);
                if (c == null || c.state() != CustomerEntity.State.WANTS_MENU) {
                    reset();
                } else if (distanceTo(c) < REACH) {
                    getLookControl().setLookAt(c);
                    swing(InteractionHand.MAIN_HAND);
                    if (!carrying.isEmpty() && c.handMenu(carrying.get(0))) {
                        carrying.remove(0);
                        level.playSound(null, blockPosition(), SoundEventRegistry.MENU_CARD.get(), SoundSource.NEUTRAL, 0.7f, 1f);
                    }
                    speak(random.nextBoolean() ? "Here's our menu." : "Take your time.", 40);
                    reset();
                } else {
                    getNavigation().moveTo(c, speed);
                }
            }
            case TAKE_ORDER -> {
                CustomerEntity c = customer(level);
                if (c == null || c.state() != CustomerEntity.State.READY) {
                    reset();
                } else if (distanceTo(c) < REACH) {
                    getNavigation().stop();
                    getLookControl().setLookAt(c);
                    emote(Emote.TALK, duration(60, record));

                    startBusy(duration(60, record), () -> {
                        ItemStack card = c.takeOrder(null);
                        if (!card.isEmpty()) carrying.add(card);
                        reset();
                    });
                } else {
                    getNavigation().moveTo(c, speed);
                }
            }
            case TO_TRAY -> {
                CustomerEntity c = customer(level);
                if (c == null || c.state() != CustomerEntity.State.WAITING_FOOD || !(level.getBlockEntity(targetTray) instanceof TrayBlockEntity tray)
                        || !tray.containsAll(c.order().items())) {
                    reset();
                } else if (inReach(targetTray)) {
                    getNavigation().stop();
                    getLookControl().setLookAt(targetTray.getX() + 0.5, targetTray.getY(), targetTray.getZ() + 0.5);
                    emote(Emote.THINK, duration(30, record));
                    startBusy(duration(30, record), () -> {
                        if (level.getBlockEntity(targetTray) instanceof TrayBlockEntity t && t.containsAll(c.order().items())) {
                            carrying.addAll(t.extract(c.order().items()));
                            setItemSlot(EquipmentSlot.MAINHAND, carrying.isEmpty() ? ItemStack.EMPTY : carrying.get(0).copyWithCount(1));
                            job = Job.TO_CUSTOMER;
                            jobTicks = 0;
                        } else {
                            reset();
                        }
                    });
                } else {
                    walkTo(targetTray, speed);
                }
            }
            case CLEAN -> {
                if (targetTable == null || !r.dirtyTables.contains(targetTable)) {
                    reset();
                } else if (inReach(targetTable)) {
                    getNavigation().stop();
                    setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRUSH));
                    level.playSound(null, targetTable, SoundEventRegistry.CLEAR_DISHES.get(), SoundSource.NEUTRAL, 0.8f, 1f);
                    emote(Emote.THINK, duration(50, record));
                    BlockPos table = targetTable;
                    startBusy(duration(50, record), () -> {
                        clearTable(level, r, table);
                        reset();
                    });
                } else if (getNavigation().isDone() || tickCount % 40 == 0) {
                    walkTo(targetTable.below(), speed);
                }

                if (job == Job.CLEAN && jobTicks > 60) reset();
            }
            case PICK_UP -> {
                if (!(level.getEntity(targetItem) instanceof ItemEntity item) || !item.isAlive()) {
                    reset();
                } else if (distanceTo(item) < 1.6) {
                    getNavigation().stop();
                    swing(InteractionHand.MAIN_HAND);
                    carrying.add(item.getItem().copy());
                    item.discard();
                    level.playSound(null, blockPosition(), SoundEventRegistry.MENU_CARD.get(), SoundSource.NEUTRAL, 0.6f, 1f);
                    reset();
                } else if (getNavigation().isDone() || tickCount % 40 == 0) {
                    getNavigation().moveTo(item, speed);
                }
            }
            case RETURN_CARDS -> {
                BlockPos stand = standWithRoom(level, r);
                if (stand == null || inReach(stand)) {
                    if (stand != null) {
                        getLookControl().setLookAt(stand.getX() + 0.5, stand.getY() + 0.5, stand.getZ() + 0.5);
                        swing(InteractionHand.MAIN_HAND);
                    }
                    returnCards(level, r);
                    reset();
                } else {
                    setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ItemRegistry.MENU_CARD.get()));
                    if (getNavigation().isDone() || tickCount % 40 == 0) walkTo(stand, speed);
                }
            }
            case TO_CUSTOMER -> {
                CustomerEntity c = customer(level);
                if (c == null || c.state() != CustomerEntity.State.WAITING_FOOD) {
                    putFoodBack(level, r, carrying);
                    carrying.clear();
                    reset();
                } else if (distanceTo(c) < REACH) {
                    c.deliver(new ArrayList<>(carrying), null, displayName());
                    carrying.clear();
                    speak("Enjoy.", 30);
                    reset();
                } else {
                    getNavigation().moveTo(c, speed);
                }
            }
        }
        if (job != Job.IDLE && jobTicks > 25) {
            dropCarried();
            reset();
        }
    }

    private void pickWaiterJob(ServerLevel level, Restaurant r) {
        jobTicks = 0;
        if (carrying.stream().anyMatch(st -> st.is(ItemRegistry.MENU_CARD.get()))) {
            job = Job.RETURN_CARDS;
            return;
        }
        List<CustomerEntity> customers = CustomerEntity.inRestaurant(level, r);

        boolean queue = customers.stream().anyMatch(c -> c.state() == CustomerEntity.State.WAITING_LINE || c.state() == CustomerEntity.State.ARRIVING);
        if (queue && pickCleaning(level, r)) return;
        if (findDelivery(level, r, customers)) {
            job = Job.TO_TRAY;
            return;
        }
        if (pickCleaning(level, r)) return;
        CustomerEntity wantsMenu = oldest(level, r, customers, CustomerEntity.State.WANTS_MENU);
        if (wantsMenu != null && MenuStandBlockEntity.find(level, r) != null) {
            targetCustomer = wantsMenu.getUUID();
            job = Job.FETCH_MENU;
            return;
        }
        if (wantsMenu != null) sayOnce(level, r, wantsMenu, "We've no menu cards out! Put some in a menu stand.");
        CustomerEntity ready = oldest(level, r, customers, CustomerEntity.State.READY);
        if (ready != null) {
            targetCustomer = ready.getUUID();
            job = Job.TAKE_ORDER;
            return;
        }
        ItemEntity dropped = droppedCard(level, r);
        if (dropped != null) {
            targetItem = dropped.getUUID();
            job = Job.PICK_UP;
            return;
        }
        if (pickCleaning(level, r)) return;
        stroll(level, r, customers.isEmpty());
    }

    private boolean pickCleaning(ServerLevel level, Restaurant r) {
        BlockPos dirty = dirtyTable(level, r);
        if (dirty == null) return false;
        targetTable = dirty;
        job = Job.CLEAN;
        return true;
    }

    @Nullable
    private ItemEntity droppedCard(ServerLevel level, Restaurant r) {
        return level.getEntitiesOfClass(ItemEntity.class, r.box().inflate(1),
                        e -> e.isAlive() && e.getItem().is(ItemRegistry.MENU_CARD.get()) && e.onGround()).stream()
                .filter(e -> !takenByOther(level, r, s -> e.getUUID().equals(s.targetItem)))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
    }

    private void clearTable(ServerLevel level, Restaurant r, BlockPos top) {
        Map<ResourceLocation, Integer> wanted = new HashMap<>();
        for (CustomerEntity c : CustomerEntity.inRestaurant(level, r)) {
            Seat seat = c.seatPos() == null ? null : r.seatAt(c.seatPos()).orElse(null);
            if (seat == null || !seat.table.contains(top.below())) continue;
            for (ResourceLocation id : c.stillWanted()) wanted.merge(id, 1, Integer::sum);
        }
        for (TableService.Found f : TableService.scanTables(level, List.of(top.below()))) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(f.stack().getItem());
            int keep = wanted.getOrDefault(id, 0);
            if (keep > 0) wanted.put(id, keep - 1);
            else f.consume().run();
        }

        if (level.getBlockEntity(top) instanceof com.ladleandlantern.core.block.entity.ServedPlateBlockEntity plate && plate.food().isEmpty()) level.removeBlock(top, false);
        boolean card = r.tableCards.remove(top);
        if (level.getBlockState(top).is(com.ladleandlantern.core.registry.BlockRegistry.TABLE_CRUMBS.get())) {
            card |= level.getBlockState(top).getValue(com.ladleandlantern.core.block.TableCrumbsBlock.MENU);
            level.removeBlock(top, false);
        }
        if (card) {
            ItemStack fresh = new ItemStack(ItemRegistry.MENU_CARD.get());
            MenuCardItem.write(fresh, r, store().day());
            carrying.add(fresh);
        }
        r.dirtyTables.remove(top);
        store().markDirty();
    }

    @Nullable
    private BlockPos dirtyTable(ServerLevel level, Restaurant r) {
        return r.dirtyTables.stream().filter(p -> !takenByOther(level, r, s -> p.equals(s.targetTable)))
                .min(Comparator.comparingDouble(p -> p.distSqr(blockPosition()))).orElse(null);
    }

    @Nullable
    private BlockPos standWithRoom(ServerLevel level, Restaurant r) {
        return r.fixtures(com.ladleandlantern.core.restaurant.Fixture.MENU_STAND).stream()
                .filter(p -> level.isLoaded(p) && level.getBlockEntity(p) instanceof MenuStandBlockEntity s && s.hasRoom())
                .min(Comparator.comparingDouble(p -> p.distSqr(blockPosition()))).orElse(null);
    }

    private void brushStroke(ServerLevel level) {
        if (targetTable == null) return;
        swing(InteractionHand.MAIN_HAND);
        getLookControl().setLookAt(targetTable.getX() + 0.5, targetTable.getY(), targetTable.getZ() + 0.5);
        level.playSound(null, targetTable, SoundEvents.BRUSH_GENERIC, SoundSource.NEUTRAL, 0.25f, 0.9f + random.nextFloat() * 0.2f);
        BlockState crumbs = level.getBlockState(targetTable);
        BlockState table = level.getBlockState(targetTable.below());
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, crumbs.isAir() ? table : crumbs),
                targetTable.getX() + 0.5, targetTable.getY() + 0.05, targetTable.getZ() + 0.5, 6, 0.3, 0.02, 0.3, 0.05);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, table),
                targetTable.getX() + 0.5, targetTable.getY() + 0.02, targetTable.getZ() + 0.5, 3, 0.3, 0.01, 0.3, 0.03);
    }

    @Nullable
    private CustomerEntity oldest(ServerLevel level, Restaurant r, List<CustomerEntity> customers, CustomerEntity.State state) {
        return customers.stream().filter(c -> !c.takeout() && c.state() == state)
                .sorted(Comparator.comparingLong(c -> c.order().readyTick))
                .filter(c -> free(level, r, c)).findFirst().orElse(null);
    }

    private boolean findDelivery(ServerLevel level, Restaurant r, List<CustomerEntity> customers) {
        List<CustomerEntity> waiting = customers.stream()
                .filter(c -> !c.takeout() && c.state() == CustomerEntity.State.WAITING_FOOD)
                .sorted(Comparator.comparingLong(c -> c.order().readyTick)).toList();
        for (CustomerEntity c : waiting) {
            for (BlockPos trayPos : r.trays) {
                if (level.getBlockEntity(trayPos) instanceof TrayBlockEntity tray && tray.containsAll(c.order().items()) && free(level, r, c)) {
                    targetTray = trayPos;
                    targetCustomer = c.getUUID();
                    return true;
                }
            }
        }
        return false;
    }

    private void returnCards(ServerLevel level, Restaurant r) {
        Iterator<ItemStack> it = carrying.iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            if (!s.is(ItemRegistry.MENU_CARD.get())) continue;
            if (!MenuStandBlockEntity.putBack(level, r, s)) Containers.dropItemStack(level, getX(), getY() + 0.5, getZ(), s);
            it.remove();
        }
    }

    private static final Map<UUID, Long> MISSING_CALLED = new HashMap<>();

    private void callOutMissing(ServerLevel level, Restaurant r, List<CustomerEntity> customers, boolean takeoutOnly) {
        long now = level.getGameTime();
        String note = null;
        for (CustomerEntity c : customers) {
            if (c.state() != CustomerEntity.State.WAITING_FOOD || c.takeout() != takeoutOnly) continue;
            if (takeoutOnly && !home.equals(c.cashierPos())) continue;
            Seat seat = c.seatPos() == null ? null : r.seatAt(c.seatPos()).orElse(null);
            List<ResourceLocation> missing = seat != null ? c.missing(level, seat) : new ArrayList<>(c.order().items());
            for (BlockPos trayPos : r.trays) {
                if (level.getBlockEntity(trayPos) instanceof TrayBlockEntity tray) {
                    for (ItemStack st : tray.items()) {
                        for (int i = 0; i < st.getCount(); i++) missing.remove(BuiltInRegistries.ITEM.getKey(st.getItem()));
                    }
                }
            }
            if (missing.isEmpty() || missing.size() == c.order().items().size()) continue;
            String line = c.label(r) + " is still missing " + CustomerEntity.names(missing) + "!";
            if (note == null) note = line;
            if (now - MISSING_CALLED.getOrDefault(c.getUUID(), -CALLOUT_COOLDOWN * 2L) >= CALLOUT_COOLDOWN) {
                MISSING_CALLED.put(c.getUUID(), now);
                if (MISSING_CALLED.size() > 256) MISSING_CALLED.clear();
                ringBell(level);
                say(level, r, line);
                emote(Emote.ALERT, 40);
            }
        }
        missingNote = note;
        if (note == null && emote() == Emote.ALERT) emote(Emote.NONE, 1);
    }

    private void putFoodBack(ServerLevel level, Restaurant r, List<ItemStack> food) {
        for (ItemStack s : food) {
            ItemStack left = s.copy();
            for (BlockPos t : r.trays.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(blockPosition()))).toList()) {
                if (left.isEmpty()) break;
                if (level.getBlockEntity(t) instanceof TrayBlockEntity tray) tray.insert(left);
            }
            if (!left.isEmpty()) Containers.dropItemStack(level, getX(), getY() + 0.5, getZ(), left);
        }
    }

    private void stroll(ServerLevel level, Restaurant r, boolean quiet) {
        long now = level.getGameTime();
        BlockPos rest = restSpot(level, r);
        if (!quiet) {
            if (!near(rest, 1.5) && getNavigation().isDone()) walkTo(rest, 0.6);
            return;
        }
        if (now < nextStroll) return;
        nextStroll = now + 100 + random.nextInt(200);
        if (random.nextFloat() < 0.4f) return;
        for (int tries = 0; tries < 8; tries++) {
            int x = r.min.getX() + random.nextInt(r.max.getX() - r.min.getX() + 1);
            int z = r.min.getZ() + random.nextInt(r.max.getZ() - r.min.getZ() + 1);
            BlockPos spot = new BlockPos(x, rest.getY(), z);
            if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir()
                    && level.getBlockState(spot.below()).isSolidRender(level, spot.below())
                    && !r.noWalk().contains(spot.below().asLong())) {
                getNavigation().moveTo(x + 0.5, spot.getY(), z + 0.5, 0.45);
                return;
            }
        }
    }

    private BlockPos restSpot(ServerLevel level, Restaurant r) {
        BlockPos base = standingSpot(level, r, home);
        int index = 0;
        for (StaffRecord s : r.staff) {
            if (s.entityId.equals(getUUID())) break;
            if (s.role == StaffRole.WAITER) index++;
        }
        return index == 0 ? base : base.relative(net.minecraft.core.Direction.from2DDataValue(index % 4), 1 + index / 4);
    }

    private void tickCashier(ServerLevel level, Restaurant r, StaffRecord record) {
        BlockPos spot = standingSpot(level, r, home);
        if (job != Job.IDLE) {
            tickBagging(level, r, record, spot);
            return;
        }

        if (!r.shiftOpen && job == Job.IDLE) {
            stroll(level, r, true);
            return;
        }

        if (!near(home, 3.5)) {
            walkTo(spot, 0.6);
            return;
        }
        if (!near(spot, 1.0) && getNavigation().isDone()) walkTo(spot, 0.6);
        List<CustomerEntity> customers = CustomerEntity.inRestaurant(level, r).stream()
                .filter(c -> c.takeout() && home.equals(c.cashierPos()))
                .sorted(Comparator.comparingLong(CustomerEntity::arrivalTick)).toList();

        for (CustomerEntity c : customers) {
            if (c.state() != CustomerEntity.State.WAITING_FOOD || !free(level, r, c)) continue;
            BlockPos trayPos = trayFor(level, r, c);
            if (trayPos == null) continue;
            BlockPos rack = nearestRack(level, r);
            if (rack == null) {
                sayOnce(level, r, c, "We're out of to-go bags! Put some in a bag rack.");
                return;
            }
            targetCustomer = c.getUUID();
            targetTray = trayPos;
            targetRack = rack;
            job = Job.TO_RACK;
            jobTicks = 0;
            return;
        }
        for (CustomerEntity c : customers) {
            if (c.state() != CustomerEntity.State.READY || !free(level, r, c)) continue;
            targetCustomer = c.getUUID();
            job = Job.TAKE_ORDER;
            getLookControl().setLookAt(c);
            emote(Emote.TALK, duration(50, record));
            startBusy(duration(50, record), () -> {
                if (c.isAlive()) c.takeOrder(null);
                reset();
            });
            return;
        }
        callOutMissing(level, r, customers, true);
    }

    @Nullable
    private BlockPos nearestRack(ServerLevel level, Restaurant r) {
        List<BlockPos> racks = new ArrayList<>(r.fixtures(com.ladleandlantern.core.restaurant.Fixture.BAG_RACK));
        if (racks.isEmpty()) {
            for (BlockPos p : BlockPos.betweenClosed(home.offset(-CASHIER_RANGE, -1, -CASHIER_RANGE), home.offset(CASHIER_RANGE, 1, CASHIER_RANGE))) {
                if (level.getBlockEntity(p) instanceof BagRackBlockEntity) racks.add(p.immutable());
            }
        }
        return racks.stream().filter(p -> level.isLoaded(p) && level.getBlockEntity(p) instanceof BagRackBlockEntity rack && rack.count() > 0)
                .min(Comparator.comparingDouble(p -> p.distSqr(blockPosition()))).orElse(null);
    }

    @Nullable
    private BlockPos trayFor(ServerLevel level, Restaurant r, CustomerEntity c) {
        return r.trays.stream().filter(t -> level.isLoaded(t) && level.getBlockEntity(t) instanceof TrayBlockEntity tray && tray.containsAll(c.order().items()))
                .min(Comparator.comparingDouble(t -> t.distSqr(home))).orElse(null);
    }

    private void tickBagging(ServerLevel level, Restaurant r, StaffRecord record, BlockPos spot) {
        if (tickCount % 20 == 0) jobTicks++;
        CustomerEntity c = customer(level);
        boolean stillWaiting = c != null && c.state() == CustomerEntity.State.WAITING_FOOD;
        if (busy()) return;
        switch (job) {
            case TO_RACK -> {
                if (!stillWaiting) {
                    reset();
                } else if (inReach(targetRack)) {
                    if (level.getBlockEntity(targetRack) instanceof BagRackBlockEntity rack && !rack.takeOne().isEmpty()) {
                        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ItemRegistry.TO_GO_BAG.get()));
                        job = Job.TO_TRAY;
                    } else {
                        reset();
                    }
                } else if (getNavigation().isDone()) {
                    walkTo(targetRack, 0.6);
                }
            }
            case TO_TRAY -> {
                if (!stillWaiting || !(level.getBlockEntity(targetTray) instanceof TrayBlockEntity tray) || !tray.containsAll(c.order().items())) {
                    reset();
                } else if (inReach(targetTray)) {
                    getLookControl().setLookAt(targetTray.getX() + 0.5, targetTray.getY(), targetTray.getZ() + 0.5);
                    carrying.addAll(tray.extract(c.order().items()));
                    swing(InteractionHand.MAIN_HAND);
                    job = Job.TO_CUSTOMER;
                } else if (getNavigation().isDone()) {
                    walkTo(targetTray, 0.6);
                }
            }
            case TO_CUSTOMER -> {
                if (!stillWaiting) {
                    putFoodBack(level, r, carrying);
                    carrying.clear();
                    reset();
                } else if (distanceTo(c) < REACH + 0.5 || near(spot, 1.2)) {
                    getNavigation().stop();
                    getLookControl().setLookAt(c);
                    List<ItemStack> bagged = new ArrayList<>(carrying);
                    carrying.clear();
                    level.playSound(null, blockPosition(), SoundEventRegistry.BAG.get(), SoundSource.NEUTRAL, 0.8f, 1f);
                    startBusy(duration(30, record), () -> {
                        swing(InteractionHand.MAIN_HAND);
                        if (c.isAlive() && c.state() == CustomerEntity.State.WAITING_FOOD) c.deliver(bagged, null, displayName());
                        else putFoodBack(level, r, bagged);
                        reset();
                    });
                } else if (getNavigation().isDone()) {
                    walkTo(spot, 0.8);
                }
            }
            default -> reset();
        }
        if (job != Job.IDLE && jobTicks > 40) {
            putFoodBack(level, r, carrying);
            carrying.clear();
            reset();
        }
    }

    public static void kitchenHelp(ServerLevel level, Restaurant r, List<CustomerEntity> present) {
        if (level.getGameTime() % 1200 != 0) return;
        long day = RestaurantStore.get(level.getServer()).day();
        for (StaffRecord record : r.staff) {
            if (record.role != StaffRole.CASHIER || record.affection < StaffRecord.HELPS_AT || record.unpaidDays > 0) continue;
            if (record.helpedDay != day) {
                record.helpedDay = day;
                record.helpedToday = 0;
            }
            int allowance = 1 + (record.affection - StaffRecord.HELPS_AT) / 30;
            if (record.helpedToday >= allowance || level.random.nextFloat() > 0.5f) continue;
            if (!(level.getEntity(record.entityId) instanceof StaffEntity staff) || staff.home == null) continue;
            TrayBlockEntity tray = r.trays.stream().filter(t -> level.isLoaded(t) && level.getBlockEntity(t) instanceof TrayBlockEntity)
                    .min(Comparator.comparingDouble(t -> t.distSqr(staff.home)))
                    .map(t -> (TrayBlockEntity) level.getBlockEntity(t)).orElse(null);
            if (tray == null) continue;
            List<ResourceLocation> wanted = new ArrayList<>();
            present.stream().filter(c -> c.state() == CustomerEntity.State.WAITING_FOOD).forEach(c -> wanted.addAll(c.order().items()));
            if (wanted.isEmpty()) wanted.addAll(r.available(day, Config.SEASON_LENGTH.get()).keySet());
            if (wanted.isEmpty()) continue;
            ResourceLocation dish = wanted.get(level.random.nextInt(wanted.size()));
            if (!tray.insert(FoodRules.stackOf(dish))) continue;
            record.helpedToday++;
            RestaurantStore.get(level.getServer()).markDirty();
            staff.say(level, r, "I made a " + FoodRules.nameOf(dish) + " and left it on the tray. Thought it might help.");
            staff.emote(Emote.LOVE, 60);
        }
    }

    public void sitForDinner(@Nullable BlockPos seat) {
        dinnerSeat = seat;
        mealTicks = 0;
        hadMeal = false;
        lookTarget = null;
        if (seat == null) {
            if (isPassenger()) stopRiding();
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    public void lookAt(@Nullable UUID who) {
        lookTarget = who;
        speakerUntil = level().getGameTime() + 80;
    }

    public boolean dinnerSeatValid(ServerLevel level, Restaurant r) {
        if (dinnerSeat == null) return true;
        Seat seat = r.seatAt(dinnerSeat).orElse(null);
        return seat != null && !seat.table.isEmpty() && !level.getBlockState(dinnerSeat).isAir()
                && seat.table.stream().noneMatch(t -> level.getBlockState(t).isAir());
    }

    private void tickDinner(ServerLevel level) {
        Restaurant r = restaurant().orElse(null);
        if (r == null) return;

        if (standUpAt >= 0 && !dinnerSeatValid(level, r)) standUpAt = level.getGameTime();

        long now = level.getGameTime();
        net.minecraft.world.entity.Entity focus = null;
        if (lookTarget != null && now < speakerUntil) focus = level.getEntity(lookTarget);
        if (focus == null) {
            if (now >= glanceUntil || glance == null || !glance.isAlive()) {
                Player you = level.getNearestPlayer(this, 8);
                if (you != null && random.nextFloat() < 0.45f) glance = you;
                else glance = random.nextFloat() < 0.15f ? null : someoneNearby(8, e -> e instanceof StaffEntity || e instanceof Player);
                glanceUntil = now + 40 + random.nextInt(80);
            }
            focus = glance;
        }
        if (focus != null) turnHeadTo(focus);
        if (!isPassenger()) {
            Seat seat = r.seatAt(dinnerSeat).orElse(null);
            if (seat == null) {
                dinnerSeat = null;
                return;
            }
            if (near(dinnerSeat, 1.8)) sitOn(level, seat);
            else if (tickCount % 20 == 0 || getNavigation().isDone()) walkTo(dinnerSeat, 0.6);
            return;
        }
        if (mealTicks > 0) {
            ItemStack food = getMainHandItem();
            boolean biting = mealTicks % 120 < 40;
            if (biting && mealTicks % 10 == 0 && !food.isEmpty()) {
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food), getX(), getEyeY() - 0.2, getZ(), 2, 0.1, 0.1, 0.1, 0.03);
                playSound(SoundEvents.GENERIC_EAT, 0.35f, voicePitch());
            }
            if (random.nextInt(60) == 0) level.playSound(null, blockPosition(), SoundEventRegistry.CUTLERY.get(), SoundSource.NEUTRAL, 0.3f, 1f);
            if (--mealTicks == 0) setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return;
        }
        if (!hadMeal && tickCount % 40 == 0) {
            for (BlockPos t : r.trays) {
                if (level.getBlockEntity(t) instanceof TrayBlockEntity tray) {
                    for (ItemStack s : tray.items()) {
                        if (s.isEmpty()) continue;
                        eat(s.split(1));
                        tray.setChanged();
                        return;
                    }
                }
            }
            if (emote() == Emote.NONE && speech().isEmpty() && random.nextFloat() < 0.2f) emote(Emote.THINK, 40);
        }
    }

    private void turnHeadTo(net.minecraft.world.entity.Entity e) {
        if (!isPassenger()) {
            getLookControl().setLookAt(e, 30, 30);
            headFocus = null;
            return;
        }
        headFocus = e;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isPassenger() || dinnerSeat == null || headFocus == null || !headFocus.isAlive()) return;
        double dx = headFocus.getX() - getX(), dz = headFocus.getZ() - getZ(), dy = headFocus.getEyeY() - getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float body = getYRot();
        float rel = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(yaw - body), -75f, 75f);
        yBodyRot = body;
        setYHeadRot(net.minecraft.util.Mth.approachDegrees(getYHeadRot(), body + rel, 8f));
        setXRot(net.minecraft.util.Mth.approach(getXRot(), net.minecraft.util.Mth.clamp(pitch, -30f, 30f), 4f));
    }

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        if (dinnerSeat != null && isPassenger()) {
            yBodyRot = getYRot();
            return animStep;
        }
        return super.tickHeadTurn(yRot, animStep);
    }

    private void eat(ItemStack food) {
        hadMeal = true;
        mealTicks = 900 + random.nextInt(600);
        setItemSlot(EquipmentSlot.MAINHAND, food.copyWithCount(1));
        emote(Emote.YUM, 40);
    }

    public void goToBed(int standUpDelay) {
        bedtime = true;
        bedStuck = 0;
        bedDistance = Double.MAX_VALUE;
        if (job != Job.IDLE) reset();
        if (dinnerSeat != null && standUpDelay > 0) {
            standUpAt = level().getGameTime() + standUpDelay;
            lookTarget = null;
        } else {
            standUpAt = -1;
            sitForDinner(null);
            leaveTableAt = level().getGameTime() + 10 + random.nextInt(20);
        }
    }

    public boolean headingToBed() {
        return bedtime;
    }

    public void wakeUp() {
        bedtime = false;
        standUpAt = -1;
        if (isSleeping()) stopSleeping();
    }

    private long wakeTime() {
        return (StaffDinner.BED_UNTIL + 200 + Math.floorMod(getUUID().hashCode(), 2400)) % 24000L;
    }

    private boolean shouldWake(ServerLevel level) {
        Restaurant r = restaurant().orElse(null);
        long t = level.getDayTime() % 24000L, w = wakeTime();
        if (r == null || r.shiftOpen) return true;
        return w >= StaffDinner.BED_UNTIL ? t >= w || t < 11000 : t >= w && t < 11000;
    }

    private void checkClock(ServerLevel level) {
        long day = level.getDayTime(), game = level.getGameTime();
        if (lastGameTime >= 0) {
            long gap = game - lastGameTime;
            long drift = Math.abs((day - lastDayTime) - gap);

            boolean jumped = drift > 1200 && Math.abs(day - lastDayTime) > 1200;
            if (gap > 1200 || jumped) catchUp(level, jumped);
        }
        lastDayTime = day;
        lastGameTime = game;
    }

    private void catchUp(ServerLevel level, boolean jumped) {
        Restaurant r = restaurant().orElse(null);
        if (r == null) return;
        if (StaffDinner.active(r)) {
            if (!jumped) return;
            StaffDinner.cancel(level, r);
        }
        long t = level.getDayTime() % 24000L;
        if (!r.shiftOpen && t >= StaffDinner.DINNER_LATEST && t < StaffDinner.BED_UNTIL) {
            stowCarried(level, r);
            if (dinnerSeat != null) sitForDinner(null);
            bedtime = true;
            snapIntoBed(level);
        }
    }

    private void snapIntoBed(ServerLevel level) {
        if (isSleeping()) return;
        if (!validBed(level, bed)) bed = findBed(level);
        if (bed == null) {
            bedtime = false;
            noBedUntil = level.getGameTime() + 1200;
            return;
        }
        getNavigation().stop();
        moveTo(bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5, getYRot(), 0);
        startSleeping(bed);
    }

    private boolean validBed(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) return false;
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof net.minecraft.world.level.block.BedBlock && !state.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED);
    }

    private void tickBedtime(ServerLevel level) {
        long now = level.getGameTime();
        if (now < leaveTableAt || getNavigation().isDone()) tickGlance();
        if (now < leaveTableAt || tickCount % 20 != 0) return;
        if (shouldWake(level)) {
            wakeUp();
            return;
        }

        if (!validBed(level, bed)) {
            bed = findBed(level);
            if (bed == null) {
                bedtime = false;
                noBedUntil = now + 1200;
                return;
            }
        }

        long t = level.getDayTime() % 24000L;
        double dist = distanceToSqr(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5);
        if (dist < bedDistance - 0.5) {
            bedDistance = dist;
            bedStuck = 0;
        } else {
            bedStuck++;
        }
        if (t >= StaffDinner.IN_BED_BY || bedStuck >= 15) {
            snapIntoBed(level);
            return;
        }
        if (near(bed, 1.8)) {
            getNavigation().stop();
            startSleeping(bed);
        } else if (getNavigation().isDone() || tickCount % 60 == 0) {
            walkTo(bed, 0.6);
            if (getNavigation().isDone()) getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0.6);
        }
    }

    private void stowCarried(ServerLevel level, Restaurant r) {
        if (carrying.isEmpty()) return;
        returnCards(level, r);
        putFoodBack(level, r, carrying);
        carrying.clear();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Nullable
    private BlockPos findBed(ServerLevel level) {
        BlockPos here = blockPosition();
        return BlockPos.betweenClosedStream(here.offset(-24, -8, -24), here.offset(24, 12, 24)).filter(p -> {
                    var state = level.getBlockState(p);
                    return state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                            && state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
                            && !state.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED);
                })
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(p -> p.distSqr(here))).orElse(null);
    }

    public void say(ServerLevel level, Restaurant r, String text) {
        emote(Emote.ALERT, 100);
        voice(SoundEventRegistry.CHATTER.get(), 0.7f);
        announce(level, r, displayName(), ChatFormatting.AQUA, text);
    }

    public String displayName() {
        return restaurant().flatMap(r -> r.staffRecord(getUUID())).map(s -> s.title() + " " + s.name).orElse("Staff");
    }

    public String firstName() {
        return restaurant().flatMap(r -> r.staffRecord(getUUID())).map(s -> s.name).orElse("Staff");
    }

    private void sayOnce(ServerLevel level, Restaurant r, CustomerEntity about, String text) {
        long now = level.getGameTime();
        if (now - lastCallout.getOrDefault(about.getUUID(), -CALLOUT_COOLDOWN * 2L) < CALLOUT_COOLDOWN) return;
        lastCallout.put(about.getUUID(), now);
        ringBell(level);
        say(level, r, text);
    }

    private void ringBell(ServerLevel level) {
        level.playSound(null, blockPosition(), SoundEventRegistry.SERVICE_BELL.get(), SoundSource.NEUTRAL, 0.7f, 1f);
    }

    public boolean handOverTakings(ServerLevel level, Restaurant r, @Nullable Player to) {
        if (r.till <= 0 || to == null) return false;
        int amount = r.till;
        r.till = 0;
        store().markDirty();
        int max = Config.currency().getDefaultMaxStackSize();
        for (int left = amount; left > 0; left -= max) {
            ItemEntity e = new ItemEntity(level, getX(), getEyeY() - 0.3, getZ(), new ItemStack(Config.currency(), Math.min(max, left)));
            e.setDeltaMovement(to.position().subtract(position()).normalize().scale(0.3).add(0, 0.25, 0));
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        }
        getLookControl().setLookAt(to);
        swing(InteractionHand.MAIN_HAND);
        level.playSound(null, blockPosition(), SoundEventRegistry.CHA_CHING.get(), SoundSource.NEUTRAL, 0.8f, 1f);
        say(level, r, "Here are today's takings: " + amount + " " + new ItemStack(Config.currency()).getHoverName().getString() + ".");
        emote(Emote.SMILE, 60);
        return true;
    }

    @Nullable
    private CustomerEntity customer(ServerLevel level) {
        return targetCustomer != null && level.getEntity(targetCustomer) instanceof CustomerEntity c ? c : null;
    }

    private void reset() {
        targetItem = null;
        targetTable = null;
        job = Job.IDLE;
        targetTray = null;
        targetCustomer = null;
        jobTicks = 0;
        busyUntil = 0;
        whenDone = null;
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void dropCarried() {
        carrying.forEach(s -> Containers.dropItemStack(level(), getX(), getY() + 0.5, getZ(), s));
        carrying.clear();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide || hand != InteractionHand.MAIN_HAND) return InteractionResult.sidedSuccess(level().isClientSide);
        ServerLevel level = (ServerLevel) level();
        Restaurant r = restaurant().orElse(null);
        StaffRecord record = r == null ? null : r.staffRecord(getUUID()).orElse(null);
        if (r == null || record == null) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        long day = store().day();
        int seasonLength = Config.SEASON_LENGTH.get();
        ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(held.getItem());

        if (slacking()) {
            slackUntil = 0;
            record.adjustAffection(-1);
            announce(level, r, Component.literal("<" + player.getName().getString() + "> Quit slacking! There are customers to serve."));
            say(level, r, "Sorry, boss! Back to work.");
        } else if (record.birthdayToday(day, seasonLength) && record.cakeDay != day && FoodRules.cakes().contains(heldId)) {
            boolean favourite = heldId.equals(record.favouriteCake);
            record.cakeDay = day;

            record.adjustAffection(favourite ? (record.hearts() + 1) * 20 - record.affection : 10);
            if (!player.getAbilities().instabuild) held.shrink(1);
            emote(Emote.PARTY, 100);
            level.sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.4, getZ(), 6, 0.4, 0.3, 0.4, 0);
            voice(SoundEventRegistry.LAUGH.get(), 0.8f);
            say(level, r, favourite ? "You remembered my favourite! Thank you, " + player.getName().getString() + ". This means a lot."
                    : "A birthday cake, for me? Thank you, that's so kind.");
        } else if (dinnerSeat != null && !hadMeal && FoodRules.isFood(held)) {
            eat(held.copyWithCount(1));
            if (!player.getAbilities().instabuild) held.shrink(1);
            record.adjustAffection(3);
            speak("Thank you, that's lovely.", 60);
        } else {
            if (missingNote != null) say(level, r, missingNote);
            if (r.till > 0 && dinnerSeat == null) handOverTakings(level, r, player);
            if (player instanceof ServerPlayer sp) openCard(sp, level, r, record, day, seasonLength);
        }
        store().markDirty();
        return InteractionResult.SUCCESS;
    }

    private void openCard(ServerPlayer player, ServerLevel level, Restaurant r, StaffRecord record, long day, int seasonLength) {
        CompoundTag t = new CompoundTag();
        t.putString("restaurant", r.name);
        t.putString("name", record.name);
        t.putString("skin", skin());
        t.putString("title", record.title());
        t.putInt("level", record.level);
        t.putInt("hearts", record.hearts());
        t.putInt("wage", record.wage());
        t.putString("currencyId", BuiltInRegistries.ITEM.getKey(Config.currency()).toString());
        t.putString("cake", record.favouriteCake.toString());
        int bday = record.birthday;
        t.putString("birthday", bday < 0 ? "Not known yet" : GameCalendar.Season.values()[Math.min(3, bday / seasonLength)].label() + " " + (bday % seasonLength + 1));
        boolean owed = !record.paidFor(day - 1) && !record.paidFor(day);
        t.putString("pay", record.unpaidDays > 0 ? "Owed for " + record.unpaidDays + (record.unpaidDays == 1 ? " day" : " days")
                : owed ? "Owed for today" : "Paid up");
        t.putBoolean("owed", record.unpaidDays > 0);
        String[] status = status(level, r, record, day);
        t.putString("status", status[0]);
        t.putString("why", status[1]);
        level.playSound(null, blockPosition(), SoundEventRegistry.MENU_CARD.get(), SoundSource.PLAYERS, 0.5f, 1.1f);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.ladleandlantern.core.network.OpenScreenPayload(com.ladleandlantern.core.network.OpenScreenPayload.EMPLOYEE, t));
    }

    private String[] status(ServerLevel level, Restaurant r, StaffRecord record, long day) {
        if (isSleeping()) return new String[]{"Asleep", "Back at work when the next shift opens"};
        if (bedtime) return new String[]{"Heading to bed", "The day's done"};
        if (dinnerSeat != null) return new String[]{"At dinner with the team", ""};
        if (slacking()) return new String[]{"Taking a break", "Right-click to get them back to work"};
        if (record.unpaidDays > 0) {
            return new String[]{job == Job.IDLE ? "Working, but unhappy" : jobText(), "Owed wages: top up the wage fund to keep them"};
        }
        if (!r.shiftOpen) return new String[]{"Off shift", r.dirtyTables.isEmpty() ? "The restaurant is closed" : "Tidying up after the day"};
        if (job != Job.IDLE) return new String[]{jobText(), ""};
        if (missingNote != null) return new String[]{"Waiting", missingNote};
        return new String[]{role == StaffRole.WAITER ? "Looking for something to do" : "Waiting at the till", "Nobody needs them just now"};
    }

    private String jobText() {
        return switch (job) {
            case FETCH_MENU -> "Fetching a menu card";
            case GIVE_MENU -> "Handing over a menu";
            case TAKE_ORDER -> "Taking an order";
            case TO_TRAY -> role == StaffRole.WAITER ? "Fetching an order from a tray" : "Picking up a takeout order";
            case TO_CUSTOMER -> role == StaffRole.WAITER ? "Serving an order" : "Handing over a bag";
            case TO_RACK -> "Fetching a to-go bag";
            case CLEAN -> "Clearing a table";
            case RETURN_CARDS -> "Taking menu cards back";
            case PICK_UP -> "Picking up a dropped menu";
            default -> "Working";
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", role.name());
        if (home != null) tag.putLong("Home", home.asLong());
        if (bed != null) tag.putLong("Bed", bed.asLong());

        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        carrying.forEach(s -> list.add(s.save(registryAccess())));
        tag.put("Carrying", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        try {
            readSave(tag);
        } catch (Exception e) {
            com.ladleandlantern.core.util.Guard.report(getClass().getSimpleName() + " load", e);
            reset();
        }
    }

    private void readSave(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        role = StaffRole.byName(tag.getString("Role"));
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        bed = tag.contains("Bed") ? BlockPos.of(tag.getLong("Bed")) : null;
        for (net.minecraft.nbt.Tag e : tag.getList("Carrying", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            ItemStack.parse(registryAccess(), e).ifPresent(carrying::add);
        }
    }
}
