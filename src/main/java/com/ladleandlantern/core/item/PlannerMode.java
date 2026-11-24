package com.ladleandlantern.core.item;

public enum PlannerMode {
    BOUNDS("Bounds", "Click a floor block in one corner, then a ceiling block in the opposite corner."),
    SEAT("Seats", "Click a chair to add or select it. If you picked a table first, the chair sits at it. Sneak-click to remove."),
    TABLE("Tables", "Click a table for the selected seat, or pick a table first and then click its chairs in Seats mode. Sneak-click to remove."),
    COUNTER("Counter", "Click blocks to mark or unmark the counter."),
    ENTRANCE("Entrance", "Click the floor where customers arrive and leave."),
    WAITING_LINE("Waiting line", "Click up to 5 floor blocks outside, front of the line first. Click again to remove.");

    public final String label;
    public final String hint;

    PlannerMode(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public static PlannerMode byId(int id) {
        return values()[Math.floorMod(id, values().length)];
    }
}
