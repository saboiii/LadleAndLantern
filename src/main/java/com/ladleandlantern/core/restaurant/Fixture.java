package com.ladleandlantern.core.restaurant;

public enum Fixture {
    TIP_JAR, UMBRELLA_STAND, COAT_RACK, WATER_STATION, CORKBOARD, CAT_BED, GRAMOPHONE, MENU_STAND, BAG_RACK;

    public static Fixture byName(String name) {
        for (Fixture f : values()) if (f.name().equals(name)) return f;
        return null;
    }
}
