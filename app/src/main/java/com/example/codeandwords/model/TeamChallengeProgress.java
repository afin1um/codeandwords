package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "team_challenge_progress")
public class TeamChallengeProgress {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    @ColumnInfo(name = "id")
    public int id;

    @SerializedName("challenge_id")
    @ColumnInfo(name = "challenge_id")
    public int challengeId;

    @SerializedName("team_id")
    @ColumnInfo(name = "team_id")
    public int teamId;

    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    public int userId;

    @SerializedName("progress")
    @ColumnInfo(name = "progress")
    public int progress;

    @SerializedName("is_completed")
    @ColumnInfo(name = "is_completed")
    public boolean isCompleted;

    @SerializedName("completed_at")
    @ColumnInfo(name = "completed_at")
    public String completedAt;

    @SerializedName("place")
    @ColumnInfo(name = "place")
    public Integer place;

    @SerializedName("awarded_xp")
    @ColumnInfo(name = "awarded_xp")
    public int awardedXp;

    // ✅ ИСПРАВЛЕНО: Теперь ник сохраняется в базу данных
    @SerializedName("username")
    @ColumnInfo(name = "username")
    public String username;

    public TeamChallengeProgress() {
    }
}