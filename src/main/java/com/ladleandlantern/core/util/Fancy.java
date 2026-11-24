package com.ladleandlantern.core.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Fancy {
    public static final int PINK = 0xF4A6C0, PEACH = 0xF7C08A, GOLD = 0xF2C14E, MINT = 0x8ED9A8, LAVENDER = 0xB9A6F0, CREAM = 0xF3E3C3, SOFT = 0xC9B79C;

    private Fancy() {}

    public static MutableComponent gradient(String text, int from, int to) {
        MutableComponent out = Component.empty();
        int n = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            float t = i / (float) n;
            out.append(Component.literal(String.valueOf(text.charAt(i))).withColor(mix(from, to, t)));
        }
        return out;
    }

    public static MutableComponent plain(String text, int colour) {
        return Component.literal(text).withColor(colour);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
