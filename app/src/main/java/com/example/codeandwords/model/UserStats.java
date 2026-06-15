package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

// Статистика пользователя: серии входов, XP за день, счётчики уроков и ошибок
@Entity(tableName = "user_stats")
public class UserStats {

    @PrimaryKey
    @ColumnInfo(name = "user_id")
    @SerializedName("user_id")
    public int userId;

    @ColumnInfo(name = "login_streak")
    @SerializedName("login_streak")
    public int loginStreak;

    // Unix-время начала дня последнего входа
    @ColumnInfo(name = "last_login_day")
    @SerializedName("last_login_day")
    public long lastLoginDay;

    @ColumnInfo(name = "max_xp_in_day")
    @SerializedName("max_xp_in_day")
    public int maxXpInDay;

    @ColumnInfo(name = "current_day_xp")
    @SerializedName("current_day_xp")
    public int currentDayXp;

    // Unix-время начала дня, за который накоплен currentDayXp
    @ColumnInfo(name = "current_xp_day")
    @SerializedName("current_xp_day")
    public long currentXpDay;

    @ColumnInfo(name = "perfect_lessons_streak")
    @SerializedName("perfect_lessons_streak")
    public int perfectLessonsStreak;

    @ColumnInfo(name = "perfect_lessons_total")
    @SerializedName("perfect_lessons_total")
    public int perfectLessonsTotal;

    @ColumnInfo(name = "lessons_before9")
    @SerializedName("lessons_before9")
    public int lessonsBefore9;

    @ColumnInfo(name = "lessons_after22")
    @SerializedName("lessons_after22")
    public int lessonsAfter22;

    @ColumnInfo(name = "fixed_errors_total")
    @SerializedName("fixed_errors_total")
    public int fixedErrorsTotal;

    @ColumnInfo(name = "completed_tasks_total")
    @SerializedName("completed_tasks_total")
    public int completedTasksTotal;

    @ColumnInfo(name = "sprint_xp_total")
    @SerializedName("sprint_xp_total")
    public int sprintXpTotal;

    @ColumnInfo(name = "created_at")
    @SerializedName("created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    @SerializedName("updated_at")
    public long updatedAt;

    public UserStats() {
    }

    public UserStats(int userId) {
        this.userId = userId;
        this.loginStreak = 0;
        this.lastLoginDay = 0L;
        this.maxXpInDay = 0;
        this.currentDayXp = 0;
        this.currentXpDay = 0L;
        this.perfectLessonsStreak = 0;
        this.perfectLessonsTotal = 0;
        this.lessonsBefore9 = 0;
        this.lessonsAfter22 = 0;
        this.fixedErrorsTotal = 0;
        this.completedTasksTotal = 0;
        this.sprintXpTotal = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
}