package com.example.codeandwords.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UserWordProgress {

    @SerializedName("id")
    @Expose
    private Long id;

    @SerializedName("user_id")
    @Expose
    private Long userId;

    @SerializedName("word_id")
    @Expose
    private Long wordId;

    @SerializedName("correct_answers_count")
    @Expose
    private Integer correctAnswersCount = 0;

    @SerializedName("mistakes_count")
    @Expose
    private Integer mistakesCount = 0;

    @SerializedName("is_learned")
    @Expose
    private Boolean isLearned = false;

    @SerializedName("passed_sprint")
    @Expose
    private Boolean passedSprint = false;

    @SerializedName("passed_matching")
    @Expose
    private Boolean passedMatching = false;

    @SerializedName("passed_writing")
    @Expose
    private Boolean passedWriting = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getWordId() {
        return wordId;
    }

    public void setWordId(Long wordId) {
        this.wordId = wordId;
    }

    public Integer getCorrectAnswersCount() {
        return correctAnswersCount == null ? 0 : correctAnswersCount;
    }

    public void setCorrectAnswersCount(Integer correctAnswersCount) {
        this.correctAnswersCount =
                correctAnswersCount == null ? 0 : correctAnswersCount;
    }

    public Integer getMistakesCount() {
        return mistakesCount == null ? 0 : mistakesCount;
    }

    public void setMistakesCount(Integer mistakesCount) {
        this.mistakesCount = mistakesCount == null ? 0 : mistakesCount;
    }

    public Boolean getIsLearned() {
        return isLearned != null && isLearned;
    }

    public void setIsLearned(Boolean learned) {
        isLearned = learned != null && learned;
    }

    public Boolean getPassedSprint() {
        return passedSprint != null && passedSprint;
    }

    public void setPassedSprint(Boolean value) {
        passedSprint = value != null && value;
    }

    public Boolean getPassedMatching() {
        return passedMatching != null && passedMatching;
    }

    public void setPassedMatching(Boolean value) {
        passedMatching = value != null && value;
    }

    public Boolean getPassedWriting() {
        return passedWriting != null && passedWriting;
    }

    public void setPassedWriting(Boolean value) {
        passedWriting = value != null && value;
    }
}