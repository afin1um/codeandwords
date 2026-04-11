package com.example.codeandwords.model;

public class AchievementWithProgress {
    public Long id;
    public String title;
    public String description;
    public Integer xpReward;
    public String conditionType;
    public Integer conditionValue;
    public Integer maxProgress;
    public String iconResName;

    public int currentProgress;
    public long dateReceived;
    public boolean isUnlocked;
    public boolean isNew;
}