package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.Friend;
import com.example.codeandwords.model.User;

import java.util.List;

@Dao
public interface FriendDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Friend friend);

    // Возвращает объекты User через JOIN с таблицей friends
    @Query("SELECT users.* FROM users " +
            "INNER JOIN friends ON users.id = friends.friend_id " +
            "WHERE friends.user_id = :userId")
    List<User> getFriends(int userId);

    @Query("SELECT COUNT(*) FROM friends WHERE user_id = :userId AND friend_id = :friendId")
    int isFriendExists(int userId, int friendId);

    // Полная замена списка друзей при синхронизации с сервером
    @Query("DELETE FROM friends WHERE user_id = :userId")
    void deleteFriendsByUser(int userId);
}