package com.example.codeandwords.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;

// Модель ежедневного квеста; хранится локально и сбрасывается раз в день
@Entity(tableName = "daily_quests")
public class DailyQuest implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private Long id;

    private String description;
    private int targetCount;
    private int currentProgress;
    private int xpReward;

    // Тип квеста: "XP", "GAME_PLAYED", "WORDS"
    private String type;

    private boolean isCompleted;

    // Unix-время создания — используется для сброса квестов в новый день
    private long dateCreated;

    public DailyQuest(String description, int targetCount, int xpReward,
                      String type, long dateCreated) {
        this.description = description;
        this.targetCount = targetCount;
        this.xpReward = xpReward;
        this.type = type;
        this.dateCreated = dateCreated;
        this.currentProgress = 0;
        this.isCompleted = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDescription() { return description; }
    public int getTargetCount() { return targetCount; }
    public int getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(int currentProgress) { this.currentProgress = currentProgress; }
    public int getXpReward() { return xpReward; }
    public String getType() { return type; }
    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
    public long getDateCreated() { return dateCreated; }
}