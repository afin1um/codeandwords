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

    /**
     * Локальный id Room.
     * На сервер НЕ отправляем как "id".
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @SerializedName("local_room_id")
    private Long id;

    /**
     * id записи на сервере Supabase.
     * На сервере это колонка "id".
     */
    @ColumnInfo(name = "serverId")
    @SerializedName("id")
    private Long serverId;

    @ColumnInfo(name = "userId")
    @SerializedName("user_id")
    private Integer userId;

    /**
     * Локально поле называется themeId.
     * На сервере у тебя колонка называется themeid.
     */
    @ColumnInfo(name = "themeId")
    @SerializedName(value = "themeid", alternate = {"theme_id"})
    private Long themeId;

    /**
     * Локально поле называется themeTitle.
     * На сервере у тебя колонка называется themetitle.
     */
    @ColumnInfo(name = "themeTitle")
    @SerializedName(value = "themetitle", alternate = {"theme_title"})
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

    /**
     * ВАЖНО:
     * На сервере date_added — строка timestamp.
     * Локально dateAdded — long.
     *
     * Поэтому НЕ ставим @SerializedName("date_added"),
     * иначе Gson будет пытаться строку даты превратить в long
     * и снова будет NumberFormatException.
     */
    @ColumnInfo(name = "dateAdded")
    private long dateAdded;

    /**
     * Локальное поле.
     * На сервере колонки is_synced нет, поэтому SerializedName убран.
     */
    @ColumnInfo(name = "isSynced")
    private boolean isSynced;

    public UserWord() {
        this.isSynced = false;
        this.dateAdded = System.currentTimeMillis();
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
                ? "Без темы"
                : themeTitle.trim();
        this.word = word;
        this.translation = translation;
        this.transcription = transcription;
        this.notes = notes;
        this.dateAdded = System.currentTimeMillis();
        this.isSynced = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Long getThemeId() {
        return themeId;
    }

    public void setThemeId(Long themeId) {
        this.themeId = themeId;
    }

    public String getThemeTitle() {
        return themeTitle == null || themeTitle.trim().isEmpty()
                ? "Без темы"
                : themeTitle;
    }

    public void setThemeTitle(String themeTitle) {
        this.themeTitle = themeTitle == null || themeTitle.trim().isEmpty()
                ? "Без темы"
                : themeTitle.trim();
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getTranscription() {
        return transcription;
    }

    public void setTranscription(String transcription) {
        this.transcription = transcription;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(long dateAdded) {
        this.dateAdded = dateAdded;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public void setSynced(boolean synced) {
        this.isSynced = synced;
    }

    @Ignore
    public String getTerm() {
        return word;
    }
}