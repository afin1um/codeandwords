package com.example.codeandwords.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_personal_words")
public class UserWord {
    @PrimaryKey(autoGenerate = true)
    private Long id;

    private Integer userId; // Привязка к конкретному пользователю
    private String word;
    private String translation;
    private String transcription;
    private String notes; // Заметки или пример использования
    private long dateAdded;

    public UserWord(Integer userId, String word, String translation, String transcription, String notes) {
        this.userId = userId;
        this.word = word;
        this.translation = translation;
        this.transcription = transcription;
        this.notes = notes;
        this.dateAdded = System.currentTimeMillis();
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getTranslation() { return translation; }
    public void setTranslation(String translation) { this.translation = translation; }
    public String getTranscription() { return transcription; }
    public void setTranscription(String transcription) { this.transcription = transcription; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public long getDateAdded() { return dateAdded; }
    public void setDateAdded(long dateAdded) { this.dateAdded = dateAdded; }
}