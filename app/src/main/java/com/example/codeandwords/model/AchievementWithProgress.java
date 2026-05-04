package com.example.codeandwords.model;

import androidx.room.ColumnInfo;

public class AchievementWithProgress {

    @ColumnInfo(name = "id")
    public Long id;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "xp_reward")
    public Integer xpReward;

    @ColumnInfo(name = "condition_type")
    public String conditionType;

    @ColumnInfo(name = "condition_value")
    public Integer conditionValue;

    @ColumnInfo(name = "max_progress")
    public Integer maxProgress;

    @ColumnInfo(name = "icon_res_id")
    public String iconResName;

    @ColumnInfo(name = "current_progress")
    public int currentProgress;

    @ColumnInfo(name = "date_received")
    public long dateReceived;

    @ColumnInfo(name = "is_unlocked")
    public boolean isUnlocked;

    @ColumnInfo(name = "is_new")
    public boolean isNew;
}