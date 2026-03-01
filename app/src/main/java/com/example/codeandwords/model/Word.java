package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

@Entity(tableName = "words")
public class Word implements Serializable {

    @PrimaryKey(autoGenerate = false)
    @SerializedName("id")
    private Long id;

    @ColumnInfo(name = "theme_id")
    @SerializedName("theme_id")
    private Long themeId;

    @ColumnInfo(name = "term")
    @SerializedName("term")
    private String term;

    @ColumnInfo(name = "translation")
    @SerializedName("translation")
    private String translation;

    @ColumnInfo(name = "definition")
    @SerializedName("definition")
    private String definition;

    @ColumnInfo(name = "transcription")
    @SerializedName("transcription")
    private String transcription;

    @ColumnInfo(name = "example_sentence")
    @SerializedName("example_sentence")
    private String exampleSentence;

    // --- Конструкторы ---

    public Word() {
    }

    public Word(Long id, Long themeId, String term, String translation,
                String definition, String transcription, String exampleSentence) {
        this.id = id;
        this.themeId = themeId;
        this.term = term;
        this.translation = translation;
        this.definition = definition;
        this.transcription = transcription;
        this.exampleSentence = exampleSentence;
    }

    // --- Геттеры и сеттеры ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public String getTranslation() { return translation; }
    public void setTranslation(String translation) { this.translation = translation; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    public String getTranscription() { return transcription; }
    public void setTranscription(String transcription) { this.transcription = transcription; }

    public String getExampleSentence() { return exampleSentence; }
    public void setExampleSentence(String exampleSentence) { this.exampleSentence = exampleSentence; }

    @Override
    public String toString() {
        return "Word{" + "term='" + term + '\'' + '}';
    }
}