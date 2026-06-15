package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

// Запись об одном завершённом уроке; используется для статистики и истории
@Entity(
        tableName = "lesson_history",
        indices = {
                @Index(value = "finished_at", name = "idx_lesson_finished_at"),
                @Index(value = {"user_id", "finished_at"}, name = "idx_user_finished_at")
        }
)
public class LessonHistory {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @SerializedName("id")
    public long id;

    @ColumnInfo(name = "user_id")
    @SerializedName("user_id")
    public int userId;

    @ColumnInfo(name = "lesson_type")
    @SerializedName("lesson_type")
    public String lessonType;

    @ColumnInfo(name = "theme_id")
    @SerializedName("theme_id")
    public Long themeId;

    @ColumnInfo(name = "earned_xp")
    @SerializedName("earned_xp")
    public int earnedXp;

    @ColumnInfo(name = "total_words")
    @SerializedName("total_words")
    public int totalWords;

    @ColumnInfo(name = "mistakes_count")
    @SerializedName("mistakes_count")
    public int mistakesCount;

    @ColumnInfo(name = "fixed_errors_count")
    @SerializedName("fixed_errors_count")
    public int fixedErrorsCount;

    @ColumnInfo(name = "finished_at")
    @SerializedName("finished_at")
    public long finishedAt;

    @ColumnInfo(name = "was_perfect")
    @SerializedName("was_perfect")
    public boolean wasPerfect;

    @ColumnInfo(name = "completed_before9")
    @SerializedName("completed_before9")
    public boolean completedBefore9;

    @ColumnInfo(name = "completed_after22")
    @SerializedName("completed_after22")
    public boolean completedAfter22;

    public LessonHistory() {
    }

    public LessonHistory(int userId, String lessonType, Long themeId, int earnedXp,
                         int totalWords, int mistakesCount, int fixedErrorsCount,
                         long finishedAt, boolean wasPerfect,
                         boolean completedBefore9, boolean completedAfter22) {
        this.userId = userId;
        this.lessonType = lessonType;
        this.themeId = themeId;
        this.earnedXp = earnedXp;
        this.totalWords = totalWords;
        this.mistakesCount = mistakesCount;
        this.fixedErrorsCount = fixedErrorsCount;
        this.finishedAt = finishedAt;
        this.wasPerfect = wasPerfect;
        this.completedBefore9 = completedBefore9;
        this.completedAfter22 = completedAfter22;
    }
}