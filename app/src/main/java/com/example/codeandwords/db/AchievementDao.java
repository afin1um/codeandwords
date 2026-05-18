package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.codeandwords.model.Achievement;
import com.example.codeandwords.model.AchievementWithProgress;
import com.example.codeandwords.model.UserAchievement;

import java.util.List;

@Dao
public interface AchievementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Achievement> achievements);

    @Query("SELECT * FROM achievements ORDER BY id ASC")
    List<Achievement> getAllAchievements();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertUserAchievement(UserAchievement userAchievement);

    @Update
    void updateUserAchievement(UserAchievement userAchievement);

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId AND achievement_id = :achievementId LIMIT 1")
    UserAchievement getUserAchievement(int userId, int achievementId);

    @Query("SELECT COUNT(*) FROM user_achievements WHERE user_id = :userId AND achievement_id = :achievementId AND is_unlocked = 1")
    int hasAchievement(int userId, int achievementId);

    @Transaction
    @Query("SELECT " +
            "a.id AS id, " +
            "a.title AS title, " +
            "a.description AS description, " +
            "a.xp_reward AS xp_reward, " +
            "a.condition_type AS condition_type, " +
            "a.condition_value AS condition_value, " +
            "a.max_progress AS max_progress, " +
            "a.icon_res_id AS icon_res_id, " +
            "COALESCE(ua.current_progress, 0) AS current_progress, " +
            "COALESCE(ua.received_at, 0) AS date_received, " +
            "COALESCE(ua.is_unlocked, 0) AS is_unlocked, " +
            "COALESCE(ua.is_new, 0) AS is_new " +
            "FROM achievements a " +
            "LEFT JOIN user_achievements ua " +
            "ON ua.achievement_id = a.id AND ua.user_id = :userId " +
            "ORDER BY a.id ASC")
    List<AchievementWithProgress> getAchievementsWithProgress(int userId);

    @Query("DELETE FROM achievements")
    void deleteAll();
}