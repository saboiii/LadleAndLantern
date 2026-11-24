package com.ladleandlantern.core.block;

import com.ladleandlantern.core.registry.SoundEventRegistry;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import com.ladleandlantern.core.registry.ItemRegistry;
import com.ladleandlantern.core.restaurant.Fixture;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class TipJarBlock extends FixtureBlock implements EntityBlock {
    public enum Money implements StringRepresentable {
        CASH, DIAMOND, COIN;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 6);
    public static final EnumProperty<Money> MONEY = EnumProperty.create("money", Money.class);
    public static final MapCodec<TipJarBlock> CODEC = simpleCodec(TipJarBlock::new);

    private static final int[] STEPS = {2, 5, 9, 14, 20, 28};

    public TipJarBlock(Properties props) {
        super(props, Fixture.TIP_JAR, box(5, 0, 5, 11, 9, 11));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FILL, 0).setValue(MONEY, Money.DIAMOND).setValue(Finish.PROPERTY, Finish.COTTAGE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILL, MONEY, Finish.PROPERTY);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JarEntity(pos, state);
    }

    public static Money moneyFor(net.minecraft.world.item.Item currency) {
        if (currency == ItemRegistry.DOLLAR_BILL.get()) return Money.CASH;
        if (currency == Items.DIAMOND) return Money.DIAMOND;
        return Money.COIN;
    }

    public static boolean addTips(ServerLevel level, BlockPos pos, int amount) {
        if (!(level.getBlockEntity(pos) instanceof JarEntity jar)) return false;
        jar.tips += amount;
        jar.setChanged();
        refresh(level, pos, jar);
        level.playSound(null, pos, com.ladleandlantern.core.registry.SoundEventRegistry.TIP_JAR.get(), SoundSource.BLOCKS, 0.7f, 1f);
        return true;
    }

    private static void refresh(Level level, BlockPos pos, JarEntity jar) {
        int fill = 0;
        for (int step : STEPS) if (jar.tips >= step) fill++;
        BlockState state = level.getBlockState(pos);
        BlockState next = state.setValue(FILL, fill).setValue(MONEY, moneyFor(Config.currency()));
        if (next != state) level.setBlock(pos, next, Block.UPDATE_ALL);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        Finish finish = Finish.fromDye(stack);
        if (finish == null) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return MenuStandBlock.refinish(stack, state, level, pos, player, finish);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof JarEntity jar) {
            if (jar.tips <= 0) {
                player.displayClientMessage(Component.literal("The tip jar is empty. Great service fills it up."), true);
            } else {
                int left = jar.tips;
                int max = Config.currency().getDefaultMaxStackSize();
                while (left > 0) {
                    ItemStack s = new ItemStack(Config.currency(), Math.min(max, left));
                    left -= s.getCount();
                    if (!player.getInventory().add(s)) player.drop(s, false);
                }
                player.displayClientMessage(Component.literal("You empty " + jar.tips + " in tips from the jar."), true);
                level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.6f);
                jar.tips = 0;
                jar.setChanged();
                refresh(level, pos, jar);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof JarEntity jar && jar.tips > 0) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(Config.currency(), Math.min(jar.tips, Config.currency().getDefaultMaxStackSize())));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    public static class JarEntity extends BlockEntity {
        int tips;

        public JarEntity(BlockPos pos, BlockState state) {
            super(BlockEntityTypeRegistry.TIP_JAR.get(), pos, state);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("tips", tips);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            tips = tag.getInt("tips");
        }
    }
}
