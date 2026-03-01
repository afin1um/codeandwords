    package com.example.codeandwords.db;

    import androidx.room.Dao;
    import androidx.room.Insert;
    import androidx.room.OnConflictStrategy;
    import androidx.room.Query;

    import com.example.codeandwords.model.Word;

    import java.util.List;

    @Dao
    public interface WordDao {

        // Сохранить список слов
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void insertAll(List<Word> words);

        // Получить все слова для конкретной темы
        @Query("SELECT * FROM words WHERE theme_id = :themeId")
        List<Word> getWordsByTheme(Long themeId);

        // Получить одно слово по ID
        @Query("SELECT * FROM words WHERE id = :wordId")
        Word getWordById(Long wordId);

        // Удалить все слова (для очистки кеша)
        @Query("DELETE FROM words")
        void deleteAll();
    }