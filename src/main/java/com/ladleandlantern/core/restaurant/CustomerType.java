package com.ladleandlantern.core.restaurant;

public enum CustomerType {
    REGULAR(0xFFFFFF, 1.0f),
    CRITIC(0xFFC83D, 1.0f),
    CLOSER(0xC77DFF, 0.9f);

    public final int colour;

    public final float scoreMultiplier;

    CustomerType(int colour, float scoreMultiplier) {
        this.colour = colour;
        this.scoreMultiplier = scoreMultiplier;
    }

    public boolean rushed() {
        return this != REGULAR;
    }

    public static CustomerType byId(int id) {
        return values()[Math.floorMod(id, values().length)];
    }
}
