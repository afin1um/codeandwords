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
}