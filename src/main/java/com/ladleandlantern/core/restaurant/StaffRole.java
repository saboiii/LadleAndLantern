package com.ladleandlantern.core.restaurant;

public enum StaffRole {
    WAITER("Waiter", 2),
    CASHIER("Cashier", 3);

    public final String title;
    public final int baseWage;

    StaffRole(String title, int baseWage) {
        this.title = title;
        this.baseWage = baseWage;
    }

    public static StaffRole byName(String name) {
        for (StaffRole r : values()) if (r.name().equals(name)) return r;
        return WAITER;
    }
}
