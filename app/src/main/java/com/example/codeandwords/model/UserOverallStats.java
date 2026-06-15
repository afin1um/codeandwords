package com.example.codeandwords.model;

// Агрегированная статистика пользователя для экрана "Моя статистика"
public class UserOverallStats {

    private int totalLessons;
    private int totalWords;
    private int totalMistakes;
    private int fixedErrors;
    private int accuracyPercent;
    private int learnedWords;
    private int totalXp;
    private int currentLevel;
    private String leagueTitle;
    private String leagueIcon;

    public UserOverallStats() {
    }

    public UserOverallStats(int totalLessons, int totalWords, int totalMistakes,
                            int fixedErrors, int accuracyPercent, int learnedWords,
                            int totalXp, int currentLevel,
                            String leagueTitle, String leagueIcon) {
        this.totalLessons = totalLessons;
        this.totalWords = totalWords;
        this.totalMistakes = totalMistakes;
        this.fixedErrors = fixedErrors;
        this.accuracyPercent = accuracyPercent;
        this.learnedWords = learnedWords;
        this.totalXp = totalXp;
        this.currentLevel = currentLevel;
        this.leagueTitle = leagueTitle;
        this.leagueIcon = leagueIcon;
    }

    public int getTotalLessons() { return totalLessons; }
    public void setTotalLessons(int totalLessons) { this.totalLessons = totalLessons; }
    public int getTotalWords() { return totalWords; }
    public void setTotalWords(int totalWords) { this.totalWords = totalWords; }
    public int getTotalMistakes() { return totalMistakes; }
    public void setTotalMistakes(int totalMistakes) { this.totalMistakes = totalMistakes; }
    public int getFixedErrors() { return fixedErrors; }
    public void setFixedErrors(int fixedErrors) { this.fixedErrors = fixedErrors; }
    public int getAccuracyPercent() { return accuracyPercent; }
    public void setAccuracyPercent(int accuracyPercent) { this.accuracyPercent = accuracyPercent; }
    public int getLearnedWords() { return learnedWords; }
    public void setLearnedWords(int learnedWords) { this.learnedWords = learnedWords; }
    public int getTotalXp() { return totalXp; }
    public void setTotalXp(int totalXp) { this.totalXp = totalXp; }
    public int getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(int currentLevel) { this.currentLevel = currentLevel; }
    public String getLeagueTitle() { return leagueTitle; }
    public void setLeagueTitle(String leagueTitle) { this.leagueTitle = leagueTitle; }
    public String getLeagueIcon() { return leagueIcon; }
    public void setLeagueIcon(String leagueIcon) { this.leagueIcon = leagueIcon; }
}