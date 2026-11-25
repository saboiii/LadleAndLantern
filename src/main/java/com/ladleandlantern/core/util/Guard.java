package com.ladleandlantern.core.util;

import com.ladleandlantern.LadleAndLantern;

import java.util.HashMap;
import java.util.Map;

public final class Guard {
    private static final Map<String, Long> LAST_LOGGED = new HashMap<>();

    private Guard() {}

    public static void report(String where, Throwable error) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOGGED.get(where);
        if (last != null && now - last < 60_000) return;
        LAST_LOGGED.put(where, now);
        LadleAndLantern.LOGGER.error("Ladle & Lantern caught an error in {} and carried on. Please report it.", where, error);
    }

    public static void run(String where, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            report(where, e);
        }
    }
}
