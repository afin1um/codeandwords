package com.example.codeandwords.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_achievements",
        foreignKeys = {
                @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "userId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Achievement.class, parentColumns = "id", childColumns = "achievementId", onDelete = ForeignKey.CASCADE)
        })
public class UserAchievement {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public Long userId;
    public Long achievementId;
    public long dateReceived;
    public Integer currentProgress;
    public boolean isNew;

    public UserAchievement(Long userId, Long achievementId, long dateReceived, Integer currentProgress) {
        this.userId = userId;
        this.achievementId = achievementId;
        this.dateReceived = dateReceived;
        this.currentProgress = currentProgress;
        this.isNew = true;
    }
}