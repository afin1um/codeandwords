package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.UserWord;

import java.util.List;

@Dao
public interface UserWordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserWord word);

    @Query("SELECT * FROM user_personal_words WHERE userId = :userId ORDER BY dateAdded DESC")
    List<UserWord> getUserWords(Integer userId);

    @Delete
    void delete(UserWord word);

    @Query("DELETE FROM user_personal_words WHERE userId = :userId")
    void deleteAllUserWords(Integer userId);

    @Query("SELECT COUNT(*) FROM user_personal_words " +
            "WHERE userId = :userId " +
            "AND LOWER(word) = LOWER(:word) " +
            "AND LOWER(translation) = LOWER(:translation)")
    int countSameWord(Integer userId, String word, String translation);

    @Query("SELECT * FROM user_personal_words " +
            "WHERE userId = :userId AND LOWER(word) = LOWER(:word) " +
            "LIMIT 1")
    UserWord findWordByUserAndTerm(Integer userId, String word);
}