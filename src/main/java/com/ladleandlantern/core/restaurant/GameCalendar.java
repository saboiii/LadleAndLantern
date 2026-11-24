package com.ladleandlantern.core.restaurant;

import java.util.ArrayList;
import java.util.List;

public final class GameCalendar {
    public enum Season { SPRING, SUMMER, AUTUMN, WINTER;
        public String label() { return name().charAt(0) + name().substring(1).toLowerCase(); }
    }

    public enum Holiday {
        LUNAR_NEW_YEAR("Lunar New Year", 2, 10),
        VALENTINES("Valentine's Day", 2, 14),
        EASTER("Easter", 4, 5),
        MOTHERS_DAY("Mother's Day", 5, 10),
        HALLOWEEN("Halloween", 10, 31),
        THANKSGIVING("Thanksgiving", 11, 26),
        CHRISTMAS("Christmas", 12, 25),
        NEW_YEARS_EVE("New Year's Eve", 12, 31);

        public final String label;
        private final int month;
        private final int dayOfMonth;

        Holiday(String label, int month, int dayOfMonth) {
            this.label = label;
            this.month = month;
            this.dayOfMonth = dayOfMonth;
        }

        public int dayOfYear(int seasonLength) {
            double fraction = (month - 3 + (dayOfMonth - 1) / 30.0) / 12.0;
            fraction = fraction - Math.floor(fraction);
            return (int) Math.floor(fraction * seasonLength * 4);
        }
    }

    private GameCalendar() {}

    public static Season season(long day, int seasonLength) {
        return Season.values()[(int) (Math.floorMod(day, seasonLength * 4L) / seasonLength)];
    }

    public static List<Holiday> holidays(long day, int seasonLength) {
        int dayOfYear = (int) Math.floorMod(day, seasonLength * 4L);
        List<Holiday> active = new ArrayList<>();
        for (Holiday h : Holiday.values()) if (h.dayOfYear(seasonLength) == dayOfYear) active.add(h);
        return active;
    }

    public static int dayOfSeason(long day, int seasonLength) {
        return (int) (Math.floorMod(day, seasonLength * 4L) % seasonLength) + 1;
    }
}
