package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.codeandwords.model.DailyQuest;

import java.util.List;

@Dao
public interface DailyQuestDao {
    @Insert
    void insertAll(List<DailyQuest> quests);

    @Query("SELECT * FROM daily_quests")
    List<DailyQuest> getAllQuests();

    @Query("DELETE FROM daily_quests")
    void deleteAll();

    @Update
    void updateQuest(DailyQuest quest);
}