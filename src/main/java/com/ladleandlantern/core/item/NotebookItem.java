package com.ladleandlantern.core.item;

import com.ladleandlantern.core.network.ServerScreens;
import com.ladleandlantern.core.restaurant.RestaurantStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class NotebookItem extends Item {
    public NotebookItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            RestaurantStore.get(sp.server).near(level.dimension(), sp.blockPosition(), 16).ifPresentOrElse(
                    r -> ServerScreens.openNotebook(sp, r),
                    () -> sp.displayClientMessage(Component.literal("You're not in a restaurant."), true));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
