package com.ladleandlantern.core.registry;

import com.ladleandlantern.core.block.CatBedBlock;
import com.ladleandlantern.core.block.CorkboardBlock;
import com.ladleandlantern.core.block.GramophoneBlock;
import com.ladleandlantern.core.block.MenuStandBlock;
import com.ladleandlantern.core.block.StandBlock;
import com.ladleandlantern.core.block.TableCrumbsBlock;
import com.ladleandlantern.core.block.TipJarBlock;
import com.ladleandlantern.core.block.WaterStationBlock;

import com.ladleandlantern.LadleAndLantern;
import com.ladleandlantern.core.block.BagRackBlock;
import com.ladleandlantern.core.block.CashierBlock;
import com.ladleandlantern.core.block.RibbonBlock;
import com.ladleandlantern.core.block.SpecialsBoardBlock;
import com.ladleandlantern.core.block.DigitalClockBlock;
import com.ladleandlantern.core.block.ServedPlateBlock;
import com.ladleandlantern.core.block.TrayBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BlockRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LadleAndLantern.MODID);

    public static final DeferredBlock<CashierBlock> CASHIER = BLOCKS.register("cashier", () -> new CashierBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<TrayBlock> TRAY = BLOCKS.register("tray", () -> new TrayBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<com.ladleandlantern.core.block.TableCrumbsBlock> TABLE_CRUMBS = BLOCKS.register("table_crumbs", () -> new com.ladleandlantern.core.block.TableCrumbsBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).instabreak().sound(SoundType.WOOL).noCollission().noOcclusion()
                    .replaceable().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY).noLootTable()));
    public static final DeferredBlock<ServedPlateBlock> SERVED_PLATE = BLOCKS.register("served_plate", () -> new ServedPlateBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.2f).sound(SoundType.STONE).noOcclusion().noLootTable()));

    public static final DeferredBlock<DigitalClockBlock> DIGITAL_CLOCK = BLOCKS.register("digital_clock", () -> new DigitalClockBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 3)));

    public static final DeferredBlock<RibbonBlock> BLUE_RIBBON = BLOCKS.register("blue_ribbon", () -> new RibbonBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.5f).sound(SoundType.WOOL).noCollission().noOcclusion()));
    public static final DeferredBlock<SpecialsBoardBlock> SPECIALS_BOARD = BLOCKS.register("specials_board", () -> new SpecialsBoardBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<BagRackBlock> BAG_RACK = BLOCKS.register("bag_rack", () -> new BagRackBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion()));

    public static final DeferredBlock<com.ladleandlantern.core.block.TipJarBlock> TIP_JAR = BLOCKS.register("tip_jar", () -> new com.ladleandlantern.core.block.TipJarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.3f).sound(SoundType.GLASS).noOcclusion()));
    public static final DeferredBlock<com.ladleandlantern.core.block.StandBlock> UMBRELLA_STAND = BLOCKS.register("umbrella_stand", () -> com.ladleandlantern.core.block.StandBlock.umbrella(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.LANTERN).noOcclusion()));
    public static final DeferredBlock<com.ladleandlantern.core.block.StandBlock> COAT_RACK = BLOCKS.register("coat_rack", () -> com.ladleandlantern.core.block.StandBlock.coat(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<com.ladleandlantern.core.block.WaterStationBlock> WATER_STATION = BLOCKS.register("water_station", () -> new com.ladleandlantern.core.block.WaterStationBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8f).sound(SoundType.GLASS).noOcclusion()));
    public static final DeferredBlock<com.ladleandlantern.core.block.CorkboardBlock> CORKBOARD = BLOCKS.register("corkboard", () -> new com.ladleandlantern.core.block.CorkboardBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.8f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<com.ladleandlantern.core.block.CatBedBlock> CAT_BED = BLOCKS.register("cat_bed", () -> new com.ladleandlantern.core.block.CatBedBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5f).sound(SoundType.WOOL).noOcclusion()));
    public static final DeferredBlock<com.ladleandlantern.core.block.GramophoneBlock> GRAMOPHONE = BLOCKS.register("gramophone", () -> new com.ladleandlantern.core.block.GramophoneBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<com.ladleandlantern.core.block.MenuStandBlock> MENU_STAND = BLOCKS.register("menu_stand", () -> new com.ladleandlantern.core.block.MenuStandBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5f).sound(SoundType.WOOD).noOcclusion()));

    private BlockRegistry() {}
}
