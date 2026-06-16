package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.Word;

import java.util.List;

@Dao
public interface WordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Word word);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Word> words);

    @Query("SELECT * FROM words WHERE theme_id = :themeId ORDER BY id ASC")
    List<Word> getWordsByTheme(Long themeId);

    @Query("SELECT * FROM words WHERE id = :wordId LIMIT 1")
    Word getWordById(Long wordId);

    // Пакетная выборка слов по списку ID для cache-first загрузки
    @Query("SELECT * FROM words WHERE id IN (:ids)")
    List<Word> getWordsByIds(List<Long> ids);

    @Query("SELECT * FROM words WHERE LOWER(term) = LOWER(:term) LIMIT 1")
    Word getWordByTerm(String term);

    @Query("SELECT * FROM words WHERE LOWER(term) = LOWER(:term) AND LOWER(translation) = LOWER(:translation) LIMIT 1")
    Word getWordByTermAndTranslation(String term, String translation);

    @Query("SELECT MAX(id) FROM words")
    Long getMaxWordId();

    @Query("DELETE FROM words WHERE theme_id = :themeId")
    void deleteWordsByThemeId(Long themeId);

    @Query("DELETE FROM words WHERE id = :wordId")
    void deleteWordById(Long wordId);

    @Query("DELETE FROM words")
    void deleteAll();

    // Возвращает только id слов и id их тем одним запросом.
    // Используется для быстрого расчёта прогресса по темам в StatsRepository.
    @Query("SELECT id AS wordId, theme_id AS themeId FROM words")
    List<ThemeWordIdPair> getAllThemeWordIdPairs();
}