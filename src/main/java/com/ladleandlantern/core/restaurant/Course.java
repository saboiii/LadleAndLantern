package com.ladleandlantern.core.restaurant;

public enum Course {
    DRINK("Drink", 0.50f),
    MAIN("Main", 0.70f),
    SIDE("Side", 0.35f),
    DESSERT("Dessert", 0.30f);

    public final String label;
    public final float chance;

    Course(String label, float chance) {
        this.label = label;
        this.chance = chance;
    }

    public Course next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Course byName(String name) {
        for (Course c : values()) if (c.name().equals(name)) return c;
        return MAIN;
    }
}
