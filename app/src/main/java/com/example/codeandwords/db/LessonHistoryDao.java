package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;

import com.example.codeandwords.model.LessonHistory;

@Dao
public interface LessonHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LessonHistory history);
}