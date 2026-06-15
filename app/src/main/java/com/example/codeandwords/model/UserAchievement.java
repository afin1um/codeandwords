package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

// Связь пользователя с достижением: хранит прогресс и статус разблокировки
@Entity(
        tableName = "user_achievements",
        foreignKeys = {
                @ForeignKey(entity = User.class, parentColumns = "id",
                        childColumns = "user_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Achievement.class, parentColumns = "id",
                        childColumns = "achievement_id", onDelete = ForeignKey.CASCADE)
        }
)
public class UserAchievement {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @SerializedName("id")
    public Long id;

    @ColumnInfo(name = "user_id")
    @SerializedName("user_id")
    public Long userId;

    @ColumnInfo(name = "achievement_id")
    @SerializedName("achievement_id")
    public Long achievementId;

    @ColumnInfo(name = "received_at")
    @SerializedName("received_at")
    public long dateReceived;

    @ColumnInfo(name = "current_progress")
    @SerializedName("current_progress")
    public Integer currentProgress;

    @ColumnInfo(name = "is_unlocked")
    @SerializedName("is_unlocked")
    public boolean isUnlocked;

    @ColumnInfo(name = "is_new")
    @SerializedName("is_new")
    public boolean isNew;

    public UserAchievement() {
    }

    public UserAchievement(Long userId, Long achievementId, long dateReceived,
                           Integer currentProgress, boolean isUnlocked, boolean isNew) {
        this.userId = userId;
        this.achievementId = achievementId;
        this.dateReceived = dateReceived;
        this.currentProgress = currentProgress;
        this.isUnlocked = isUnlocked;
        this.isNew = isNew;
    }
}