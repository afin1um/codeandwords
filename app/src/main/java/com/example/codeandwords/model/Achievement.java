package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Класс Achievement (Достижение).
 * Описывает сущность достижения, которая хранится в PostgreSQL (Supabase)
 * и кешируется локально через Room.
 */
@Entity(tableName = "achievements")
public class Achievement implements Serializable {

    @PrimaryKey(autoGenerate = false)
    @SerializedName("id")
    private Long id;

    @ColumnInfo(name = "title")
    @SerializedName("title")
    private String title;

    @ColumnInfo(name = "description")
    @SerializedName("description")
    private String description;

    @ColumnInfo(name = "xp_reward")
    @SerializedName("xp_reward")
    private Integer xpReward;

    /**
     * Тип условия: "XP", "WORDS", "STREAK", "THEME" и т.д.
     */
    @ColumnInfo(name = "condition_type")
    @SerializedName("condition_type")
    private String conditionType;

    @ColumnInfo(name = "condition_value")
    @SerializedName("condition_value")
    private Integer conditionValue;

    /**
     * Поле для отображения прогресса в UI (например, 5/10 изученных слов)
     */
    @ColumnInfo(name = "max_progress")
    @SerializedName("max_progress")
    private Integer maxProgress;

    /**
     * Имя ресурса иконки в папке res/drawable
     */
    @ColumnInfo(name = "icon_res_id")
    @SerializedName("icon_res_id")
    private String iconResName;

    // --- Конструкторы ---

    public Achievement() {
    }

    public Achievement(Long id, String title, String description, Integer xpReward,
                       String conditionType, Integer conditionValue, Integer maxProgress, String iconResName) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.xpReward = xpReward;
        this.conditionType = conditionType;
        this.conditionValue = conditionValue;
        this.maxProgress = maxProgress;
        this.iconResName = iconResName;
    }

    // --- Геттеры и Сеттеры ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getXpReward() { return xpReward; }
    public void setXpReward(Integer xpReward) { this.xpReward = xpReward; }

    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }

    public Integer getConditionValue() { return conditionValue; }
    public void setConditionValue(Integer conditionValue) { this.conditionValue = conditionValue; }

    public Integer getMaxProgress() { return maxProgress; }
    public void setMaxProgress(Integer maxProgress) { this.maxProgress = maxProgress; }

    public String getIconResName() { return iconResName; }
    public void setIconResName(String iconResName) { this.iconResName = iconResName; }

    @Override
    public String toString() {
        return "Achievement{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", xpReward=" + xpReward +
                '}';
    }
}