package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

import com.google.gson.annotations.SerializedName;

/**
 * ✅ ИСПРАВЛЕНО: композитный первичный ключ (user_id, friend_id)
 * вместо autoGenerate id.
 *
 * Проблема была в том, что autoGenerate генерировал id=0 для каждой
 * новой записи, и Room с OnConflictStrategy.REPLACE перезаписывал
 * предыдущую запись.
 */
@Entity(
        tableName = "friends",
        primaryKeys = {"user_id", "friend_id"},
        indices = {
                @Index(value = "user_id"),
                @Index(value = "friend_id")
        }
)
public class Friend {

    @ColumnInfo(name = "user_id")
    @SerializedName("user_id")
    public int userId;

    @ColumnInfo(name = "friend_id")
    @SerializedName("friend_id")
    public int friendId;

    public Friend(int userId, int friendId) {
        this.userId = userId;
        this.friendId = friendId;
    }
}