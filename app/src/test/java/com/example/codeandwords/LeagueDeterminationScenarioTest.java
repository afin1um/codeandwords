package com.example.codeandwords;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Тестовый сценарий 2:
 * Определение лиги пользователя по опыту
 */
public class LeagueDeterminationScenarioTest {

    private String getLeague(int totalXp) {
        if (totalXp >= 2500) return "Алмазная лига";
        if (totalXp >= 1200) return "Изумрудная лига";
        if (totalXp >= 600)  return "Золотая лига";
        if (totalXp >= 250)  return "Серебряная лига";
        return "Бронзовая лига";
    }

    // ===== Шаг 1: 0 XP — Бронзовая лига =====

    @Test
    public void step1_zeroXpBronzeLeague() {
        assertEquals("Бронзовая лига", getLeague(0));
    }

    @Test
    public void step1_lowXpStillBronze() {
        assertEquals("Бронзовая лига", getLeague(249));
    }

    // ===== Шаг 2: 250 XP — Серебряная лига =====

    @Test
    public void step2_exactSilverThreshold() {
        assertEquals("Серебряная лига", getLeague(250));
    }

    @Test
    public void step2_midSilverRange() {
        assertEquals("Серебряная лига", getLeague(450));
    }

    // ===== Шаг 3: 600 XP — Золотая лига =====

    @Test
    public void step3_exactGoldThreshold() {
        assertEquals("Золотая лига", getLeague(600));
    }

    @Test
    public void step3_midGoldRange() {
        assertEquals("Золотая лига", getLeague(900));
    }

    // ===== Шаг 4: 1200 XP — Изумрудная лига =====

    @Test
    public void step4_exactEmeraldThreshold() {
        assertEquals("Изумрудная лига", getLeague(1200));
    }

    // ===== Шаг 5: 2500 XP — Алмазная лига =====

    @Test
    public void step5_exactDiamondThreshold() {
        assertEquals("Алмазная лига", getLeague(2500));
    }

    @Test
    public void step5_highXpDiamond() {
        assertEquals("Алмазная лига", getLeague(5000));
    }
}