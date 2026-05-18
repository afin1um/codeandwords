package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.UserStats;

@Dao
public interface UserStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(UserStats stats);

    @Query("SELECT * FROM user_stats WHERE user_id = :userId LIMIT 1")
    UserStats getByUserId(int userId);

    // Быстрые точечные апдейты — без чтения всей строки
    @Query("UPDATE user_stats SET current_day_xp = :xp, updated_at = :updatedAt WHERE user_id = :userId")
    void updateDailyXp(int userId, int xp, long updatedAt);

    @Query("UPDATE user_stats SET login_streak = :streak, last_login_day = :day, updated_at = :updatedAt WHERE user_id = :userId")
    void updateLoginStreak(int userId, int streak, long day, long updatedAt);
}