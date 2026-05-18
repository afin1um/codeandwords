package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(
        tableName = "user_personal_words",
        indices = {
                @Index(value = "userId", name = "idx_userword_userid"),
                @Index(value = {"userId", "themeId"}, name = "idx_userword_user_theme"),
                @Index(value = {"userId", "dateAdded"}, name = "idx_userword_user_date")
        }
)
public class UserWord {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @SerializedName("local_room_id")
    private Long id;

    @ColumnInfo(name = "serverId")
    @SerializedName("id")
    private Long serverId;

    @ColumnInfo(name = "userId")
    @SerializedName("user_id")
    private Integer userId;

    @ColumnInfo(name = "themeId")
    @SerializedName("theme_id")
    private Long themeId;

    @ColumnInfo(name = "themeTitle")
    @SerializedName("theme_title")
    private String themeTitle;

    @ColumnInfo(name = "word")
    @SerializedName("word")
    private String word;

    @ColumnInfo(name = "translation")
    @SerializedName("translation")
    private String translation;

    @ColumnInfo(name = "transcription")
    @SerializedName("transcription")
    private String transcription;

    @ColumnInfo(name = "notes")
    @SerializedName("notes")
    private String notes;

    @ColumnInfo(name = "dateAdded")
    @SerializedName("date_added")
    private long dateAdded;

    @ColumnInfo(name = "isSynced")
    @SerializedName("is_synced")
    private boolean isSynced;

    public UserWord() {
        this.isSynced = false;
    }

    @Ignore
    public UserWord(Integer userId, String word, String translation, String transcription, String notes) {
        this(userId, null, "Без темы", word, translation, transcription, notes);
    }

    @Ignore
    public UserWord(Integer userId, Long themeId, String themeTitle, String word, String translation, String transcription, String notes) {
        this.userId = userId;
        this.themeId = themeId;
        this.themeTitle = themeTitle == null || themeTitle.trim().isEmpty() ? "Без темы" : themeTitle.trim();
        this.word = word;
        this.translation = translation;
        this.transcription = transcription;
        this.notes = notes;
        this.dateAdded = System.currentTimeMillis();
        this.isSynced = false;
    }

    // Геттеры и сеттеры — все как были, только с правильными @ColumnInfo
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }
    public String getThemeTitle() { return themeTitle == null || themeTitle.trim().isEmpty() ? "Без темы" : themeTitle; }
    public void setThemeTitle(String themeTitle) { this.themeTitle = themeTitle == null || themeTitle.trim().isEmpty() ? "Без темы" : themeTitle.trim(); }
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
    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { this.isSynced = synced; }

    @Ignore
    public String getTerm() { return word; }
}