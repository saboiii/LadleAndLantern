package com.ladleandlantern.core.registry;

import com.ladleandlantern.core.entity.RestaurantCat;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.entity.CustomerEntity;
import com.ladleandlantern.core.entity.RestaurantNpc;
import com.ladleandlantern.core.entity.SeatEntity;
import com.ladleandlantern.core.entity.StaffEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class EntityTypeRegistry {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, LadleAndLantern.MODID);

    public static final Supplier<EntityType<CustomerEntity>> CUSTOMER = TYPES.register("customer",
            () -> EntityType.Builder.of(CustomerEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f)
                    .vehicleAttachment(new Vec3(0, 0.6, 0)).clientTrackingRange(10).build("customer"));
    public static final Supplier<EntityType<StaffEntity>> STAFF = TYPES.register("staff",
            () -> EntityType.Builder.of(StaffEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f)
                    .vehicleAttachment(new Vec3(0, 0.6, 0)).clientTrackingRange(10).build("staff"));
    public static final Supplier<EntityType<SeatEntity>> SEAT = TYPES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.01f, 0.01f)
                    .noSummon().clientTrackingRange(10).build("seat"));

    public static final Supplier<EntityType<com.ladleandlantern.core.entity.RestaurantCat>> CAT = TYPES.register("cat",
            () -> EntityType.Builder.<com.ladleandlantern.core.entity.RestaurantCat>of(com.ladleandlantern.core.entity.RestaurantCat::new, MobCategory.MISC)
                    .sized(0.6f, 0.7f).eyeHeight(0.35f).clientTrackingRange(8).build("cat"));

    private EntityTypeRegistry() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CUSTOMER.get(), RestaurantNpc.createAttributes().build());
        event.put(STAFF.get(), RestaurantNpc.createAttributes().build());
        event.put(CAT.get(), net.minecraft.world.entity.animal.Cat.createAttributes().build());
    }
}
