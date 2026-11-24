package com.ladleandlantern.core.food;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoringTest {
    @Test
    void quickPerfectOrderScoresFull() {
        var r = Scoring.score(List.of("a", "b"), List.of("b", "a"), 40, 200, 1f);
        assertEquals(100, r.score());
        assertTrue(r.onTime());
    }

    @Test
    void waitingLongerAlwaysCostsSomething() {
        int fast = Scoring.score(List.of("a"), List.of("a"), 50, 200, 1f).score();
        int slower = Scoring.score(List.of("a"), List.of("a"), 150, 200, 1f).score();
        int atDeadline = Scoring.score(List.of("a"), List.of("a"), 200, 200, 1f).score();
        int veryLate = Scoring.score(List.of("a"), List.of("a"), 400, 200, 1f).score();
        assertTrue(fast > slower && slower > atDeadline && atDeadline > veryLate);
        assertEquals(60, veryLate);
    }

    @Test
    void missingAndExtraItemsCostAccuracy() {
        assertEquals(50, Scoring.score(List.of("a", "b"), List.of("a"), 0, 200, 1f).score());
        assertEquals(50, Scoring.score(List.of("a"), List.of("a", "z"), 0, 200, 1f).score());
    }

    @Test
    void wrongFoodScoresNothingEvenWhenFast() {
        assertEquals(0, Scoring.score(List.of("a"), List.of("z"), 0, 200, 1f).score());
    }

    @Test
    void duplicatesAreCountedSeparately() {
        assertEquals(1, Scoring.score(List.of("a", "a"), List.of("a"), 0, 200, 1f).missing());
    }
}
