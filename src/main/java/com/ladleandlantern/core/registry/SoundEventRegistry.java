package com.ladleandlantern.core.registry;

import com.ladleandlantern.LadleAndLantern;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class SoundEventRegistry {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, LadleAndLantern.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DOORBELL = register("doorbell");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUTLERY = register("cutlery");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHATTER = register("chatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUGH = register("laugh");
    public static final DeferredHolder<SoundEvent, SoundEvent> MMM = register("mmm");
    public static final DeferredHolder<SoundEvent, SoundEvent> APPROVE = register("approve");
    public static final DeferredHolder<SoundEvent, SoundEvent> BAG = register("bag");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHA_CHING = register("cha_ching");
    public static final DeferredHolder<SoundEvent, SoundEvent> DINE_IN_PAY = register("dine_in_pay");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLACE_PLATE = register("place_plate");
    public static final DeferredHolder<SoundEvent, SoundEvent> MENU_CARD = register("menu_card");
    public static final DeferredHolder<SoundEvent, SoundEvent> SERVICE_BELL = register("service_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUCCESSFUL_SERVE = register("successful_serve");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIP_JAR = register("tip_jar");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATER_POUR = register("water_pour");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLEAR_DISHES = register("clear_dishes");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUGHTER = register("laughter");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENCE_CAFE = register("ambience_cafe");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENCE_ROOM = register("ambience_room");
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> RECORDS = List.of(register("record_lantern_glow"), register("record_morning_bread"), register("record_snowy_windowsill"));

    private SoundEventRegistry() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(LadleAndLantern.id(name)));
    }
}
