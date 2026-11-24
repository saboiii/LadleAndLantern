package com.ladleandlantern.core.world;

import com.ladleandlantern.core.config.Config;
import com.ladleandlantern.core.restaurant.GameCalendar;
import com.ladleandlantern.core.restaurant.Restaurant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

public final class Weather {
    private Weather() {}

    private static BlockPos door(Restaurant r) {
        return r.entrance != null ? r.entrance : r.min;
    }

    public static boolean wet(ServerLevel level, Restaurant r) {
        BlockPos at = door(r);
        return level.isRaining() && level.getBiome(at).value().getPrecipitationAt(at) == Biome.Precipitation.RAIN;
    }

    public static boolean cold(ServerLevel level, Restaurant r, long day) {
        GameCalendar.Season s = GameCalendar.season(day, Config.SEASON_LENGTH.get());
        BlockPos at = door(r);
        boolean snowing = level.isRaining() && level.getBiome(at).value().getPrecipitationAt(at) == Biome.Precipitation.SNOW;
        return snowing || s == GameCalendar.Season.AUTUMN || s == GameCalendar.Season.WINTER;
    }
}
