package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.codeandwords.model.DailyQuest;

import java.util.List;

@Dao
public interface DailyQuestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<DailyQuest> quests);

    @Query("SELECT * FROM daily_quests ORDER BY id ASC")
    List<DailyQuest> getAllQuests();

    @Query("DELETE FROM daily_quests")
    void deleteAll();

    @Update
    void updateQuest(DailyQuest quest);

    // Атомарная замена всех квестов в одной транзакции
    @Transaction
    default void replaceAll(List<DailyQuest> newQuests) {
        deleteAll();
        if (newQuests != null && !newQuests.isEmpty()) {
            insertAll(newQuests);
        }
    }

    // Возвращает активные квесты заданного типа для обновления прогресса
    @Query("SELECT * FROM daily_quests WHERE type = :type AND isCompleted = 0")
    List<DailyQuest> getActiveQuestsByType(String type);
}