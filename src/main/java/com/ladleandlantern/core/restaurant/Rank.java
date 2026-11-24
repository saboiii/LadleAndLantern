package com.ladleandlantern.core.restaurant;

import com.ladleandlantern.core.food.Order;

public final class Rank {
    private static final String[] TITLES = {
            "Trainee Tastebud", "Spoon Polisher", "Cashier", "Order Whisperer", "Meal Builder", "Part-Time Cook",
            "Line Cook", "Lantern Lighter", "Sous Chef", "Head Chef", "Ladle Legend", "Keeper of the Hearth"};
    private static final int[] POINTS = {0, 300, 900, 1800, 3200, 5000, 7500, 11000, 15000, 20000, 27000, 36000};

    private Rank() {}

    public static int of(long points) {
        int rank = 1;
        for (int i = 0; i < POINTS.length; i++) if (points >= POINTS[i]) rank = i + 1;
        return rank;
    }

    public static String title(int rank) {
        return TITLES[Math.max(0, Math.min(TITLES.length, rank) - 1)];
    }

    public static int nextThreshold(int rank) {
        return rank >= POINTS.length ? -1 : POINTS[rank];
    }

    public static int threshold(int rank) {
        return POINTS[Math.max(0, Math.min(POINTS.length, rank) - 1)];
    }
}
