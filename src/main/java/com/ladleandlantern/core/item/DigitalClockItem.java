package com.ladleandlantern.core.item;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.restaurant.GameCalendar;
import com.ladleandlantern.core.restaurant.RestaurantStore;

import com.ladleandlantern.core.restaurant.Schedule;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class DigitalClockItem extends BlockItem {
    public DigitalClockItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && selected && entity instanceof Player player && level.getGameTime() % 20 == 0) {
            var store = com.ladleandlantern.core.restaurant.RestaurantStore.get(((net.minecraft.server.level.ServerLevel) level).getServer());
            long day = store.day();
            String season = com.ladleandlantern.core.restaurant.GameCalendar.season(day, com.ladleandlantern.core.config.Config.SEASON_LENGTH.get()).label();
            player.displayClientMessage(Component.literal(Schedule.DAY_NAMES[Schedule.weekday(day)] + " " + Schedule.clock((int) (level.getDayTime() % 24000L))
                    + ", " + season + " day " + com.ladleandlantern.core.restaurant.GameCalendar.dayOfSeason(day, com.ladleandlantern.core.config.Config.SEASON_LENGTH.get())), true);
        }
    }
}
