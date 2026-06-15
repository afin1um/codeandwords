package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.User;

import java.util.List;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertUser(User user);

    @Query("SELECT * FROM users WHERE email = :email AND password_hash = :password LIMIT 1")
    User login(String email, String password);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User checkUserByEmail(String email);

    // Обновляет XP и уровень пользователя без полной перезаписи строки
    @Query("UPDATE users SET total_xp = :xp, current_level = :level WHERE id = :id")
    void updateProgress(Integer id, int xp, int level);

    // Возвращает топ-50 пользователей по XP для таблицы лидеров
    @Query("SELECT * FROM users ORDER BY total_xp DESC LIMIT 50")
    List<User> getLeaderboard();

    @Query("DELETE FROM users")
    void deleteAllUsers();

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    User getByUsername(String username);

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    User getUserByUsername(String username);
}