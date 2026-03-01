package com.example.codeandwords.model;

import androidx.room.Embedded;

public class AchievementWithProgress {
    @Embedded
    public Achievement achievement;

    public Long userAchievementId;
    public Integer currentProgress;
    public Boolean isNew;
    public Long dateReceived;
}