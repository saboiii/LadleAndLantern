package com.ladleandlantern.core.registry;

import com.ladleandlantern.core.block.CorkboardBlock;
import com.ladleandlantern.core.block.GramophoneBlock;
import com.ladleandlantern.core.block.TipJarBlock;
import com.ladleandlantern.core.block.entity.MenuStandBlockEntity;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.block.entity.BagRackBlockEntity;
import com.ladleandlantern.core.block.entity.DigitalClockBlockEntity;
import com.ladleandlantern.core.block.RibbonBlock;
import com.ladleandlantern.core.block.SpecialsBoardBlock;
import com.ladleandlantern.core.block.entity.ServedPlateBlockEntity;
import com.ladleandlantern.core.block.entity.TrayBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class BlockEntityTypeRegistry {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LadleAndLantern.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<TrayBlockEntity>> TRAY = TYPES.register("tray",
            () -> BlockEntityType.Builder.of(TrayBlockEntity::new, BlockRegistry.TRAY.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ServedPlateBlockEntity>> SERVED_PLATE = TYPES.register("served_plate",
            () -> BlockEntityType.Builder.of(ServedPlateBlockEntity::new, BlockRegistry.SERVED_PLATE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<DigitalClockBlockEntity>> DIGITAL_CLOCK = TYPES.register("digital_clock",
            () -> BlockEntityType.Builder.of(DigitalClockBlockEntity::new, BlockRegistry.DIGITAL_CLOCK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<RibbonBlock.RibbonBlockEntity>> RIBBON = TYPES.register("blue_ribbon",
            () -> BlockEntityType.Builder.of(RibbonBlock.RibbonBlockEntity::new, BlockRegistry.BLUE_RIBBON.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SpecialsBoardBlock.BoardEntity>> SPECIALS_BOARD = TYPES.register("specials_board",
            () -> BlockEntityType.Builder.of(SpecialsBoardBlock.BoardEntity::new, BlockRegistry.SPECIALS_BOARD.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<BagRackBlockEntity>> BAG_RACK = TYPES.register("bag_rack",
            () -> BlockEntityType.Builder.of(BagRackBlockEntity::new, BlockRegistry.BAG_RACK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.ladleandlantern.core.block.TipJarBlock.JarEntity>> TIP_JAR = TYPES.register("tip_jar",
            () -> BlockEntityType.Builder.of(com.ladleandlantern.core.block.TipJarBlock.JarEntity::new, BlockRegistry.TIP_JAR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.ladleandlantern.core.block.entity.MenuStandBlockEntity>> MENU_STAND = TYPES.register("menu_stand",
            () -> BlockEntityType.Builder.of(com.ladleandlantern.core.block.entity.MenuStandBlockEntity::new, BlockRegistry.MENU_STAND.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.ladleandlantern.core.block.GramophoneBlock.GramophoneEntity>> GRAMOPHONE = TYPES.register("gramophone",
            () -> BlockEntityType.Builder.of(com.ladleandlantern.core.block.GramophoneBlock.GramophoneEntity::new, BlockRegistry.GRAMOPHONE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.ladleandlantern.core.block.CorkboardBlock.Board>> CORKBOARD = TYPES.register("corkboard",
            () -> BlockEntityType.Builder.of(com.ladleandlantern.core.block.CorkboardBlock.Board::new, BlockRegistry.CORKBOARD.get()).build(null));

    private BlockEntityTypeRegistry() {}
}
