package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.codeandwords.model.UserWord;

import java.util.List;

@Dao
public interface UserWordDao {

    // Возвращает локальный ID вставленной записи для последующего сохранения serverId
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(UserWord word);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<UserWord> words);

    @Query("SELECT * FROM user_personal_words WHERE userId = :userId ORDER BY dateAdded DESC")
    List<UserWord> getUserWords(Integer userId);

    @Query("SELECT * FROM user_personal_words WHERE userId = :userId AND themeId = :themeId ORDER BY dateAdded DESC")
    List<UserWord> getUserWordsByThemeId(Integer userId, Long themeId);

    // Слова без привязки к теме (для восстановления тем)
    @Query("SELECT * FROM user_personal_words " +
            "WHERE userId = :userId AND (themeId IS NULL OR themeTitle IS NULL OR themeTitle = 'Без темы') " +
            "ORDER BY dateAdded DESC")
    List<UserWord> getUserWordsWithoutTheme(Integer userId);

    @Query("SELECT DISTINCT themeTitle FROM user_personal_words WHERE userId = :userId AND themeTitle IS NOT NULL")
    List<String> getUserDictionaryThemeTitles(Integer userId);

    @Delete
    void delete(UserWord word);

    @Query("DELETE FROM user_personal_words WHERE userId = :userId")
    void deleteAllUserWords(Integer userId);

    @Query("DELETE FROM user_personal_words WHERE userId = :userId")
    void deleteAllUserWordsForUser(Integer userId);

    @Query("DELETE FROM user_personal_words WHERE themeId = :themeId")
    void deleteUserWordsByThemeId(Long themeId);

    // Удаление по термину с учётом userId — защита от удаления слов других пользователей
    @Query("DELETE FROM user_personal_words WHERE userId = :userId AND LOWER(word) = LOWER(:term)")
    void deleteUserWordsByTerm(Integer userId, String term);

    @Query("DELETE FROM user_personal_words WHERE userId = :userId AND serverId = :serverId")
    void deleteUserWordByServerId(Integer userId, Long serverId);

    @Query("SELECT COUNT(*) FROM user_personal_words " +
            "WHERE userId = :userId " +
            "AND LOWER(word) = LOWER(:word) " +
            "AND LOWER(translation) = LOWER(:translation)")
    int countSameWord(Integer userId, String word, String translation);

    @Query("SELECT * FROM user_personal_words " +
            "WHERE userId = :userId AND LOWER(word) = LOWER(:word) " +
            "LIMIT 1")
    UserWord findWordByUserAndTerm(Integer userId, String word);

    @Query("SELECT * FROM user_personal_words " +
            "WHERE userId = :userId AND serverId = :serverId " +
            "LIMIT 1")
    UserWord findUserWordByServerId(Integer userId, Long serverId);

    @Query("SELECT * FROM user_personal_words " +
            "WHERE id = :localId " +
            "LIMIT 1")
    UserWord findUserWordByLocalId(Long localId);

    @Query("UPDATE user_personal_words SET themeId = :themeId, themeTitle = :themeTitle WHERE id = :userWordId")
    void updateUserWordTheme(Long userWordId, Long themeId, String themeTitle);

    @Query("SELECT * FROM user_personal_words WHERE userId = :userId AND isSynced = 0")
    List<UserWord> getUnsyncedUserWords(Integer userId);

    @Query("UPDATE user_personal_words SET isSynced = :isSynced WHERE id = :localId")
    void updateUserWordIsSynced(Long localId, boolean isSynced);

    // Обновляет serverId и помечает запись как синхронизированную
    @Query("UPDATE user_personal_words SET serverId = :serverId, isSynced = 1 WHERE id = :localId")
    void updateUserWordServerId(Long localId, Long serverId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(UserWord userWord);
}