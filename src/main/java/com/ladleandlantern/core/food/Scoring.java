package com.ladleandlantern.core.food;

import java.util.ArrayList;
import java.util.List;

public final class Scoring {
    public record Result(int score, float accuracy, boolean onTime, int missing, int extra, float speed) {}

    private static final float GRACE = 0.25f;

    private Scoring() {}

    public static <T> Result score(List<T> ordered, List<T> served, long elapsedTicks, int deadlineTicks, float multiplier) {
        List<T> remaining = new ArrayList<>(ordered);
        int correct = 0;
        int extra = 0;
        for (T item : served) {
            if (remaining.remove(item)) correct++;
            else extra++;
        }
        int total = Math.max(1, ordered.size());
        float accuracy = clamp(correct / (float) total - 0.5f * extra / total);
        float deadline = Math.max(1, deadlineTicks);
        float speed = clamp(1f - Math.max(0f, elapsedTicks - GRACE * deadline) / ((2f - GRACE) * deadline));
        int accuracyPoints = Math.round(60f * accuracy * multiplier);
        int speedPoints = Math.round(40f * speed * accuracy * multiplier);
        int score = Math.max(0, Math.min(100, accuracyPoints + speedPoints));
        return new Result(score, accuracy, elapsedTicks <= deadlineTicks, remaining.size(), extra, speed);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
