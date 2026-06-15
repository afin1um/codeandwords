package com.example.codeandwords.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

// DTO для строки таблицы лидеров: объединяет данные из leaderboard и users
public class LeaderboardEntry implements Serializable {

    // Место вычисляется на стороне клиента по порядку сортировки
    @SerializedName("rank")
    private Integer rank;

    @SerializedName("username")
    private String username;

    @SerializedName("weekly_xp")
    private Integer weeklyXp;

    @SerializedName("user_id")
    private Long userId;

    public LeaderboardEntry() {
    }

    public LeaderboardEntry(Integer rank, String username, Integer weeklyXp, Long userId) {
        this.rank = rank;
        this.username = username;
        this.weeklyXp = weeklyXp;
        this.userId = userId;
    }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getWeeklyXp() { return weeklyXp; }
    public void setWeeklyXp(Integer weeklyXp) { this.weeklyXp = weeklyXp; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    @Override
    public String toString() {
        return "LeaderboardEntry{rank=" + rank + ", username='" + username
                + '\'' + ", xp=" + weeklyXp + '}';
    }
}