package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

// Командное задание с условием выполнения (XP или количество уроков) и наградами по местам
@Entity(tableName = "team_challenges")
public class TeamChallenge {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    @ColumnInfo(name = "id")
    public int id;

    @SerializedName("team_id")
    @ColumnInfo(name = "team_id")
    public int teamId;

    @SerializedName("title")
    @ColumnInfo(name = "title")
    public String title;

    // Тип условия: "XP" или "LESSONS"
    @SerializedName("condition_type")
    @ColumnInfo(name = "condition_type")
    public String conditionType;

    @SerializedName("target_value")
    @ColumnInfo(name = "target_value")
    public int targetValue;

    // XP-награды за 1-е, 2-е место и остальных участников
    @SerializedName("xp_first")
    @ColumnInfo(name = "xp_first")
    public int xpFirst;

    @SerializedName("xp_second")
    @ColumnInfo(name = "xp_second")
    public int xpSecond;

    @SerializedName("xp_other")
    @ColumnInfo(name = "xp_other")
    public int xpOther;

    @SerializedName("is_completed")
    @ColumnInfo(name = "is_completed")
    public boolean isCompleted;

    @SerializedName("winner_user_id")
    @ColumnInfo(name = "winner_user_id")
    public Integer winnerUserId;

    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    public String createdAt;

    @SerializedName("completed_at")
    @ColumnInfo(name = "completed_at")
    public String completedAt;

    public TeamChallenge() {
    }
}