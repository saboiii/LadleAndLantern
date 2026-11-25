package com.ladleandlantern.core.entity;

import com.ladleandlantern.core.block.ServedPlateBlock;
import com.ladleandlantern.core.block.TableCrumbsBlock;
import com.ladleandlantern.core.block.entity.ServedPlateBlockEntity;
import com.ladleandlantern.core.network.OpenScreenPayload;
import com.ladleandlantern.core.registry.BlockRegistry;
import com.ladleandlantern.core.util.Fancy;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.block.CashierBlock;
import com.ladleandlantern.core.block.Fixtures;
import com.ladleandlantern.core.block.TipJarBlock;
import com.ladleandlantern.core.block.WaterStationBlock;
import com.ladleandlantern.core.food.FoodRules;
import com.ladleandlantern.core.food.Order;
import com.ladleandlantern.core.food.Scoring;
import com.ladleandlantern.core.food.TableService;
import com.ladleandlantern.core.item.MenuCardItem;
import com.ladleandlantern.core.item.NotebookItem;
import com.ladleandlantern.core.item.PlannerItem;
import com.ladleandlantern.core.item.ThankYouNoteItem;
import com.ladleandlantern.core.item.ToGoBagItem;
import com.ladleandlantern.core.registry.EntityTypeRegistry;
import com.ladleandlantern.core.registry.ItemRegistry;
import com.ladleandlantern.core.registry.SoundEventRegistry;
import com.ladleandlantern.core.restaurant.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.ladleandlantern.core.shift.ShiftManager;
import com.ladleandlantern.core.world.Weather;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.*;

public class CustomerEntity extends RestaurantNpc {
    public enum State { WAITING_LINE, ARRIVING, WANTS_MENU, BROWSING, READY, WAITING_FOOD, SAVOURING, EATING, LEAVING, LEAVING_ANGRY;
        public boolean waiting() { return this == WANTS_MENU || this == BROWSING || this == READY || this == WAITING_FOOD; }
        public boolean leaving() { return this == LEAVING || this == LEAVING_ANGRY; }
        public boolean atTable() { return ordinal() >= WANTS_MENU.ordinal() && ordinal() <= EATING.ordinal(); }
    }

    private static final EntityDataAccessor<Float> PATIENCE = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> KID = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final float HIDDEN = -9f;
    private static final double WALK_SPEED = 0.6;
    private static final double STROLL_OUT_SPEED = 0.45;
    private static final int STUCK_TICKS = 40 * 20;
    private static final int LINE_PATIENCE_TICKS = 4 * 60 * 20;
    private static final int TO_DOOR_TICKS = 30 * 20;
    private static final int NO_ROUTE_TICKS = 6 * 20;
    private static final int WANDER_TICKS = 3 * 60 * 20;
    private static final int MENU_GRACE_TICKS = 40 * 20;
    private static final List<String> YUM = List.of("Mmm!", "Yummy!", "Ooh, lovely.", "Smells amazing.", "This looks so good.",
            "Oh, that's gorgeous.", "Yes please!", "Mmm, just like home.");
    private static final List<String> YUM_KID = List.of("Yay!", "Yummy!", "Mmm!", "Wow!");

    private static final Map<UUID, Crowd> CROWDS = new HashMap<>();
    private record Crowd(long tick, List<CustomerEntity> customers) {}

    public static void clearCaches() {
        CROWDS.clear();
    }

    private UUID profileId;
    private CustomerType type = CustomerType.REGULAR;
    private State state = State.ARRIVING;
    private boolean takeout;
    private BlockPos seatPos;
    private BlockPos cashierPos;
    private Order order = new Order();
    private long arrivalTick;
    private int stateTicks;
    private int eatTicks;
    private int browseTicks;
    private int pendingPay;
    private int pendingTip;
    private boolean pendingRibbon;
    private boolean leaveNote;
    private UUID payTo;
    private boolean throughDoor;
    private boolean staffServed;

    @Nullable
    private String servedByName;
    private boolean music;
    private int noRouteTicks;
    private int nodTicks;
    private List<ResourceLocation> eating = new ArrayList<>();

    private List<ResourceLocation> stillOnPlate = new ArrayList<>();
    private int eatIndex, eatSegment;

    private BlockPos wanderTarget;

    @Nullable private UUID partner;
    private int roses;
    private long nextRoseAt;
    @Nullable private UUID roseInFlight;

    private boolean cardOnTable;

    private double heading = Double.NaN;

    private BlockPos detour;
    private int detourTicks;
    private boolean detourDone;

    private boolean turnedAway;

    private BlockPos exitDoor;
    private boolean pastDoor;

    private BlockPos tipJar;

    private BlockPos useSpot;

    public CustomerEntity(EntityType<? extends CustomerEntity> type, Level level) {
        super(type, level);
    }

    public static CustomerEntity spawn(ServerLevel level, Restaurant r, CustomerProfile profile, boolean takeout,
            @Nullable BlockPos seat, @Nullable BlockPos cashier, List<Order.Line> lines, BlockPos at, boolean kid) {
        CustomerEntity c = create(level, r, profile, at);
        c.takeout = takeout;
        c.seatPos = seat;
        c.cashierPos = cashier;
        c.entityData.set(KID, kid);

        c.state = (takeout ? cashier == null : seat == null) ? State.WAITING_LINE : State.ARRIVING;
        c.order.lines.addAll(lines);
        c.order.ticket = ++r.ticketCounter;
        int patience = profile.type.rushed() ? Config.RUSH_PATIENCE.get() : Config.REGULAR_PATIENCE.get();
        c.music = Fixtures.musicPlaying(level, r);
        c.order.deadlineTicks = (int) ((patience + Config.PER_ITEM_SECONDS.get() * lines.size()) * 20 * (c.music ? 1.15 : 1.0));
        level.addFreshEntity(c);
        return c;
    }

    public static void turnAway(ServerLevel level, Restaurant r, CustomerProfile profile, BlockPos at) {
        CustomerEntity c = create(level, r, profile, at);
        c.turnedAway = true;
        level.addFreshEntity(c);
    }

    private static CustomerEntity create(ServerLevel level, Restaurant r, CustomerProfile profile, BlockPos at) {
        CustomerEntity c = new CustomerEntity(EntityTypeRegistry.CUSTOMER.get(), level);
        c.restaurantId = r.id;
        c.profileId = profile.id;
        c.type = profile.type;
        c.arrivalTick = level.getGameTime();
        c.setSkin(profile.skin);
        c.refreshName(profile);
        c.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        return c;
    }

