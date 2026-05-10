package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.LessonHistory;

import java.util.List;

@Dao
public interface LessonHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LessonHistory history);

    @Query("SELECT * FROM lesson_history WHERE user_id = :userId ORDER BY finished_at DESC")
    List<LessonHistory> getAllByUser(int userId);

    @Query("SELECT * FROM lesson_history WHERE user_id = :userId ORDER BY finished_at DESC LIMIT :limit")
    List<LessonHistory> getRecentByUser(int userId, int limit);

    @Query("SELECT * FROM lesson_history WHERE user_id = :userId AND finished_at >= :startTime ORDER BY finished_at ASC")
    List<LessonHistory> getFromDate(int userId, long startTime);

    @Query("DELETE FROM lesson_history WHERE user_id = :userId")
    void deleteByUser(int userId);
}