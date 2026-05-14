package com.example.codeandwords;

import com.example.codeandwords.model.User;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Тестовый сценарий 1:
 * Создание команды с челленджем
 */
public class TeamCreationScenarioTest {

    // ===== Вспомогательные методы =====

    private boolean isTeamNameValid(String name) {
        return name != null && !name.trim().isEmpty();
    }

    private boolean isTeamSizeValid(List<User> friends) {
        return friends != null && friends.size() >= 1 && friends.size() <= 3;
    }

    private String resolveChallengeType(int position) {
        return position == 1 ? "LESSONS" : "XP";
    }

    private boolean isTargetValid(String targetStr) {
        try {
            int value = Integer.parseInt(targetStr);
            return value > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ===== Шаг 1: Заполнение названия команды =====

    @Test
    public void step1_validTeamName() {
        assertTrue(isTeamNameValid("Dream Team"));
    }

    @Test
    public void step1_emptyTeamName() {
        assertFalse(isTeamNameValid(""));
    }

    @Test
    public void step1_nullTeamName() {
        assertFalse(isTeamNameValid(null));
    }

    // ===== Шаг 2: Выбор типа челленджа =====

    @Test
    public void step2_selectXpChallenge() {
        assertEquals("XP", resolveChallengeType(0));
    }

    @Test
    public void step2_selectLessonsChallenge() {
        assertEquals("LESSONS", resolveChallengeType(1));
    }

    // ===== Шаг 3: Выбор целевого значения =====

    @Test
    public void step3_validTarget() {
        assertTrue(isTargetValid("100"));
    }

    @Test
    public void step3_validTargetMax() {
        assertTrue(isTargetValid("300"));
    }

    @Test
    public void step3_invalidTargetZero() {
        assertFalse(isTargetValid("0"));
    }

    // ===== Шаг 4: Выбор друзей =====

    @Test
    public void step4_selectOneFriend() {
        List<User> friends = new ArrayList<>();
        friends.add(new User());
        assertTrue(isTeamSizeValid(friends));
    }

    @Test
    public void step4_selectThreeFriends() {
        List<User> friends = new ArrayList<>();
        friends.add(new User());
        friends.add(new User());
        friends.add(new User());
        assertTrue(isTeamSizeValid(friends));
    }

    @Test
    public void step4_selectFourFriendsExceedsLimit() {
        List<User> friends = new ArrayList<>();
        for (int i = 0; i < 4; i++) friends.add(new User());
        assertFalse(isTeamSizeValid(friends));
    }

    @Test
    public void step4_noFriendsSelected() {
        assertFalse(isTeamSizeValid(new ArrayList<>()));
    }

    // ===== Шаг 5: Создание команды (все проверки вместе) =====

    @Test
    public void step5_createTeamSuccess() {
        String name = "Dream Team";
        List<User> friends = new ArrayList<>();
        friends.add(new User());
        friends.add(new User());
        String target = "100";

        assertTrue(isTeamNameValid(name));
        assertTrue(isTeamSizeValid(friends));
        assertTrue(isTargetValid(target));
    }

    @Test
    public void step5_createTeamFailEmptyName() {
        String name = "";
        List<User> friends = new ArrayList<>();
        friends.add(new User());

        assertFalse(isTeamNameValid(name));
    }
}