    public static List<CustomerEntity> inRestaurant(ServerLevel level, Restaurant r) {
        long tick = level.getGameTime();
        Crowd crowd = CROWDS.get(r.id);
        if (crowd == null || crowd.tick != tick) {
            if (CROWDS.size() > 64) CROWDS.clear();
            crowd = new Crowd(tick, level.getEntitiesOfClass(CustomerEntity.class, r.searchBox(), c -> r.id.equals(c.restaurantId)));
            CROWDS.put(r.id, crowd);
        }
        return crowd.customers.stream().filter(CustomerEntity::isAlive).toList();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PATIENCE, HIDDEN);
        builder.define(KID, false);
    }

    public float patience() {
        return entityData.get(PATIENCE);
    }

    public boolean isKid() {
        return entityData.get(KID);
    }

    @Override
    public boolean isBaby() {
        return isKid();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KID.equals(key)) refreshDimensions();
    }

    @Override
    public float voicePitch() {
        return super.voicePitch() + (isKid() ? 0.4f : 0f);
    }

    @Override
    protected boolean canSit() {
        return true;
    }

    public State state() { return state; }
    public boolean takeout() { return takeout; }
    public Order order() { return order; }
    public CustomerType type() { return type; }
    @Nullable public BlockPos seatPos() { return seatPos; }
    @Nullable public BlockPos cashierPos() { return cashierPos; }
    public long arrivalTick() { return arrivalTick; }
    public UUID profileId() { return profileId; }

    public boolean occupies(BlockPos seat) {
        if (seat.equals(seatPos) && !state.leaving() && state != State.WAITING_LINE) return true;
        double dx = getX() - (seat.getX() + 0.5), dz = getZ() - (seat.getZ() + 0.5);
        return dx * dx + dz * dz < 1.0 && getY() > seat.getY() - 1 && getY() < seat.getY() + 2;
    }

    public void assignTill(BlockPos till) {
        if (state != State.WAITING_LINE || !takeout) return;
        cashierPos = till;
        setState(State.ARRIVING);
    }

    public void assignSeat(BlockPos seat) {
        if (state != State.WAITING_LINE || takeout) return;
        seatPos = seat;
        setState(State.ARRIVING);
    }

    public String label(Restaurant r) {
        if (takeout) return "Takeout #" + order.ticket;
        String table = seatPos == null ? null : r.seatLabels().get(seatPos);
        return "Table " + (table == null ? "?" : table);
    }

    @Nullable
    private CustomerProfile profile() {
        return restaurant().map(r -> r.customers.get(profileId)).orElse(null);
    }

    private void refreshName(CustomerProfile p) {
        setCustomName(Component.literal(p.name).withColor(p.type.colour));
        setRating(STARS, p.stars, CustomerProfile.MAX_STARS);
    }

    private void setState(State next) {
        if (next.leaving() && !state.leaving()) {
            dropMenuCard();
            if (getOffhandItem().is(Items.POPPY)) setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        state = next;
        stateTicks = 0;
    }

    @Override
    protected void customServerAiStep() {
        try {
            serverStep();
        } catch (Exception e) {
            com.ladleandlantern.core.util.Guard.report(getClass().getSimpleName() + " tick", e);

            discard();
        }
    }

    private boolean broken;

    private void serverStep() {
        if (broken) {
            discard();
            return;
        }
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        Restaurant r = restaurant().orElse(null);
        if (r == null || r.entrance == null) {
            discard();
            return;
        }
        stateTicks++;
        long now = level.getGameTime();
        if (tickCount % 10 == 0) {
            float p = state.waiting() && order.started() ? Math.max(-1f, Math.round(order.patience(now) * 50f) / 50f) : HIDDEN;
            if (p != patience()) entityData.set(PATIENCE, p);
        }
        if (!throughDoor && !turnedAway && near(r.entrance, 2.0)) ringDoorbell(level, r);
        if (detour != null) {
            tickDetour(level, r);
            return;
        }

        if (takeout && state != State.WAITING_LINE && (state == State.ARRIVING || state.waiting())) tickQueue(level, r, now);
        else if (state == State.WAITING_LINE) tickLine(level, r);
        if (state.atTable()) tickTableLife(level, r);
        if (nodTicks > 0) tickNod();
        else if (getNavigation().isDone() && state != State.BROWSING) tickGlance();
        if (tickCount % 10 != 0) return;

        switch (state) {
            case ARRIVING -> {
                if (turnedAway) tickTurnedAway(level, r);
                else if (!takeout) tickWalkToSeat(level, r, now);
            }
            case WANTS_MENU, BROWSING, READY, WAITING_FOOD -> {
                if (!takeout) keepSeated(level, r);
                if (state == State.WAITING_FOOD && !takeout && stateTicks % 20 == 0) checkTable(level, r, null);
                if (stateTicks % 40 == 0) moodBubble();
            }
            case SAVOURING, EATING -> keepSeated(level, r);
            case LEAVING, LEAVING_ANGRY -> tickLeaving(level, r);
            default -> {}
        }
        if (state.waiting() && order.started() && order.elapsed(now) > 2L * order.deadlineTicks) {
            walkOut(r, true);
        }
    }

    private void tickTurnedAway(ServerLevel level, Restaurant r) {
        if (near(r.entrance, 3.0) || stateTicks > 40 * 20) {
            getNavigation().stop();
            lookAt(EntityAnchorArgument.Anchor.EYES, r.centre());
            speak(random.nextBoolean() ? "Oh, it's packed. Another time." : "No room today. Maybe tomorrow.", 60);
            voice(SoundEvents.VILLAGER_NO, 0.6f);
            setState(State.LEAVING);
        } else if (stateTicks % 20 == 0 || getNavigation().isDone()) {
            walkTo(r.entrance, WALK_SPEED);
        }
    }

    private void ringDoorbell(ServerLevel level, Restaurant r) {
        throughDoor = true;
        level.playSound(null, r.entrance, SoundEventRegistry.DOORBELL.get(), SoundSource.BLOCKS, 0.7f, 1f);
        if (state.leaving() || !Weather.wet(level, r)) return;

        level.playSound(null, blockPosition(), SoundEvents.WOLF_SHAKE, SoundSource.NEUTRAL, 0.6f, 1.3f);
        level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.2, getZ(), 24, 0.4, 0.5, 0.4, 0.1);
        level.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY() + 1.6, getZ(), 8, 0.3, 0.2, 0.3, 0);
    }

    private void tickLine(ServerLevel level, Restaurant r) {
        if (r.waitingLine.isEmpty() || stateTicks > LINE_PATIENCE_TICKS) {
            setState(State.LEAVING);
            return;
        }
        List<CustomerEntity> line = inRestaurant(level, r).stream().filter(c -> c.state == State.WAITING_LINE)
                .sorted(Comparator.comparingLong(c -> c.arrivalTick)).toList();
        BlockPos spot = r.waitingLine.get(Math.min(Math.max(0, line.indexOf(this)), r.waitingLine.size() - 1));
        standAt(spot, null);
    }

    private void tickWalkToSeat(ServerLevel level, Restaurant r, long now) {
        Seat seat = seatPos == null ? null : r.seatAt(seatPos).orElse(null);
        if (seat == null || level.getBlockState(seatPos).isAir()) {
            setState(State.LEAVING);
            return;
        }
        if (throughDoor && !detourDone && maybeDetour(level, r, 0.3f)) return;

        if ((r.seatDirty(seat) || ShiftManager.playerSittingOn(level, seatPos)) && stateTicks % 20 == 0) {
            List<BlockPos> clean = ShiftManager.freeSeats(level, r, inRestaurant(level, r));
            if (!clean.isEmpty()) {
                seatPos = clean.get(random.nextInt(clean.size()));
                return;
            }
        }
        boolean someoneThere = ShiftManager.playerSittingOn(level, seatPos);
        if (r.seatDirty(seat) || someoneThere) {
            if (near(seatPos, 2.5)) {
                getNavigation().stop();
                if (stateTicks % 100 == 0) speak(someoneThere ? "Oh, someone's sitting here. I'll wait."
                        : random.nextBoolean() ? "I'll wait for them to clear it." : "Just a moment while they tidy up.", 60);
                stateTicks = Math.min(stateTicks, STUCK_TICKS - 1);
            } else if (stateTicks % 20 == 0 || getNavigation().isDone()) {
                walkTo(seatPos, WALK_SPEED);
            }
            return;
        }
        if (near(seatPos, 1.8)) {
            sitOn(level, seat);
            order.readyTick = now;
            order.deadlineTicks += MENU_GRACE_TICKS;
            setState(State.WANTS_MENU);
            emote(Emote.MENU, 80);
            remark(level, r);
        } else if (stateTicks > STUCK_TICKS) {
            if (!throughDoor) ringDoorbell(level, r);
            teleportTo(seatPos.getX() + 0.5, seatPos.getY() + 1, seatPos.getZ() + 0.5);
        } else if (stateTicks % 20 == 0 || getNavigation().isDone()) {
            walkTo(seatPos, WALK_SPEED);
        }
    }

    private void tickQueue(ServerLevel level, Restaurant r, long now) {
        if (!validCashier(r)) return;
        BlockPos spot = queueSpot(level, r);
        if (state == State.ARRIVING) {
            if (horizontalDistance(spot) < 1.0 && Math.abs(getY() - spot.getY()) < 1.5) {
                order.readyTick = now;
                setState(State.READY);
                remark(level, r);
            } else if (stateTicks > STUCK_TICKS) {
                if (!throughDoor) ringDoorbell(level, r);
                teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            }
        }
        standAt(spot, cashierPos);
    }

    private boolean validCashier(Restaurant r) {
        if (cashierPos != null && r.cashiers.contains(cashierPos)) return true;
        cashierPos = r.cashiers.isEmpty() ? null : r.cashiers.get(0);
        if (cashierPos == null) setState(State.LEAVING);
        return cashierPos != null;
    }

    private void standAt(BlockPos spot, @Nullable BlockPos faceTowards) {
        double d = horizontalDistance(spot);
        if (d > 1.2 || Math.abs(getY() - spot.getY()) > 1.0) {
            if (stateTicks % 20 == 0 || getNavigation().isDone()) {
                getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, WALK_SPEED);
            }
        } else if (d > 0.15) {
            getNavigation().stop();
            Vec3 to = new Vec3(spot.getX() + 0.5 - getX(), 0, spot.getZ() + 0.5 - getZ()).normalize().scale(Math.min(0.1, d * 0.4));
            setDeltaMovement(to.x, Math.min(0, getDeltaMovement().y), to.z);
            setYRot((float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90));
            yBodyRot = getYRot();
        } else {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (faceTowards != null && tickCount % 20 == 0) lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(faceTowards));
        }
    }

    public int queueIndex(ServerLevel level, Restaurant r) {
        List<CustomerEntity> line = inRestaurant(level, r).stream()
                .filter(c -> c.takeout && Objects.equals(c.cashierPos, cashierPos)
                        && (c.state == State.ARRIVING || c.state.waiting()))
                .sorted(Comparator.comparingLong(c -> c.arrivalTick)).toList();
        return Math.max(0, line.indexOf(this));
    }

    private BlockPos queueSpot(ServerLevel level, Restaurant r) {
        List<BlockPos> spots = CashierBlock.queueSpots(level, r, cashierPos);
        if (spots.isEmpty()) return cashierPos.relative(CashierBlock.customerSide(level, cashierPos, r.entrance));
        return spots.get(Math.min(queueIndex(level, r), spots.size() - 1));
    }

    private void keepSeated(ServerLevel level, Restaurant r) {
        if (isPassenger()) return;
        r.seatAt(seatPos).ifPresent(seat -> sitOn(level, seat));
    }

    private void moodBubble() {
        if (emote() != Emote.NONE || !speech().isEmpty() || random.nextFloat() > 0.35f) return;
        float p = patience();
        if (p < 0.3f) emote(Emote.ANGRY, 50);
        else if (music && random.nextFloat() < 0.3f) emote(Emote.MUSIC, 50);
        else if (p > 0.6f) emote(hasCompany() ? Emote.TALK : Emote.SMILE, 50);
    }

    private boolean hasCompany() {
        if (!(level() instanceof ServerLevel level)) return false;
        Restaurant r = restaurant().orElse(null);
        if (r == null) return false;
        for (CustomerEntity c : inRestaurant(level, r)) if (c != this && c.isPassenger() && c.distanceToSqr(this) < 16) return true;
        return false;
    }

    public static void pairUp(CustomerEntity a, CustomerEntity b) {
        a.partner = b.getUUID();
        b.partner = a.getUUID();
        long now = a.level().getGameTime();

        CustomerEntity first = a.random.nextBoolean() ? a : b;
        first.nextRoseAt = now + 30 * 20 + a.random.nextInt(30 * 20);
        (first == a ? b : a).nextRoseAt = Long.MAX_VALUE;
    }

    private void tickCouple(ServerLevel level) {
        if (partner == null || !(level.getEntity(partner) instanceof CustomerEntity other) || !other.isAlive()) return;
        long now = level.getGameTime();
        boolean bothSeated = isPassenger() && other.isPassenger();
        if (roseInFlight != null) {
            if (level.getEntity(roseInFlight) instanceof ItemEntity rose && rose.isAlive()) {
                if (rose.tickCount > 18 || rose.distanceTo(other) < 1.0) {
                    rose.discard();
                    roseInFlight = null;
                    other.catchRose();
                }
            } else {
                roseInFlight = null;
            }
            return;
        }
        if (bothSeated && roses < 1 && now >= nextRoseAt && state.atTable() && !state.leaving()) {
            ItemEntity rose = new ItemEntity(level, getX(), getEyeY() - 0.1, getZ(), new ItemStack(Items.POPPY));
            Vec3 toward = other.getEyePosition().subtract(rose.position());
            rose.setDeltaMovement(toward.x * 0.18, toward.y * 0.18 + 0.22, toward.z * 0.18);
            rose.setPickUpDelay(32767);
            rose.setUnlimitedLifetime();
            level.addFreshEntity(rose);
            roseInFlight = rose.getUUID();
            roses = 1;
            swing(InteractionHand.MAIN_HAND);
            getLookControl().setLookAt(other);
        }
        if (getOffhandItem().is(Items.POPPY) && tickCount % 30 == 0) {
            level.sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.6, getZ(), 1, 0.2, 0.1, 0.2, 0);
            level.sendParticles(ParticleTypes.HEART, other.getX(), other.getEyeY() + 0.6, other.getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    private void catchRose() {
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.POPPY));
        emote(Emote.SMILE, 60);
        playSound(SoundEvents.ITEM_PICKUP, 0.5f, 1.4f);
        if (roses == 0) nextRoseAt = level().getGameTime() + 15 * 20 + random.nextInt(20 * 20);
    }

    private void tickTableLife(ServerLevel level, Restaurant r) {
        if (partner != null) tickCouple(level);
        boolean company = tickCount % 20 == 0 ? hasCompany() : false;

        if (company && random.nextInt(11) == 0) {
            if (emote() == Emote.NONE && speech().isEmpty()) emote(Emote.TALK, 40);
        } else if (company && random.nextInt(40) == 0) {
            if (speech().isEmpty()) emote(Emote.SMILE, 30);
        }
        switch (state) {
            case BROWSING -> {
                setXRot(25f);
                if (random.nextInt(70) == 0) level.playSound(null, blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.5f, 1f);
                if (--browseTicks <= 0) {
                    setXRot(0f);
                    setState(State.READY);
                    speak(isKid() ? "I know what I want!" : "Ready to order.", 60);
                    emote(Emote.THINK, 60);
                }
            }
            case SAVOURING -> {
                if (stateTicks == 1) {
                    List<String> lines = isKid() ? YUM_KID : YUM;
                    speak(lines.get(random.nextInt(lines.size())), 60);
                    voice(SoundEventRegistry.MMM.get(), 0.7f);
                    emote(Emote.YUM, 60);
                }
                if (stateTicks >= 70) {
                    eatTicks = 400 + random.nextInt(300);
                    stillOnPlate = new ArrayList<>(eating);
                    eatIndex = 0;
                    eatSegment = eatTicks / Math.max(1, eating.size());
                    if (!eating.isEmpty()) startOn(level, r, eating.get(0));
                    setState(State.EATING);
                }
            }
            case EATING -> tickEating(level, r);
            default -> {}
        }
    }

    private void tickEating(ServerLevel level, Restaurant r) {
        if (eatIndex < eating.size() - 1 && eatTicks <= (eating.size() - 1 - eatIndex) * eatSegment) {
            eatIndex++;
            startOn(level, r, eating.get(eatIndex));
        }
        ItemStack food = getMainHandItem();
        if (eatTicks % 6 == 0 && !food.isEmpty() && eatTicks % 60 < 24) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food), getX(), getEyeY() - 0.2, getZ(), 3, 0.15, 0.1, 0.15, 0.05);
            if (eatTicks % 12 == 0) playSound(SoundEvents.GENERIC_EAT, 0.4f, 0.9f + random.nextFloat() * 0.2f);
        }
        if (random.nextInt(40) == 0) level.playSound(null, blockPosition(), SoundEventRegistry.CUTLERY.get(), SoundSource.NEUTRAL, 0.35f, 0.9f + random.nextFloat() * 0.25f);
        if (--eatTicks <= 0) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            clearPlates(level, r);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getEyeY() + 0.3, getZ(), 5, 0.3, 0.2, 0.3, 0);
            pay(level, r);
            if (leaveNote) leaveThankYouNote(level, r);
            setState(State.LEAVING);
        }
    }

    private void startOn(ServerLevel level, Restaurant r, ResourceLocation id) {
        setItemSlot(EquipmentSlot.MAINHAND, FoodRules.stackOf(id));
        Seat seat = seatPos == null ? null : r.seatAt(seatPos).orElse(null);
        if (seat == null || !stillOnPlate.remove(id)) return;
        for (TableService.Found f : TableService.scan(level, seat)) {
            if (BuiltInRegistries.ITEM.getKey(f.stack().getItem()).equals(id)) {
                f.consume().run();
                return;
            }
        }
    }

    public List<ResourceLocation> stillWanted() {
        if (takeout) return List.of();
        if (state == State.WAITING_FOOD) return order.items();
        if (state == State.SAVOURING) return eating;
        if (state == State.EATING) return stillOnPlate;
        return List.of();
    }

    private void clearPlates(ServerLevel level, Restaurant r) {
        Seat seat = seatPos == null ? null : r.seatAt(seatPos).orElse(null);
        if (seat == null) return;
        List<ResourceLocation> left = new ArrayList<>(stillOnPlate.isEmpty() && eatIndex == 0 ? eating : stillOnPlate);
        for (TableService.Found f : TableService.scan(level, seat)) {
            if (left.remove(BuiltInRegistries.ITEM.getKey(f.stack().getItem()))) f.consume().run();
        }
        eating.clear();
    }

    private void leaveThankYouNote(ServerLevel level, Restaurant r) {
        Seat seat = seatPos == null ? null : r.seatAt(seatPos).orElse(null);
        CustomerProfile profile = r.customers.get(profileId);
        if (seat == null || seat.table.isEmpty() || profile == null) return;
        BlockPos table = seat.table.iterator().next();
        String dish = order.items().isEmpty() ? "meal" : FoodRules.nameOf(order.items().get(0));
        ItemEntity note = new ItemEntity(level, table.getX() + 0.5, table.getY() + 1.05, table.getZ() + 0.5,
                ThankYouNoteItem.write(profile.name, dish, random));
        note.setDeltaMovement(Vec3.ZERO);
        note.setUnlimitedLifetime();
        level.addFreshEntity(note);
    }

    private void tickNod() {
        getNavigation().stop();
        setXRot((float) Math.sin(nodTicks * 0.6) * 20f + 10f);
        if (--nodTicks == 0) setXRot(0);
    }

    private void remark(ServerLevel level, Restaurant r) {
        long day = store().day();
        if (Weather.wet(level, r) && r.fixtures(Fixture.UMBRELLA_STAND).isEmpty()) {
            sayAloud(level, r, "umbrella", day, Emote.UMBRELLA, "This place could use a spot to keep our umbrellas...");
        } else if (Weather.cold(level, r, day) && r.fixtures(Fixture.COAT_RACK).isEmpty()) {
            sayAloud(level, r, "coat", day, Emote.COAT, "Could do with somewhere to hang my coat...");
        }
    }

    private void sayAloud(ServerLevel level, Restaurant r, String key, long day, Emote emote, String text) {
        if (r.remarks.getOrDefault(key, -1L) == day || random.nextFloat() > 0.5f) return;
        r.remarks.put(key, day);
        store().markDirty();
        speak(text, 100);
        emote(emote, 100);
        voice(SoundEventRegistry.CHATTER.get(), 0.6f);
        CustomerProfile p = profile();
        announce(level, r, p == null ? "Customer" : p.name, ChatFormatting.YELLOW, text);
    }

    private boolean maybeDetour(ServerLevel level, Restaurant r, float chance) {
        detourDone = true;
        if (isKid() || random.nextFloat() > chance) return false;
        for (BlockPos station : r.fixtures(Fixture.WATER_STATION)) {
            if (WaterStationBlock.glasses(level, station) > 0 && station.distSqr(blockPosition()) < 24 * 24) {
                BlockPos spot = standSpotNear(level, r, station);
                if (spot == null) continue;
                detour = station;
                useSpot = spot;
                detourTicks = 0;
                return true;
            }
        }
        return false;
    }

    @Nullable
    private BlockPos standSpotNear(ServerLevel level, Restaurant r, BlockPos fixture) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos front = com.ladleandlantern.core.block.Fixtures.front(level, fixture);
        candidates.add(front);
        candidates.add(front.below());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            candidates.add(fixture.relative(d));
            candidates.add(fixture.relative(d).below());
        }
        for (BlockPos p : candidates) {
            boolean clear = level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
                    && !r.noWalk().contains(p.asLong()) && !r.noWalk().contains(p.below().asLong());
            if (clear && canReach(p)) return p;
        }
        return null;
    }

    private boolean atSpot(BlockPos spot) {
        if (horizontalDistance(spot) < 1.3 && Math.abs(getY() - spot.getY()) < 1.2) return true;
        BlockPos fixture = detour != null ? detour : tipJar;
        return fixture != null && distanceToSqr(Vec3.atCenterOf(fixture)) < 2.4 * 2.4;
    }

    private void tickDetour(ServerLevel level, Restaurant r) {
        detourTicks++;
        if (detourTicks > 10 * 20 || useSpot == null) {
            endDetour();
            return;
        }
        if (getMainHandItem().is(Items.POTION)) {
            getLookControl().setLookAt(Vec3.atCenterOf(detour));
            if (detourTicks % 8 == 0) playSound(SoundEvents.GENERIC_DRINK, 0.5f, voicePitch());
            if (--browseTicks <= 0) {
                setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (random.nextFloat() < 0.5f) speak("Ahh, refreshing.", 40);
                endDetour();
            }
            return;
        }
        BlockPos spot = useSpot;
        if (atSpot(spot)) {
            getNavigation().stop();
            lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(detour));
            if (WaterStationBlock.useGlass(level, detour)) {
                setItemSlot(EquipmentSlot.MAINHAND, PotionContents.createItemStack(Items.POTION, Potions.WATER));
                emote(Emote.DRINK, 50);
                browseTicks = 50;
            } else {
                endDetour();
            }
        } else if (detourTicks % 20 == 1 || getNavigation().isDone()) {
            getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, WALK_SPEED);
        }
    }

    private void endDetour() {
        detour = null;
        useSpot = null;
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        stateTicks = 0;
    }

    @Override
    public net.minecraft.world.phys.AABB pathBounds() {
        if (takeout || !state.atTable() && state != State.READY) return null;
        return restaurant().map(r -> r.box().inflate(1, 0, 1).expandTowards(0, 1, 0)).orElse(null);
    }

    @Override
    protected BlockPos returnPoint() {
        return seatPos != null ? seatPos : restaurant().map(r -> r.entrance).orElse(null);
    }

    private void tickLeaving(ServerLevel level, Restaurant r) {
        if (isPassenger()) stopRiding();
        if (nodTicks > 0) return;
        boolean angry = state == State.LEAVING_ANGRY;
        double speed = angry ? 0.65 : STROLL_OUT_SPEED;
        if (angry && stateTicks % 20 == 0) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getEyeY() + 0.4, getZ(), 2, 0.3, 0.2, 0.3, 0);
        }
        if (tipJar != null) {
            tickTipJar(level, r);
            return;
        }

        if (exitDoor == null) exitDoor = findDoor(level, r);
        if (!pastDoor && exitDoor != null && near(exitDoor, 1.3)) pastDoor = true;
        BlockPos door = exitDoor != null && !pastDoor ? exitDoor : r.entrance;
        if (wanderTarget == null) {
            if (!angry && !takeout && !detourDone && stateTicks > 20 && maybeDetour(level, r, 0.15f)) return;
            if (door == r.entrance && near(door, 2.0)) {
                net.minecraft.world.phys.Vec3 away = Vec3.atCenterOf(r.entrance).subtract(r.centre()).multiply(1, 0, 1);
                double base = away.lengthSqr() < 0.01 ? random.nextDouble() * Math.PI * 2 : Math.atan2(away.z, away.x);
                heading = base + (random.nextDouble() - 0.5) * Math.PI * 0.5;
                wanderTarget = ShiftManager.outsidePoint(level, r, random, 4, 7);
                if (wanderTarget == null) wanderTarget = nextWaypoint(level, r);
                stateTicks = 0;
                if (wanderTarget == null) slipAway();
                else getNavigation().moveTo(wanderTarget.getX() + 0.5, wanderTarget.getY(), wanderTarget.getZ() + 0.5, speed);
                return;
            }
            if (stateTicks % 20 == 0 || getNavigation().isDone()) {
                if (canReach(door)) {
                    noRouteTicks = 0;
                    walkTo(door, speed);
                } else {
                    noRouteTicks += 20;
                    Path near = safePath(door, 3, 1);
                    if (near != null) getNavigation().moveTo(near, speed);
                }
            }

            boolean watched = nearestPlayerDistance(level) < 16;
            if ((noRouteTicks >= NO_ROUTE_TICKS * 4 || stateTicks > TO_DOOR_TICKS) && (!watched || stateTicks > TO_DOOR_TICKS * 2)) slipAway();
            return;
        }

        if (stateTicks % 20 == 0 && nearestPlayerDistance(level) > horizon(level)) {
            discard();
            return;
        }

        if (stateTicks > WANDER_TICKS && stateTicks % 20 == 0 && (nearestPlayerDistance(level) > 48 || stateTicks > WANDER_TICKS * 4)) {
            discard();
            return;
        }
        if (near(wanderTarget, 3.0) || getNavigation().isDone() && stateTicks % 20 == 0) {
            BlockPos next = nextWaypoint(level, r);
            if (next == null) {
                if (nearestPlayerDistance(level) > 24) discard();
                return;
            }
            wanderTarget = next;
            getNavigation().moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, speed);
        } else if (stateTicks % 40 == 0 && getNavigation().isDone()) {
            getNavigation().moveTo(wanderTarget.getX() + 0.5, wanderTarget.getY(), wanderTarget.getZ() + 0.5, speed);
        }
    }

    private double horizon(ServerLevel level) {
        int chunks = Math.min(getType().clientTrackingRange(), Math.max(2, level.getServer().getPlayerList().getViewDistance() - 1));
        return chunks * 16.0;
    }

    private double nearestPlayerDistance(ServerLevel level) {
        Player p = level.getNearestPlayer(getX(), getY(), getZ(), -1, (java.util.function.Predicate<net.minecraft.world.entity.Entity>) null);
        return p == null ? Double.MAX_VALUE : p.distanceTo(this);
    }

    @Nullable
    private BlockPos nextWaypoint(ServerLevel level, Restaurant r) {
        if (Double.isNaN(heading)) heading = random.nextDouble() * Math.PI * 2;
        for (int dist : new int[]{12, 8, 5}) {
            for (int tries = 0; tries < 12; tries++) {
                double angle = heading + (tries == 0 ? 0 : (tries % 2 == 0 ? 1 : -1) * ((tries + 1) / 2) * 0.35);
                int x = getBlockX() + (int) Math.round(Math.cos(angle) * dist);
                int z = getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
                if (!level.isPositionEntityTicking(new BlockPos(x, getBlockY(), z))) continue;
                BlockPos ground = standingSpot(level, x, getBlockY(), z);
                if (ground == null || r.nearBounds(ground, 1)) continue;
                heading = angle;
                return ground;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos standingSpot(ServerLevel level, int x, int y, int z) {
        for (int dy = 4; dy >= -6; dy--) {
            BlockPos feet = new BlockPos(x, y + dy, z);
            if (standable(level, feet) && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.below()).isEmpty()) {
                return feet;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findDoor(ServerLevel level, Restaurant r) {
        return BlockPos.betweenClosedStream(r.entrance.offset(-1, -1, -1), r.entrance.offset(1, 1, 1))
                .filter(p -> level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        && level.getBlockState(p).getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(p -> p.distSqr(r.entrance))).orElse(null);
    }

    private void tickTipJar(ServerLevel level, Restaurant r) {
        if (useSpot == null) useSpot = standSpotNear(level, r, tipJar);
        BlockPos spot = useSpot == null ? tipJar : useSpot;

        if (useSpot == null || atSpot(spot) || stateTicks > 8 * 20) {
            useSpot = null;
            getNavigation().stop();
            lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(tipJar));
            swing(InteractionHand.MAIN_HAND);
            int tip = pendingTip;
            pendingTip = 0;
            if (TipJarBlock.addTips(level, tipJar, tip)) {
                r.shiftTips += tip;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, tipJar.getX() + 0.5, tipJar.getY() + 0.8, tipJar.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0);
                store().markDirty();
            }
            tipJar = null;
            stateTicks = 0;
        } else if (stateTicks % 20 == 0 || getNavigation().isDone()) {
            getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, STROLL_OUT_SPEED);
        }
    }

    public boolean handMenu(ItemStack card) {
        if (state != State.WANTS_MENU || !MenuCardItem.isWritten(card)) return false;
        setItemSlot(EquipmentSlot.MAINHAND, card.copyWithCount(1));
        browseTicks = (isKid() ? 80 : 100) + random.nextInt(100);
        setState(State.BROWSING);
        emote(Emote.MENU, 40);
        playSound(SoundEventRegistry.MENU_CARD.get(), 0.8f, 1f);
        return true;
    }

    public ItemStack takeOrder(@Nullable ServerPlayer by) {
        if (state != State.READY) return ItemStack.EMPTY;
        order.taken = true;
        setState(State.WAITING_FOOD);
        ItemStack card = getMainHandItem().is(ItemRegistry.MENU_CARD.get()) ? getMainHandItem().copy() : ItemStack.EMPTY;
        if (!card.isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (by == null && !card.isEmpty() && seatPos != null) {
            cardOnTable = true;
            card = ItemStack.EMPTY;
        }
        if (by != null) {
            by.displayClientMessage(Component.literal("Order taken: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(names(order.items()))), false);
            level().playSound(null, by.blockPosition(), SoundEvents.VILLAGER_WORK_LIBRARIAN, SoundSource.PLAYERS, 0.8f, 1f);
            if (!card.isEmpty() && !by.getInventory().add(card)) by.drop(card, false);
            card = ItemStack.EMPTY;
        }
        voice(SoundEvents.VILLAGER_YES, 0.5f);
        emote(Emote.SMILE, 30);
        return card;
    }

    private void dropMenuCard() {
        if (!(level() instanceof ServerLevel level)) return;
        ItemStack held = getMainHandItem();
        boolean holdingCard = held.is(ItemRegistry.MENU_CARD.get());
        if (holdingCard) setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        boolean ate = state == State.EATING;
        boolean card = cardOnTable || holdingCard;
        cardOnTable = false;
        Restaurant r = restaurant().orElse(null);
        Seat seat = r == null || seatPos == null ? null : r.seatAt(seatPos).orElse(null);
        if (seat != null && !seat.table.isEmpty()) {
            boolean leftovers = !takeout && !TableService.scan(level, seat).isEmpty();
            if (ate || card || leftovers) {
                markTable(level, r, seat, ate || leftovers, card);
                return;
            }
        }
        if (holdingCard) {
            ItemEntity e = new ItemEntity(level, getX(), getY() + 0.6, getZ(), held);
            e.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(e);
        }
    }

    private void markTable(ServerLevel level, Restaurant r, Seat seat, boolean crumbs, boolean card) {
        var crumbsBlock = com.ladleandlantern.core.registry.BlockRegistry.TABLE_CRUMBS.get();
        var plateBlock = com.ladleandlantern.core.registry.BlockRegistry.SERVED_PLATE.get();
        boolean cardPlaced = !card, marked = false;
        for (BlockPos table : seat.table) {
            BlockPos top = table.above().immutable();
            BlockState here = level.getBlockState(top);
            if (!here.is(plateBlock)) continue;
            boolean empty = !(level.getBlockEntity(top) instanceof com.ladleandlantern.core.block.entity.ServedPlateBlockEntity plate) || plate.food().isEmpty();
            if (empty) {
                level.setBlock(top, crumbsBlock.defaultBlockState()
                        .setValue(com.ladleandlantern.core.block.TableCrumbsBlock.FACING, here.getValue(com.ladleandlantern.core.block.ServedPlateBlock.FACING))
                        .setValue(com.ladleandlantern.core.block.TableCrumbsBlock.DIRTY, true)
                        .setValue(com.ladleandlantern.core.block.TableCrumbsBlock.MENU, !cardPlaced), Block.UPDATE_ALL);
                if (!cardPlaced) r.tableCards.add(top);
                cardPlaced = true;
            }
            r.dirtyTables.add(top);
            marked = true;
        }
        if (!cardPlaced || !marked) {
            BlockPos table = seat.table.stream().min(Comparator.comparingDouble(t -> t.distSqr(seat.pos))).orElseThrow();
            BlockPos top = table.above().immutable();
            BlockState here = level.getBlockState(top);
            if (here.isAir() || here.is(crumbsBlock)) {
                boolean already = here.is(crumbsBlock);
                level.setBlock(top, crumbsBlock.defaultBlockState()
                        .setValue(com.ladleandlantern.core.block.TableCrumbsBlock.FACING, com.ladleandlantern.core.block.ServedPlateBlock.facing(top, seat.pos)
                                .getValue(com.ladleandlantern.core.block.ServedPlateBlock.FACING))
                        .setValue(com.ladleandlantern.core.block.TableCrumbsBlock.DIRTY, crumbs || already && here.getValue(com.ladleandlantern.core.block.TableCrumbsBlock.DIRTY))
                        .setValue(com.ladleandlantern.core.block.TableCrumbsBlock.MENU, !cardPlaced || already && here.getValue(com.ladleandlantern.core.block.TableCrumbsBlock.MENU)),
                        Block.UPDATE_ALL);
            }
            r.dirtyTables.add(top);
            if (!cardPlaced) r.tableCards.add(top);
        }
        store().markDirty();
    }

    static String names(List<ResourceLocation> ids) {
        return String.join(", ", ids.stream().map(FoodRules::nameOf).toList());
    }

    public List<ResourceLocation> missing(ServerLevel level, Seat seat) {
        List<ResourceLocation> remaining = new ArrayList<>(order.items());
        for (TableService.Found f : TableService.scan(level, seat)) remaining.remove(BuiltInRegistries.ITEM.getKey(f.stack().getItem()));
        return remaining;
    }

    private void checkTable(ServerLevel level, Restaurant r, @Nullable ServerPlayer forcedBy) {
        Seat seat = seatPos == null ? null : r.seatAt(seatPos).orElse(null);
        if (seat == null) return;
        List<TableService.Found> found = TableService.scan(level, seat);
        List<ResourceLocation> remaining = new ArrayList<>(order.items());
        List<TableService.Found> chosen = new ArrayList<>();
        for (TableService.Found f : found) {
            if (remaining.remove(BuiltInRegistries.ITEM.getKey(f.stack().getItem()))) chosen.add(f);
        }

        if (forcedBy == null && !remaining.isEmpty()) {
            List<TableService.Found> wrong = new ArrayList<>();
            for (TableService.Found f : found) {
                if (chosen.contains(f) || wrong.size() >= remaining.size()) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(f.stack().getItem());
                boolean someoneElsesOrder = CustomerEntity.inRestaurant(level, r).stream().anyMatch(o -> o != this
                        && o.state == State.WAITING_FOOD && o.seatPos != null && !o.takeout
                        && r.seatAt(o.seatPos).map(os -> os.table.contains(f.table())).orElse(false)
                        && o.order.items().contains(id));
                if (!someoneElsesOrder) wrong.add(f);
            }
            if (wrong.size() < remaining.size()) return;
            chosen.addAll(wrong);
        }
        if (forcedBy != null) {
            for (TableService.Found f : found) {
                boolean shared = r.seats.stream().anyMatch(o -> o != seat && o.table.contains(f.table()));
                if (!chosen.contains(f) && !shared) chosen.add(f);
            }
        }
        if (chosen.isEmpty()) {
            if (forcedBy != null) info(forcedBy, r, "Nothing for them on the table yet.");
            return;
        }

        List<ItemStack> served = new ArrayList<>();
        List<Runnable> takeAway = new ArrayList<>();
        for (TableService.Found f : chosen) {
            served.add(f.stack());
            takeAway.add(f.consume());
        }
        finish(level, r, served, forcedBy != null ? forcedBy : nearestPlayer(level, r), takeAway);
    }

    public void deliver(List<ItemStack> served, @Nullable ServerPlayer credit, @Nullable String staffName) {
        if (state != State.WAITING_FOOD || !(level() instanceof ServerLevel level)) return;
        Restaurant r = restaurant().orElse(null);
        if (r == null) return;
        boolean byStaff = staffName != null;
        staffServed = byStaff;
        servedByName = staffName;
        Seat seat = takeout || seatPos == null ? null : r.seatAt(seatPos).orElse(null);
        if (seat != null && byStaff) {
            List<ItemStack> leftover = TableService.plateDishes(level, seat, r.seats, served);
            if (leftover.isEmpty()) {
                checkTable(level, r, null);
                return;
            }
        }
        if (takeout) level.playSound(null, blockPosition(), SoundEventRegistry.CHA_CHING.get(), SoundSource.NEUTRAL, 0.7f, 1f);
        else level.playSound(null, blockPosition(), SoundEventRegistry.PLACE_PLATE.get(), SoundSource.NEUTRAL, 0.8f, 0.95f + random.nextFloat() * 0.1f);
        finish(level, r, served, credit != null ? credit : nearestPlayer(level, r), List.of());
    }

    private static void detail(MutableComponent message, String label, String value, int colour, boolean bold) {
        message.append(Component.literal("\n  \u25b8 ").withStyle(s -> s.withColor(com.ladleandlantern.core.util.Fancy.PEACH)))
                .append(com.ladleandlantern.core.util.Fancy.plain(label + "  ", bold ? com.ladleandlantern.core.util.Fancy.CREAM : com.ladleandlantern.core.util.Fancy.SOFT)
                        .withStyle(s -> s.withBold(bold)))
                .append(com.ladleandlantern.core.util.Fancy.plain(value, colour).withStyle(s -> s.withBold(bold)));
    }

    private void finish(ServerLevel level, Restaurant r, List<ItemStack> servedStacks, @Nullable ServerPlayer credit, List<Runnable> takeAway) {
        List<ItemStack> served = TableService.units(servedStacks);
        List<ResourceLocation> ids = served.stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem())).toList();
        Scoring.Result result = Scoring.score(order.items(), ids, order.elapsed(level.getGameTime()), order.deadlineTicks, type.scoreMultiplier);
        RestaurantStore store = store();
        CustomerProfile profile = r.customers.get(profileId);
        boolean favourite = profile != null && ids.stream().anyMatch(profile.favourites::contains);
        if (profile != null) {
            if (profile.visits == 0) r.shiftNewFaces++;
            profile.visits++;
            if (result.score() >= 85) profile.adjustStars(1);
            else if (result.score() < 50) profile.adjustStars(-1);
            refreshName(profile);
        }
        r.shiftOrders++;
        r.shiftScoreTotal += result.score();
        if (result.score() > r.shiftBestScore && !ids.isEmpty()) {
            r.shiftBestScore = result.score();
            r.shiftBestDish = ids.get(0).toString();
        }

        boolean ribbon = type == CustomerType.CRITIC && result.accuracy() >= 1f && result.onTime();
        if (credit != null) {
            payTo = credit.getUUID();
            PlayerProgress progress = store.progress(credit.getUUID());
            int before = progress.rank();
            progress.points += result.score();
            progress.ordersServed++;
            if (ribbon) progress.ribbons++;
            r.bestRank = Math.max(r.bestRank, progress.rank());

            boolean good = result.score() >= Config.ANGRY_THRESHOLD.get();
            if (good) level.playSound(null, credit.blockPosition(), SoundEventRegistry.SUCCESSFUL_SERVE.get(), SoundSource.PLAYERS, 0.8f, 1f);

            String headline = good ? (result.score() >= 90 ? "\u2726 " + label(r) + " loved it \u2726" : "\u2726 " + label(r) + " served \u2726")
                    : label(r) + " wasn't happy";
            int food = Math.round(result.accuracy() * 100);
            int foodStars = Math.round(result.accuracy() * 5);
            MutableComponent scoreLine = good ? com.ladleandlantern.core.util.Fancy.gradient(headline, com.ladleandlantern.core.util.Fancy.PINK, com.ladleandlantern.core.util.Fancy.GOLD)
                    : com.ladleandlantern.core.util.Fancy.plain(headline, 0xE08A7A);
            detail(scoreLine, "Performance", result.score() + "%", com.ladleandlantern.core.util.Fancy.CREAM, true);
            scoreLine.append(Component.literal("\n  \u25b8 ").withStyle(st -> st.withColor(com.ladleandlantern.core.util.Fancy.PEACH)))
                    .append(com.ladleandlantern.core.util.Fancy.plain("Food  ", com.ladleandlantern.core.util.Fancy.SOFT))
                    .append(com.ladleandlantern.core.util.Fancy.stars(foodStars, 5)).append(Component.literal(" "))
                    .append(com.ladleandlantern.core.util.Fancy.plain(food + "%" + (result.missing() > 0 ? ", " + result.missing() + " missing" : "")
                            + (result.extra() > 0 ? ", " + result.extra() + " extra" : ""), com.ladleandlantern.core.util.Fancy.CREAM));
            detail(scoreLine, "Speed", Math.round(result.speed() * 100) + "%", com.ladleandlantern.core.util.Fancy.CREAM, false);
            detail(scoreLine, "Served by", servedByName != null ? servedByName : credit.getName().getString(), com.ladleandlantern.core.util.Fancy.CREAM, false);
            ServerPlayer to = credit;
            level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + (good ? 18 : 0),
                    () -> { if (!to.hasDisconnected()) to.displayClientMessage(scoreLine, false); }));
            if (progress.rank() > before) {
                credit.sendSystemMessage(Component.literal("Rank up! You're now a " + Rank.title(progress.rank()) + ".").withStyle(ChatFormatting.GOLD));
                level.playSound(null, credit.blockPosition(), SoundEvents.PLAYER_LEVELUP, credit.getSoundSource(), 1f, 1f);
            }
        }
        store.markDirty();

        if (result.score() < Config.ANGRY_THRESHOLD.get()) {
            takeAway.forEach(Runnable::run);
            pendingPay = 0;
            setState(State.LEAVING_ANGRY);
            emote(Emote.ANGRY, 80);
            voice(SoundEvents.VILLAGER_NO, 1f);
            return;
        }
        pendingPay = Config.BASE_PAY.get();

        pendingTip = r.shiftTipCount < MAX_TIPS_A_DAY ? tipFor(result.score()) : 0;
        if (pendingTip > 0) r.shiftTipCount++;
        pendingRibbon = ribbon;
        leaveNote = !takeout && result.score() >= 92 && random.nextFloat() < 0.0005f;
        if (takeout) {
            pay(level, r);
            emote(favourite ? Emote.LOVE : Emote.SMILE, 60);
            speak(isKid() ? "Thank you!" : random.nextBoolean() ? "Thanks so much." : "Lovely, thank you.", 50);
            voice(SoundEventRegistry.APPROVE.get(), 0.7f);
            nodTicks = 30;
            setState(State.LEAVING);
        } else {
            eating = new ArrayList<>(ids);
            if (favourite && result.score() >= 80) emote(Emote.LOVE, 80);
            setState(State.SAVOURING);
        }
    }

    private static final int MAX_TIPS_A_DAY = 4;

    private static int tipFor(int score) {
        if (score < 85 || Math.random() > 0.3) return 0;
        return (score >= 97 ? 2 : 1) * Config.TIP.get();
    }

    public void walkOut(Restaurant r, boolean loseStar) {
        if (state.leaving() || state == State.EATING || state == State.SAVOURING) return;
        CustomerProfile profile = r.customers.get(profileId);
        if (profile != null && loseStar) {
            profile.adjustStars(-1);
            refreshName(profile);
        }
        if (loseStar) {
            r.shiftWalkouts++;
            emote(Emote.ANGRY, 80);
        }
        if (isPassenger()) stopRiding();
        store().markDirty();
        voice(SoundEvents.VILLAGER_NO, 0.9f);
        setState(loseStar ? State.LEAVING_ANGRY : State.LEAVING);
    }

    private void pay(ServerLevel level, Restaurant r) {
        if (pendingTip > 0) {
            BlockPos jar = r.fixtures(Fixture.TIP_JAR).stream().min(Comparator.comparingDouble(p -> p.distSqr(blockPosition()))).orElse(null);
            if (jar != null) {
                tipJar = jar;
            } else {
                pendingPay += pendingTip;
                pendingTip = 0;
            }
        }
        if (pendingPay > 0) {
            r.shiftEarnings += pendingPay;

            if (!takeout) level.playSound(null, blockPosition(), SoundEventRegistry.DINE_IN_PAY.get(), SoundSource.NEUTRAL, 0.7f, 1f);
            emote(Emote.COIN, 30);
        }
        if (staffServed && pendingPay > 0) {
            r.till += pendingPay;
            pendingPay = 0;
        }
        store().markDirty();
        Player target = payTo == null ? null : level.getPlayerByUUID(payTo);
        List<ItemStack> drops = new ArrayList<>();
        if (pendingPay > 0) drops.add(new ItemStack(Config.currency(), pendingPay));
        if (pendingRibbon) drops.add(new ItemStack(ItemRegistry.BLUE_RIBBON.get()));
        for (ItemStack stack : drops) {
            ItemEntity e = new ItemEntity(level, getX(), getEyeY() - 0.3, getZ(), stack);
            if (target != null) e.setDeltaMovement(target.position().subtract(position()).normalize().scale(0.25).add(0, 0.2, 0));
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        }
        if (pendingPay > 0) ExperienceOrb.award(level, position(), 2 + random.nextInt(4));
        pendingPay = 0;
        pendingRibbon = false;
    }

    @Nullable
    private static ServerPlayer nearestPlayer(ServerLevel level, Restaurant r) {
        AABB area = r.box().inflate(8);
        ServerPlayer best = level.players().stream().filter(p -> area.contains(p.position()))
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(r.centre()))).orElse(null);
        if (best == null && r.owner != null && level.getPlayerByUUID(r.owner) instanceof ServerPlayer owner) best = owner;
        return best;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof PlannerItem) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        ServerLevel level = sp.serverLevel();
        Restaurant r = restaurant().orElse(null);
        if (r == null) return InteractionResult.PASS;

        if (state == State.WANTS_MENU && held.is(ItemRegistry.MENU_CARD.get())) {
            if (!MenuCardItem.isWritten(held)) info(sp, r, "That card is blank. Right-click a cash register with it to write today's menu.");
            else if (handMenu(held) && !sp.getAbilities().instabuild) held.shrink(1);
        } else if ((state == State.WANTS_MENU || state == State.BROWSING) && profile() != null) {
            speak(state == State.WANTS_MENU ? CustomerLines.wantsMenu(profile(), type) : CustomerLines.browsing(profile(), type, random), 80);
            getLookControl().setLookAt(sp);
        } else if (state == State.READY && held.getItem() instanceof NotebookItem) {
            takeOrder(sp);
        } else if (state == State.READY) {
            if (profile() != null) speak(CustomerLines.ready(profile(), random), 60);
            sp.displayClientMessage(Component.literal("Hold your Order Notebook and right-click"), true);
        } else if (state == State.WAITING_FOOD && takeout && held.is(ItemRegistry.TO_GO_BAG.get())) {
            List<ItemStack> contents = ToGoBagItem.contents(held);
            if (contents.isEmpty()) {
                info(sp, r, "The bag is empty.");
            } else {
                held.shrink(1);
                deliver(contents, sp, null);
            }
        } else if (state == State.WAITING_FOOD && !takeout && FoodRules.isFood(held)) {
            serveFromHand(sp, level, r, held);
        } else if (state == State.WAITING_FOOD && !takeout && held.isEmpty() && sp.isShiftKeyDown()) {
            checkTable(level, r, sp);
        } else if (state == State.WAITING_FOOD || state == State.SAVOURING || state == State.EATING) {
            openReceipt(sp, level, r);
        } else {
            info(sp, r, null);
        }
        return InteractionResult.SUCCESS;
    }

    private void serveFromHand(ServerPlayer sp, ServerLevel level, Restaurant r, ItemStack held) {
        Seat seat = r.seatAt(seatPos).orElse(null);
        if (seat == null || seat.table.isEmpty()) {
            info(sp, r, "This seat has no table. Add one with the planner.");
            return;
        }
        int stillToCome = Math.max(1, missing(level, seat).size());
        if (!TableService.serveDish(sp, seat, r.seats, held, stillToCome)) {
            info(sp, r, "No room left on the table.");
            return;
        }
        if (!sp.getAbilities().instabuild) held.shrink(1);
        List<ResourceLocation> stillMissing = missing(level, seat);
        if (stillMissing.isEmpty()) {
            checkTable(level, r, null);
        } else {
            sp.displayClientMessage(Component.literal("Still waiting for: " + names(stillMissing)).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    private void openReceipt(ServerPlayer player, ServerLevel level, Restaurant r) {
        CompoundTag t = new CompoundTag();
        t.putString("restaurant", r.name);
        t.putString("label", label(r));
        t.putInt("ticket", order.ticket);
        t.putBoolean("takeout", takeout);
        CustomerProfile p = profile();
        t.putString("name", p != null ? p.name : getName().getString());
        t.putString("skin", skin());
        t.putInt("stars", p != null ? p.stars : 0);
        t.putInt("maxStars", CustomerProfile.MAX_STARS);
        t.putString("kind", isKid() ? "Little one" : type == CustomerType.CRITIC ? "Food critic" : type == CustomerType.CLOSER ? "Closer" : "Regular");
        t.putString("status", state == State.WAITING_FOOD ? (takeout ? "Waiting for their bag" : "Waiting for food") : state == State.SAVOURING ? "Admiring their plate" : "Eating");
        t.putFloat("patience", state == State.WAITING_FOOD ? patience() : -1);
        t.putString("clock", com.ladleandlantern.core.restaurant.Schedule.clock((int) (level.getDayTime() % 24000L)));
        List<ResourceLocation> stillMissing = new ArrayList<>();
        if (state == State.WAITING_FOOD && !takeout && seatPos != null) r.seatAt(seatPos).ifPresent(seat -> stillMissing.addAll(missing(level, seat)));
        else if (state == State.WAITING_FOOD) stillMissing.addAll(order.items());
        net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
        for (ResourceLocation id : order.items()) {
            CompoundTag line = new CompoundTag();
            line.putString("id", id.toString());
            line.putBoolean("served", !stillMissing.remove(id));
            items.add(line);
        }
        t.put("items", items);
        level.playSound(null, blockPosition(), SoundEventRegistry.MENU_CARD.get(), SoundSource.PLAYERS, 0.5f, 1.2f);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.ladleandlantern.core.network.OpenScreenPayload(com.ladleandlantern.core.network.OpenScreenPayload.RECEIPT, t));
    }

    private void info(ServerPlayer player, Restaurant r, @Nullable String extra) {
        String text = label(r) + ": " + (state == State.WANTS_MENU || state == State.BROWSING ? "hasn't ordered yet" : names(order.items()));
        if (state == State.WAITING_LINE) text = takeout ? "Waiting for room in the queue" : "Waiting for a table";
        if (state.waiting() && order.started()) {
            long left = (order.deadlineTicks - order.elapsed(level().getGameTime())) / 20;
            text += left >= 0 ? String.format(", %d:%02d left", left / 60, left % 60) : ", running late";
        }
        if (extra != null) text += ". " + extra;
        player.displayClientMessage(Component.literal(text), true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (profileId != null) tag.putUUID("Profile", profileId);
        tag.putInt("Type", type.ordinal());
        tag.putString("State", state.name());
        tag.putBoolean("Takeout", takeout);
        tag.putBoolean("Kid", isKid());
        if (seatPos != null) tag.putLong("Seat", seatPos.asLong());
        if (cashierPos != null) tag.putLong("Cashier", cashierPos.asLong());
        if (wanderTarget != null) tag.putLong("Wander", wanderTarget.asLong());
        tag.put("Order", order.save());
        tag.putLong("Arrival", arrivalTick);
        tag.putInt("EatTicks", eatTicks);
        tag.putInt("BrowseTicks", browseTicks);
        tag.putInt("Pay", pendingPay);
        tag.putInt("Tip", pendingTip);
        tag.putBoolean("CardOnTable", cardOnTable);
        if (partner != null) tag.putUUID("Partner", partner);
        tag.putInt("Roses", roses);
        tag.putBoolean("Ribbon", pendingRibbon);
        tag.putBoolean("Note", leaveNote);
        tag.putBoolean("ThroughDoor", throughDoor);
        tag.putBoolean("StaffServed", staffServed);
        if (servedByName != null) tag.putString("ServedBy", servedByName);
        tag.putBoolean("Music", music);
        tag.putBoolean("DetourDone", detourDone);
        tag.putBoolean("TurnedAway", turnedAway);
        if (tipJar != null) tag.putLong("TipJar", tipJar.asLong());
        tag.putString("Eating", String.join(",", eating.stream().map(ResourceLocation::toString).toList()));
        if (payTo != null) tag.putUUID("PayTo", payTo);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        try {
            readSave(tag);
        } catch (Exception e) {
            com.ladleandlantern.core.util.Guard.report(getClass().getSimpleName() + " load", e);
            broken = true;
        }
    }

    private void readSave(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Profile")) profileId = tag.getUUID("Profile");
        type = CustomerType.byId(tag.getInt("Type"));
        try {
            state = State.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            state = State.LEAVING;
        }
        takeout = tag.getBoolean("Takeout");
        entityData.set(KID, tag.getBoolean("Kid"));
        seatPos = tag.contains("Seat") ? BlockPos.of(tag.getLong("Seat")) : null;
        cashierPos = tag.contains("Cashier") ? BlockPos.of(tag.getLong("Cashier")) : null;
        wanderTarget = tag.contains("Wander") ? BlockPos.of(tag.getLong("Wander")) : null;
        order = Order.load(tag.getCompound("Order"));
        arrivalTick = tag.getLong("Arrival");
        eatTicks = tag.getInt("EatTicks");
        browseTicks = tag.getInt("BrowseTicks");
        pendingPay = tag.getInt("Pay");
        pendingTip = tag.getInt("Tip");
        cardOnTable = tag.getBoolean("CardOnTable");
        partner = tag.hasUUID("Partner") ? tag.getUUID("Partner") : null;
        roses = tag.getInt("Roses");
        pendingRibbon = tag.getBoolean("Ribbon");
        leaveNote = tag.getBoolean("Note");
        throughDoor = tag.getBoolean("ThroughDoor");
        staffServed = tag.getBoolean("StaffServed");
        servedByName = tag.contains("ServedBy") ? tag.getString("ServedBy") : null;
        music = tag.getBoolean("Music");
        detourDone = tag.getBoolean("DetourDone");
        turnedAway = tag.getBoolean("TurnedAway");
        tipJar = tag.contains("TipJar") ? BlockPos.of(tag.getLong("TipJar")) : null;
        eating = new ArrayList<>();
        for (String id : tag.getString("Eating").split(",")) {
            ResourceLocation rl = id.isEmpty() ? null : ResourceLocation.tryParse(id);
            if (rl != null) eating.add(rl);
        }
        payTo = tag.hasUUID("PayTo") ? tag.getUUID("PayTo") : null;
    }
}
