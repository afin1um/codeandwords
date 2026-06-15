package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

// Тема обучения: раздел ИТ-лексики с текстом теории и уровнем сложности
@Entity(tableName = "themes")
public class Theme implements Serializable {

    @PrimaryKey(autoGenerate = false)
    @SerializedName("id")
    private Long id;

    @ColumnInfo(name = "title")
    @SerializedName("title")
    private String title;

    @ColumnInfo(name = "description")
    @SerializedName("description")
    private String description;

    @ColumnInfo(name = "difficulty_level")
    @SerializedName("difficulty_level")
    private String difficultyLevel;

    // Текст с разметкой [[Перевод|Term]] для подсветки терминов в UI теории
    @ColumnInfo(name = "theory_text")
    @SerializedName("theory_text")
    private String theoryText;

    public Theme() {
    }

    public Theme(Long id, String title, String description,
                 String difficultyLevel, String theoryText) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.difficultyLevel = difficultyLevel;
        this.theoryText = theoryText;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }
    public String getTheoryText() { return theoryText; }
    public void setTheoryText(String theoryText) { this.theoryText = theoryText; }

    @Override
    public String toString() {
        return "Theme{id=" + id + ", title='" + title
                + '\'' + ", difficulty='" + difficultyLevel + '\'' + '}';
    }
}