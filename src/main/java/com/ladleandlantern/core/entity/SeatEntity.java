package com.ladleandlantern.core.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !isVehicle() && tickCount > 20) discard();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return Vec3.ZERO;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (passenger instanceof LivingEntity living) {
            living.setYRot(getYRot());
            living.setYBodyRot(getYRot());
            float rel = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(living.getYHeadRot() - getYRot()), -75f, 75f);
            living.setYHeadRot(getYRot() + rel);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos seat = blockPosition();
        Direction back = Direction.fromYRot(getYRot()).getOpposite();
        Direction[] order = {back, back.getClockWise(), back.getCounterClockWise(), back.getOpposite()};
        for (int dy : new int[]{0, 1, -1}) {
            for (Direction d : order) {
                Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), seat.relative(d).above(dy), true);
                if (spot != null) return spot;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
