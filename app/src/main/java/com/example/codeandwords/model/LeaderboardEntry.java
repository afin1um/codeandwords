package com.example.codeandwords.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Класс LeaderboardEntry (Запись в таблице лидеров).
 * Это не прямое отражение таблицы 'leaderboard', а DTO (Data Transfer Object),
 * который объединяет данные из таблицы 'users' (имя) и 'leaderboard' (очки).
 * Сервер должен отправлять данные именно в таком виде.
 */
public class LeaderboardEntry implements Serializable {

    // Место в рейтинге (1, 2, 3...)
    // Этого поля нет в таблице, оно вычисляется сервером при сортировке
    @SerializedName("rank")
    private Integer rank;

    // Имя пользователя (берется из таблицы users)
    @SerializedName("username")
    private String username;

    // Количество опыта за неделю (из таблицы leaderboard)
    @SerializedName("weekly_xp")
    private Integer weeklyXp;

    // ID пользователя (полезно, если захотим открыть его профиль по клику)
    @SerializedName("user_id")
    private Long userId;

    // =================================================================
    // Конструкторы
    // =================================================================

    public LeaderboardEntry() {
    }

    public LeaderboardEntry(Integer rank, String username, Integer weeklyXp, Long userId) {
        this.rank = rank;
        this.username = username;
        this.weeklyXp = weeklyXp;
        this.userId = userId;
    }

    // =================================================================
    // Геттеры и Сеттеры
    // =================================================================

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getWeeklyXp() {
        return weeklyXp;
    }

    public void setWeeklyXp(Integer weeklyXp) {
        this.weeklyXp = weeklyXp;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    // =================================================================
    // Переопределение toString
    // =================================================================

    @Override
    public String toString() {
        return "LeaderboardEntry{" +
                "rank=" + rank +
                ", username='" + username + '\'' +
                ", xp=" + weeklyXp +
                '}';
    }
}