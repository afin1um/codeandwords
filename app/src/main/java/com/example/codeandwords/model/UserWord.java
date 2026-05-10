package com.example.codeandwords.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "user_personal_words")
public class UserWord {

    // Локальный Room ID — называем в JSON так, чтобы НЕ совпадало с "id" сервера
    // SerializedName указываем уникальное имя, которого нет в ответе сервера
    @PrimaryKey(autoGenerate = true)
    @SerializedName("local_room_id")
    private Long id;

    // ID на сервере Supabase — именно это поле получает значение из JSON поля "id"
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

    // Локальный флаг — сервер его не возвращает, конфликтов нет
    @SerializedName("is_synced")
    private boolean isSynced;

    // ===== КОНСТРУКТОРЫ =====

    // Пустой конструктор для Room и Gson
    public UserWord() {
        this.isSynced = false;
    }

    // Конструктор без темы
    @Ignore
    public UserWord(Integer userId,
                    String word,
                    String translation,
                    String transcription,
                    String notes) {
        this(userId, null, "Без темы", word, translation, transcription, notes);
    }

    // Полный конструктор
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
                ? "Без темы"
                : themeTitle.trim();
        this.word = word;
        this.translation = translation;
        this.transcription = transcription;
        this.notes = notes;
        this.dateAdded = System.currentTimeMillis();
        this.isSynced = false;
    }

    // ===== GETTERS =====

    public Long getId() {
        return id;
    }

    public Long getServerId() {
        return serverId;
    }

    public Integer getUserId() {
        return userId;
    }

    public Long getThemeId() {
        return themeId;
    }

    public String getThemeTitle() {
        return themeTitle == null || themeTitle.trim().isEmpty()
                ? "Без темы"
                : themeTitle;
    }

    public String getWord() {
        return word;
    }

    // Алиас для getWord() — используется в Repository при удалении по термину
    @Ignore
    public String getTerm() {
        return word;
    }

    public String getTranslation() {
        return translation;
    }

    public String getTranscription() {
        return transcription;
    }

    public String getNotes() {
        return notes;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public boolean isSynced() {
        return isSynced;
    }

    // ===== SETTERS =====

    public void setId(Long id) {
        this.id = id;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public void setThemeId(Long themeId) {
        this.themeId = themeId;
    }

    public void setThemeTitle(String themeTitle) {
        this.themeTitle = themeTitle == null || themeTitle.trim().isEmpty()
                ? "Без темы"
                : themeTitle.trim();
    }

    public void setWord(String word) {
        this.word = word;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public void setTranscription(String transcription) {
        this.transcription = transcription;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setDateAdded(long dateAdded) {
        this.dateAdded = dateAdded;
    }

    public void setSynced(boolean synced) {
        isSynced = synced;
    }
}