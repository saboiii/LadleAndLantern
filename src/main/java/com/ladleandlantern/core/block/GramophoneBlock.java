package com.ladleandlantern.core.block;

import com.ladleandlantern.core.block.entity.SyncedBlockEntity;
import com.ladleandlantern.core.util.ClientAccess;

import com.ladleandlantern.core.item.RecordItem;
import com.ladleandlantern.core.registry.BlockEntityTypeRegistry;
import com.ladleandlantern.core.restaurant.Fixture;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class GramophoneBlock extends FixtureBlock implements EntityBlock {
    public static final MapCodec<GramophoneBlock> CODEC = simpleCodec(GramophoneBlock::new);

    public GramophoneBlock(Properties props) {
        super(props, Fixture.GRAMOPHONE, box(2, 0, 2, 14, 16, 14));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GramophoneEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != BlockEntityTypeRegistry.GRAMOPHONE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<GramophoneEntity>) (l, p, s, be) -> be.clientTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof RecordItem) || !(level.getBlockEntity(pos) instanceof GramophoneEntity g) || g.playing()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            g.setDisc(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GramophoneEntity g && g.playing()) {
            ItemStack disc = g.disc;
            g.setDisc(ItemStack.EMPTY);
            if (!player.getInventory().add(disc)) player.drop(disc, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GramophoneEntity g && g.playing()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, g.disc);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    public static class GramophoneEntity extends SyncedBlockEntity {
        private ItemStack disc = ItemStack.EMPTY;

        public Object sound;

        public GramophoneEntity(BlockPos pos, BlockState state) {
            super(BlockEntityTypeRegistry.GRAMOPHONE.get(), pos, state);
        }

        public boolean playing() {
            return !disc.isEmpty();
        }

        public ItemStack disc() {
            return disc;
        }

        void setDisc(ItemStack stack) {
            disc = stack;
            sync();
        }

        void clientTick() {
            if (level == null) return;
            if (playing() && level.getGameTime() % 30 == 0) {
                level.addParticle(ParticleTypes.NOTE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                        level.random.nextInt(24) / 24.0, 0, 0);
            }
            com.ladleandlantern.core.util.ClientAccess.get().tickGramophone(this);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);

            tag.putBoolean("playing", playing());
            if (!disc.isEmpty()) tag.put("disc", disc.save(registries));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            disc = tag.contains("disc") ? ItemStack.parse(registries, tag.getCompound("disc")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        }
    }
}
