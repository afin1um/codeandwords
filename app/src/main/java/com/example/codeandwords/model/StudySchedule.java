package com.example.codeandwords.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "study_schedule",
        indices = {
                @Index(value = {"userId", "scheduleDate", "startTime"}, name = "idx_schedule_user_date_time")
        }
)
public class StudySchedule {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int userId;
    public int themeId;

    public String themeTitle;
    public String themeShortTitle;

    public String scheduleDate; // формат: yyyy-MM-dd
    public String startTime;    // формат: HH:mm
    public String endTime;      // формат: HH:mm
    public String note;

    public StudySchedule(int userId,
                         int themeId,
                         String themeTitle,
                         String themeShortTitle,
                         String scheduleDate,
                         String startTime,
                         String endTime,
                         String note) {
        this.userId = userId;
        this.themeId = themeId;
        this.themeTitle = themeTitle;
        this.themeShortTitle = themeShortTitle;
        this.scheduleDate = scheduleDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.note = note;
    }

    public StudySchedule() {
    }
}