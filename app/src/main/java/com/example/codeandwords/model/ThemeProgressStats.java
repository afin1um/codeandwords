package com.example.codeandwords.model;

public class ThemeProgressStats {

    private Theme theme;
    private int learnedWords;
    private int totalWords;
    private int progressPercent;
    private boolean mastered;

    public ThemeProgressStats() {
    }

    public ThemeProgressStats(Theme theme,
                              int learnedWords,
                              int totalWords,
                              int progressPercent,
                              boolean mastered) {
        this.theme = theme;
        this.learnedWords = learnedWords;
        this.totalWords = totalWords;
        this.progressPercent = progressPercent;
        this.mastered = mastered;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public int getLearnedWords() {
        return learnedWords;
    }

    public void setLearnedWords(int learnedWords) {
        this.learnedWords = learnedWords;
    }

    public int getTotalWords() {
        return totalWords;
    }

    public void setTotalWords(int totalWords) {
        this.totalWords = totalWords;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public boolean isMastered() {
        return mastered;
    }

    public void setMastered(boolean mastered) {
        this.mastered = mastered;
    }
}