package com.example.codeandwords.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;

@Entity(tableName = "daily_quests")
public class DailyQuest implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private Long id;
    private String description; // Например: "Заработать 50 XP"
    private int targetCount;    // Цель: 50
    private int currentProgress;// Текущий: 10
    private int xpReward;       // Награда: 20
    private String type;        // Тип: "XP", "GAME_PLAYED", "WORDS"
    private boolean isCompleted;
    private long dateCreated;   // Чтобы знать, когда обновлять квесты

    // Конструктор
    public DailyQuest(String description, int targetCount, int xpReward, String type, long dateCreated) {
        this.description = description;
        this.targetCount = targetCount;
        this.xpReward = xpReward;
        this.type = type;
        this.dateCreated = dateCreated;
        this.currentProgress = 0;
        this.isCompleted = false;
    }

    // Геттеры и сеттеры
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