package com.example.codeandwords.db;

// Лёгкая пара (themeId, wordId) для быстрого расчёта прогресса по темам.
// Берётся одним SQL-запросом из таблицы words без загрузки лишних полей.
public class ThemeWordIdPair {
    public Long themeId;
    public Long wordId;
}