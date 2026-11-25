package com.ladleandlantern.core.entity;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.food.TableService;
import com.ladleandlantern.core.registry.EntityTypeRegistry;
import com.ladleandlantern.core.restaurant.Seat;

import com.ladleandlantern.core.entity.ai.RestaurantNavigation;
import com.ladleandlantern.core.restaurant.Restaurant;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public abstract class RestaurantNpc extends PathfinderMob {
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(RestaurantNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(RestaurantNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SPEECH = SynchedEntityData.defineId(RestaurantNpc.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Integer> RATING = SynchedEntityData.defineId(RestaurantNpc.class, EntityDataSerializers.INT);
    public static final int STARS = 1, HEARTS = 2;

    protected UUID restaurantId;
    private long emoteUntil;
    private long speechUntil;
    private Entity glanceAt;
    private Vec3 glanceSpot;
    private long glanceUntil;

    private final Map<BlockPos, Long> openedGates = new HashMap<>();
    private Vec3 lastCheckPos;
    private int stuckChecks;
    private int outsideChecks;

    protected RestaurantNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setCustomNameVisible(true);
        jumpControl = new CarefulJump(this);
    }

    private static final class CarefulJump extends net.minecraft.world.entity.ai.control.JumpControl {
        private final RestaurantNpc npc;

        CarefulJump(RestaurantNpc npc) {
            super(npc);
            this.npc = npc;
        }

        @Override
        public void jump() {
            if (npc.isInWater() || npc.isInLava()) {
                super.jump();
                return;
            }
            Path path = npc.getNavigation().getPath();
            if (path == null || path.isDone()) return;
            Node next = path.getNextNode();
            if (next.y > npc.getBlockY()) super.jump();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new RestaurantNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, "default:0");
        builder.define(EMOTE, 0);
        builder.define(SPEECH, "");
        builder.define(RATING, 0);
    }

    public void setRating(int kind, int filled, int outOf) {
        int value = kind * 100 + Math.max(0, Math.min(9, filled)) * 10 + Math.max(0, Math.min(9, outOf));
        if (entityData.get(RATING) != value) entityData.set(RATING, value);
    }

    public int ratingKind() { return entityData.get(RATING) / 100; }
    public int ratingFilled() { return entityData.get(RATING) / 10 % 10; }
    public int ratingOutOf() { return entityData.get(RATING) % 10; }

    public String skin() {
        return entityData.get(SKIN);
    }

    public void setSkin(String skin) {
        entityData.set(SKIN, skin);
    }

    public Emote emote() {
        return Emote.byId(entityData.get(EMOTE));
    }

    public void emote(Emote emote, int ticks) {
        entityData.set(EMOTE, emote.ordinal());
        emoteUntil = level().getGameTime() + ticks;
    }

    public String speech() {
        return entityData.get(SPEECH);
    }

    public void speak(String text, int ticks) {
        entityData.set(SPEECH, text.length() > 120 ? text.substring(0, 118) + ".." : text);
        speechUntil = level().getGameTime() + ticks;
    }

    public float voicePitch() {
        return 0.85f + (Math.floorMod(skin().hashCode() * 31 + getUUID().hashCode(), 40)) / 100f;
    }

    public void voice(net.minecraft.sounds.SoundEvent sound, float volume) {
        playSound(sound, volume, voicePitch() * (0.95f + random.nextFloat() * 0.1f));
    }

    public UUID restaurantId() {
        return restaurantId;
    }

    public Optional<Restaurant> restaurant() {
        if (!(level() instanceof ServerLevel server)) return Optional.empty();
        return RestaurantStore.get(server.getServer()).byId(restaurantId);
    }

    protected RestaurantStore store() {
        return RestaurantStore.get(((ServerLevel) level()).getServer());
    }

    public LongSet noWalkBlocks() {
        return restaurant().map(Restaurant::noWalk).orElse(null);
    }

    @javax.annotation.Nullable
    public net.minecraft.world.phys.AABB pathBounds() {
        return null;
    }

    public boolean solidToOthers() {
        return false;
    }

    @javax.annotation.Nullable
    protected BlockPos returnPoint() {
        return null;
    }

    protected boolean canSit() {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        long now = level().getGameTime();
        if (emoteUntil != 0 && now >= emoteUntil) {
            entityData.set(EMOTE, 0);
            emoteUntil = 0;
        }
        if (speechUntil != 0 && now >= speechUntil) {
            entityData.set(SPEECH, "");
            speechUntil = 0;
        }
        if (tickCount % 4 == 0) tickGates(now);
        if (tickCount % 5 == 0) tickOffFurniture();
        if (tickCount % 20 == 0) {
            tickUnstick();
            tickBounds();
        }
    }

    private void tickBounds() {
        net.minecraft.world.phys.AABB bounds = pathBounds();
        if (bounds == null || isPassenger() || isSleeping() || bounds.inflate(1.5).contains(position())) {
            outsideChecks = 0;
            return;
        }
        if (++outsideChecks < 3) return;
        outsideChecks = 0;
        BlockPos back = returnPoint();
        if (back == null || !(level() instanceof ServerLevel server)) return;
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        getNavigation().stop();
        teleportTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
        setDeltaMovement(Vec3.ZERO);
    }

    private void tickOffFurniture() {
        if (isPassenger() || isSleeping() || !onGround()) return;
        if (this instanceof StaffEntity staff && staff.headingToBed()) return;
        it.unimi.dsi.fastutil.longs.LongSet blocked = noWalkBlocks();
        BlockPos feet = blockPosition(), under = BlockPos.containing(getX(), getY() - 0.2, getZ());
        if (!onFurniture(blocked, feet) && !onFurniture(blocked, under)) return;
        BlockPos best = BlockPos.betweenClosedStream(feet.offset(-2, -1, -2), feet.offset(2, 0, 2))
                .filter(p -> !onFurniture(blocked, p) && !onFurniture(blocked, p.below()) && standable(level(), p))
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(p -> p.distSqr(feet))).orElse(null);
        if (best == null) return;
        BlockPos target = getNavigation().getTargetPos();
        double speed = lastSpeed();
        getNavigation().stop();
        moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, getYRot(), getXRot());
        setDeltaMovement(Vec3.ZERO);
        repath(target, speed);
    }

    protected static boolean standable(net.minecraft.world.level.BlockGetter level, BlockPos p) {
        return level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
    }

    private double lastSpeed() {
        return getNavigation() instanceof RestaurantNavigation nav ? nav.lastSpeed() : 0.6;
    }

    private void repath(@javax.annotation.Nullable BlockPos target, double speed) {
        if (target == null) return;
        Path fresh = safePath(target);
        if (fresh != null) getNavigation().moveTo(fresh, speed);
    }

    private boolean onFurniture(@javax.annotation.Nullable it.unimi.dsi.fastutil.longs.LongSet blocked, BlockPos p) {
        if (blocked != null && blocked.contains(p.asLong())) return true;
        net.minecraft.world.level.block.state.BlockState state = level().getBlockState(p);
        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) return true;
        return !state.isAir() && com.ladleandlantern.LadleAndLantern.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())
                && !state.getCollisionShape(level(), p).isEmpty();
    }

    private void tickUnstick() {
        Vec3 here = position();
        boolean trying = !getNavigation().isDone() && !isPassenger();
        if (trying && lastCheckPos != null && here.distanceToSqr(lastCheckPos) < 0.04) {
            if (++stuckChecks >= 2) {
                stuckChecks = 0;
                BlockPos target = getNavigation().getTargetPos();
                double speed = lastSpeed();
                getNavigation().stop();
                repath(target, speed);
            }
        } else {
            stuckChecks = 0;
        }
        lastCheckPos = here;
    }

    protected void tickGlance() {
        long now = level().getGameTime();
        if (now >= glanceUntil) {
            glanceAt = null;
            glanceSpot = null;
            if (random.nextFloat() < 0.6f) {
                glanceAt = someoneNearby(6, e -> e instanceof Player || e instanceof RestaurantNpc);
                glanceUntil = now + 40 + random.nextInt(60);
            }
            if (glanceAt == null) {
                float yaw = yBodyRot + (random.nextFloat() - 0.5f) * 120f;
                glanceSpot = getEyePosition().add(Vec3.directionFromRotation(random.nextFloat() * 20f - 10f, yaw).scale(4));
                glanceUntil = now + 20 + random.nextInt(40);
            }
        }
        if (glanceAt != null && glanceAt.isAlive() && distanceToSqr(glanceAt) < 100 && Math.abs(glanceAt.getY() - getY()) < 2.5) getLookControl().setLookAt(glanceAt, 20, 20);
        else if (glanceSpot != null) getLookControl().setLookAt(glanceSpot.x, glanceSpot.y, glanceSpot.z, 15, 15);
    }

    @javax.annotation.Nullable
    protected LivingEntity someoneNearby(double range, java.util.function.Predicate<LivingEntity> who) {
        List<LivingEntity> around = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range, 2.5, range),
                e -> e != this && who.test(e) && Math.abs(e.getY() - getY()) < 2.5 && hasLineOfSight(e));
        return around.isEmpty() ? null : around.get(random.nextInt(around.size()));
    }

    private void tickGates(long now) {
        Path path = getNavigation().getPath();
        if (path != null && !path.isDone()) {
            for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2); i++) {
                Node node = path.getNode(i);
                BlockPos pos = new BlockPos(node.x, node.y, node.z);
                if (pos.distToCenterSqr(position()) > 4) continue;
                BlockState state = level().getBlockState(pos);
                if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                    level().setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 10);
                    level().playSound(null, pos, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 0.8f, 1f);
                    openedGates.put(pos, now);
                }
            }
        }
        openedGates.entrySet().removeIf(e -> {
            if (now - e.getValue() < 30) return false;
            BlockPos pos = e.getKey();
            boolean someoneThere = !level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(0.5)).isEmpty();
            if (someoneThere) return false;
            BlockState state = level().getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) {
                level().setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), 10);
                level().playSound(null, pos, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 0.8f, 1f);
            }
            return true;
        });
    }

    protected void sitOn(ServerLevel level, com.ladleandlantern.core.restaurant.Seat seat) {
        if (isPassenger()) return;
        SeatEntity chair = new SeatEntity(com.ladleandlantern.core.registry.EntityTypeRegistry.SEAT.get(), level);
        float yaw = 0;
        if (!seat.table.isEmpty()) {
            BlockPos t = seat.table.stream().min(java.util.Comparator.comparingDouble(p -> p.distSqr(seat.pos))).orElseThrow();
            double x = t.getX() - seat.pos.getX(), z = t.getZ() - seat.pos.getZ();
            yaw = (float) (Math.toDegrees(Math.atan2(z, x)) - 90.0);
        }
        chair.moveTo(seat.pos.getX() + 0.5, com.ladleandlantern.core.food.TableService.sitHeight(level, seat.pos), seat.pos.getZ() + 0.5, yaw, 0);
        level.addFreshEntity(chair);
        startRiding(chair, true);
        getNavigation().stop();
    }

    @javax.annotation.Nullable
    protected Path safePath(BlockPos target) {
        Path path = pathTo(target, 1);
        if (path == null || !path.canReach()) {
            Path looser = pathTo(target, 2);
            if (looser != null && (path == null || looser.canReach())) path = looser;
        }
        return belowCeiling(path, target, 2);
    }

    @javax.annotation.Nullable
    protected Path safePath(BlockPos target, int accuracy, int headroom) {
        return belowCeiling(pathTo(target, accuracy), target, headroom);
    }

    @javax.annotation.Nullable
    private Path belowCeiling(@javax.annotation.Nullable Path path, BlockPos target, int headroom) {
        if (path == null) return null;
        int ceiling = Math.max(blockPosition().getY(), target.getY()) + headroom;
        for (int i = 0; i < path.getNodeCount(); i++) if (path.getNode(i).y > ceiling) return null;
        return path;
    }

    @javax.annotation.Nullable
    protected Path pathTo(BlockPos target, int accuracy) {
        return getNavigation().createPath(java.util.Set.of(target.immutable()), accuracy);
    }

    protected void walkTo(BlockPos target, double speed) {
        Path path = safePath(target);
        if (path != null) getNavigation().moveTo(path, speed);
    }

    protected boolean canReach(BlockPos target) {
        Path path = safePath(target);
        return path != null && path.canReach();
    }

    protected boolean near(BlockPos target, double horizontal) {
        double dx = getX() - (target.getX() + 0.5), dz = getZ() - (target.getZ() + 0.5);
        return dx * dx + dz * dz <= horizontal * horizontal && Math.abs(getY() - target.getY()) < 2.5;
    }

    protected double horizontalDistance(BlockPos target) {
        double dx = getX() - (target.getX() + 0.5), dz = getZ() - (target.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    protected static void announce(ServerLevel level, Restaurant r, net.minecraft.network.chat.Component line) {
        net.minecraft.world.phys.AABB area = r.box().inflate(16);
        for (net.minecraft.server.level.ServerPlayer p : level.players()) if (area.contains(p.position())) p.sendSystemMessage(line);
    }

    protected static void announce(ServerLevel level, Restaurant r, String name, net.minecraft.ChatFormatting colour, String text) {
        announce(level, r, net.minecraft.network.chat.Component.literal("[" + name + "] ").withStyle(colour)
                .append(net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.WHITE)));
    }

    protected void slipAway() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        }
        discard();
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return vehicle instanceof SeatEntity && (level().isClientSide || canSit()) && super.startRiding(vehicle, force);
    }

    @Override
    protected void doPush(Entity other) {
        if (!(other instanceof RestaurantNpc) || this instanceof StaffEntity || other instanceof StaffEntity) super.doPush(other);
    }

    @Override
    public int getMaxFallDistance() {
        return 1;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        return false;
    }

    @Override
    public void thunderHit(ServerLevel level, net.minecraft.world.entity.LightningBolt lightning) {
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (restaurantId != null) tag.putUUID("Restaurant", restaurantId);
        tag.putString("Skin", skin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Restaurant")) restaurantId = tag.getUUID("Restaurant");
        if (tag.contains("Skin")) setSkin(tag.getString("Skin"));
    }
}
