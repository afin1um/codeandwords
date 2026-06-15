package com.example.codeandwords.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// Запись расписания занятий пользователя
@Entity(
        tableName = "study_schedule",
        indices = {
                @Index(value = {"userId", "scheduleDate", "startTime"},
                        name = "idx_schedule_user_date_time")
        }
)
public class StudySchedule {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int userId;
    public int themeId;
    public String themeTitle;
    public String themeShortTitle;

    // Дата в формате yyyy-MM-dd
    public String scheduleDate;

    // Время начала и конца в формате HH:mm
    public String startTime;
    public String endTime;
    public String note;

    public StudySchedule(int userId, int themeId, String themeTitle, String themeShortTitle,
                         String scheduleDate, String startTime, String endTime, String note) {
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