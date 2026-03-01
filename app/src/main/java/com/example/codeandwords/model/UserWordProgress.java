package com.example.codeandwords.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UserWordProgress {
    @SerializedName("id")
    @Expose
    private Long id; // Это поле первичного ключа из Supabase

    @SerializedName("user_id")
    @Expose
    private Long userId;

    @SerializedName("word_id")
    @Expose
    private Long wordId;

    @SerializedName("correct_answers_count")
    @Expose
    private Integer correctAnswersCount;

    @SerializedName("is_learned")
    @Expose
    private Boolean isLearned;

    // ГЕТТЕР ДЛЯ ID (Его не хватало!)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }

    public Integer getCorrectAnswersCount() { return correctAnswersCount; }
    public void setCorrectAnswersCount(Integer correctAnswersCount) { this.correctAnswersCount = correctAnswersCount; }

    public Boolean getIsLearned() { return isLearned; }
    public void setIsLearned(Boolean isLearned) { this.isLearned = isLearned; }
}