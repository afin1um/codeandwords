package com.example.codeandwords;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Тестовый сценарий 3:
 * Отслеживание прогресса командного челленджа
 */
public class TeamProgressScenarioTest {

    private int calculateProgress(int current, int target) {
        if (target <= 0) return 0;
        int percent = (current * 100) / target;
        return Math.min(percent, 100);
    }

    private boolean isChallengeCompleted(int current, int target) {
        return target > 0 && current >= target;
    }

    // ===== Шаг 1: 0 из 100 =====

    @Test
    public void step1_zeroProgress() {
        assertEquals(0, calculateProgress(0, 100));
        assertFalse(isChallengeCompleted(0, 100));
    }

    // ===== Шаг 2: 50 из 100 =====

    @Test
    public void step2_halfProgress() {
        assertEquals(50, calculateProgress(50, 100));
        assertFalse(isChallengeCompleted(50, 100));
    }

    // ===== Шаг 3: 100 из 100 =====

    @Test
    public void step3_fullProgress() {
        assertEquals(100, calculateProgress(100, 100));
        assertTrue(isChallengeCompleted(100, 100));
    }

    // ===== Шаг 4: 150 из 100 (overflow) =====

    @Test
    public void step4_overflowProgressCapped() {
        assertEquals(100, calculateProgress(150, 100));
        assertTrue(isChallengeCompleted(150, 100));
    }

    // ===== Шаг 5: целевое значение 0 =====

    @Test
    public void step5_zeroTargetSafe() {
        assertEquals(0, calculateProgress(50, 0));
        assertFalse(isChallengeCompleted(50, 0));
    }
}