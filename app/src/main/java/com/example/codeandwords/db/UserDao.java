package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.User;

import java.util.List;

@Dao
public interface UserDao {

    // Регистрация: вставка пользователя
    @Insert(onConflict = OnConflictStrategy.ABORT) // Выдаст ошибку, если что-то не так
    long insertUser(User user);

    // Вход: поиск пользователя по email и паролю
    @Query("SELECT * FROM users WHERE email = :email AND password_hash = :password LIMIT 1")
    User login(String email, String password);

    // Проверка при регистрации: занят ли email?
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User checkUserByEmail(String email);

    // Обновление прогресса (XP и уровень)
    // Используем total_xp и current_level, как они названы в таблице БД
    @Query("UPDATE users SET total_xp = :xp, current_level = :level WHERE id = :id")
    void updateProgress(Integer id, int xp, int level);

    // --- ИСПРАВЛЕНО: Изменено totalXp на total_xp ---
    @Query("SELECT * FROM users ORDER BY total_xp DESC LIMIT 50")
    List<User> getLeaderboard();


}