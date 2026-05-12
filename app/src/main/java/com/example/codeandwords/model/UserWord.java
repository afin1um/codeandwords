package com.example.codeandwords.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "user_personal_words")
public class UserWord {

    // Локальный Room ID — в JSON называется "local_room_id"
    // Сервер такого поля не возвращает — Room сам генерирует ID
    @PrimaryKey(autoGenerate = true)
    @SerializedName("local_room_id")
    private Long id;

    // ID записи на сервере Supabase — маппится на JSON поле "id"
    @SerializedName("id")
    private Long serverId;

    @SerializedName("user_id")
    private Integer userId;

    @SerializedName("theme_id")
    private Long themeId;

    @SerializedName("theme_title")
    private String themeTitle;

    @SerializedName("word")
    private String word;

    @SerializedName("translation")
    private String translation;

    @SerializedName("transcription")
    private String transcription;

    @SerializedName("notes")
    private String notes;

    @SerializedName("date_added")
    private long dateAdded;

    // Локальный флаг — сервер его не возвращает
    @SerializedName("is_synced")
    private boolean isSynced;

    // ===== КОНСТРУКТОРЫ =====

    public UserWord() {
        this.isSynced = false;
    }

    @Ignore
    public UserWord(Integer userId,
                    String word,
                    String translation,
                    String transcription,
                    String notes) {
        this(userId, null, "Без темы", word, translation, transcription, notes);
    }

    @Ignore
    public UserWord(Integer userId,
                    Long themeId,
                    String themeTitle,
                    String word,
                    String translation,
                    String transcription,
                    String notes) {
        this.userId = userId;
        this.themeId = themeId;
        this.themeTitle = themeTitle == null || themeTitle.trim().isEmpty()
                ? "Без темы" : themeTitle.trim();
        this.word = word;
        this.translation = translation;
        this.transcription = transcription;
        this.notes = notes;
        this.dateAdded = System.currentTimeMillis();
        this.isSynced = false;
    }

    // ===== GETTERS =====

    public Long getId() { return id; }

    public Long getServerId() { return serverId; }

    public Integer getUserId() { return userId; }

    public Long getThemeId() { return themeId; }

    public String getThemeTitle() {
        return themeTitle == null || themeTitle.trim().isEmpty()
                ? "Без темы" : themeTitle;
    }

    public String getWord() { return word; }

    @Ignore
    public String getTerm() { return word; }

    public String getTranslation() { return translation; }

    public String getTranscription() { return transcription; }

    public String getNotes() { return notes; }

    public long getDateAdded() { return dateAdded; }

    public boolean isSynced() { return isSynced; }

    // ===== SETTERS =====

    public void setId(Long id) { this.id = id; }

    public void setServerId(Long serverId) { this.serverId = serverId; }

    public void setUserId(Integer userId) { this.userId = userId; }

    public void setThemeId(Long themeId) { this.themeId = themeId; }

    public void setThemeTitle(String themeTitle) {
        this.themeTitle = themeTitle == null || themeTitle.trim().isEmpty()
                ? "Без темы" : themeTitle.trim();
    }

    public void setWord(String word) { this.word = word; }

    public void setTranslation(String translation) { this.translation = translation; }

    public void setTranscription(String transcription) { this.transcription = transcription; }

    public void setNotes(String notes) { this.notes = notes; }

    public void setDateAdded(long dateAdded) { this.dateAdded = dateAdded; }

    public void setSynced(boolean synced) { isSynced = synced; }
}