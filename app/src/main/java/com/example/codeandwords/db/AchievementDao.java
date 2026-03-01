package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.codeandwords.model.Achievement;
import com.example.codeandwords.model.AchievementWithProgress;
import com.example.codeandwords.model.UserAchievement;

import java.util.List;

@Dao
public interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Achievement> achievements);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUserAchievement(UserAchievement userAchievement);

    @Update
    void updateUserAchievement(UserAchievement userAchievement);

    @Query("SELECT * FROM achievements")
    List<Achievement> getAllAchievements();

    // ИСПРАВЛЕНО: Long изменен на Integer для соответствия модели User
    @Query("SELECT * FROM user_achievements WHERE userId = :userId AND achievementId = :achievementId LIMIT 1")
    UserAchievement getUserAchievement(Integer userId, Integer achievementId);

    // ИСПРАВЛЕНО: Long изменен на Integer
    @Query("SELECT COUNT(*) FROM user_achievements WHERE userId = :userId AND achievementId = :achievementId")
    int hasAchievement(Integer userId, Integer achievementId);

    // ИСПРАВЛЕНО: Long изменен на Integer.
    // Запрос сопоставляет достижения с прогрессом конкретного пользователя.
    @Query("SELECT a.*, ua.id AS userAchievementId, ua.currentProgress, ua.isNew, ua.dateReceived " +
            "FROM achievements a " +
            "LEFT JOIN user_achievements ua ON a.id = ua.achievementId AND ua.userId = :userId")
    List<AchievementWithProgress> getAchievementsWithProgress(Integer userId);
}