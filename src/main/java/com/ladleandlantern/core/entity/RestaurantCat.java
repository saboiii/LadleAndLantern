package com.ladleandlantern.core.entity;

import com.ladleandlantern.core.food.TableService;

import com.ladleandlantern.core.registry.EntityTypeRegistry;
import com.ladleandlantern.core.restaurant.Fixture;
import com.ladleandlantern.core.restaurant.Restaurant;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import com.ladleandlantern.core.shift.ShiftManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public class RestaurantCat extends Cat {
    private static final float VISIT_CHANCE_PER_SECOND = 1f / 240f;

    private UUID restaurantId;
    private BlockPos bed;
    private boolean leaving;
    private int walkTicks;
    private boolean settled;

    private double seatY = Double.NaN;
    private int nextChange;
    private Player lookAtFor;

    public RestaurantCat(EntityType<? extends Cat> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public static void maybeVisit(ServerLevel level, Restaurant r) {
        if (r.catToday || level.random.nextFloat() > VISIT_CHANCE_PER_SECOND || r.entrance == null) return;
        if (!level.getEntitiesOfClass(RestaurantCat.class, r.searchBox(), c -> r.id.equals(c.restaurantId)).isEmpty()) return;
        BlockPos bed = spot(level, r);
        BlockPos from = ShiftManager.outsidePoint(level, r, level.random, 8, 12);
        if (from == null) from = r.entrance;
        if (bed == null) return;
        r.catToday = true;
        RestaurantCat cat = new RestaurantCat(EntityTypeRegistry.CAT.get(), level);
        cat.restaurantId = r.id;
        cat.bed = bed;
        cat.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(from);
        cat.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null);
        level.addFreshEntity(cat);
    }

    private static BlockPos spot(ServerLevel level, Restaurant r) {
        List<BlockPos> beds = r.fixtures(Fixture.CAT_BED);
        if (!beds.isEmpty()) return beds.get(level.random.nextInt(beds.size())).immutable();
        List<BlockPos> free = ShiftManager.freeSeats(level, r, CustomerEntity.inRestaurant(level, r));
        return free.isEmpty() ? null : free.get(level.random.nextInt(free.size()));
    }

    public static void sendHome(ServerLevel level, Restaurant r) {
        for (RestaurantCat cat : level.getEntitiesOfClass(RestaurantCat.class, r.searchBox(), c -> r.id.equals(c.restaurantId))) cat.leave();
    }

    private void leave() {
        if (leaving) return;
        leaving = true;
        walkTicks = 0;
        settled = false;
        setLying(false);
        setInSittingPose(false);
        setNoGravity(false);
    }

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        Restaurant r = RestaurantStore.get(level.getServer()).byId(restaurantId).orElse(null);
        if (r == null || bed == null) {
            discard();
            return;
        }

        if (!r.shiftOpen) leave();
        walkTicks++;
        if (leaving) {
            if (walkTicks > 20 * 20 || r.entrance == null || distanceToSqr(r.entrance.getCenter()) < 4) {
                level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 5, 0.2, 0.2, 0.2, 0.01);
                discard();
            } else if (tickCount % 20 == 0 || getNavigation().isDone()) {
                getNavigation().moveTo(getNavigation().createPath(java.util.Set.of(r.entrance), 1), 0.8);
            }
            return;
        }
        if (settled) {
            tickSettled(level);
            return;
        }
        double dx = getX() - (bed.getX() + 0.5), dz = getZ() - (bed.getZ() + 0.5);
        if (dx * dx + dz * dz < 2.5 || walkTicks > 60 * 20) {
            getNavigation().stop();
            settled = true;
            setNoGravity(true);
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            seatY = com.ladleandlantern.core.food.TableService.sitHeight(level, bed);
            moveTo(bed.getX() + 0.5, seatY, bed.getZ() + 0.5, getYRot(), 0);
            setLying(true);
            nextChange = walkTicks + 200 + random.nextInt(400);
        } else if (tickCount % 20 == 0 || getNavigation().isDone()) {
            getNavigation().moveTo(bed.getX() + 0.5, bed.getY() + 1, bed.getZ() + 0.5, 0.8);
        }
    }

    private void tickSettled(ServerLevel level) {
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (Double.isNaN(seatY)) seatY = com.ladleandlantern.core.food.TableService.sitHeight(level, bed);
        double y = seatY;
        if (Math.abs(getY() - y) > 0.05 || horizontalDistanceToBed() > 0.1) setPos(bed.getX() + 0.5, y, bed.getZ() + 0.5);
        if (walkTicks >= nextChange) {
            boolean napping = isLying();
            setLying(!napping);
            setInSittingPose(napping);
            if (!napping) playSound(SoundEvents.CAT_AMBIENT, 0.4f, 1f);
            float yaw = getYRot() + (random.nextFloat() - 0.5f) * 120f;
            setYRot(yaw);
            setYBodyRot(yaw);
            nextChange = walkTicks + (napping ? 200 + random.nextInt(300) : 400 + random.nextInt(600));
        }
        if (!isLying() && tickCount % 40 == 0) {
            Player near = level.getNearestPlayer(this, 8);
            if (near != null && random.nextBoolean()) lookAtFor = near;
            else lookAtFor = null;
        }
        if (!isLying() && lookAtFor != null && lookAtFor.isAlive()) getLookControl().setLookAt(lookAtFor, 20, 20);
    }

    private double horizontalDistanceToBed() {
        double dx = getX() - (bed.getX() + 0.5), dz = getZ() - (bed.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.6, getZ(), 3, 0.2, 0.2, 0.2, 0);
            playSound(SoundEvents.CAT_PURREOW, 0.8f, 1f);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    @javax.annotation.Nullable
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        if (settled && isLying()) return SoundEvents.CAT_PURR;
        if (settled) return random.nextInt(4) == 0 ? SoundEvents.CAT_PURREOW : SoundEvents.CAT_AMBIENT;
        return SoundEvents.CAT_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (restaurantId != null) tag.putUUID("Restaurant", restaurantId);
        if (bed != null) tag.putLong("Bed", bed.asLong());
        tag.putBoolean("Leaving", leaving);
        tag.putBoolean("Settled", settled);
        if (!Double.isNaN(seatY)) tag.putDouble("SeatY", seatY);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Restaurant")) restaurantId = tag.getUUID("Restaurant");
        bed = tag.contains("Bed") ? BlockPos.of(tag.getLong("Bed")) : null;
        leaving = tag.getBoolean("Leaving");
        settled = tag.getBoolean("Settled");
        seatY = tag.contains("SeatY") ? tag.getDouble("SeatY") : Double.NaN;
        if (settled && !leaving) setNoGravity(true);
    }
}
