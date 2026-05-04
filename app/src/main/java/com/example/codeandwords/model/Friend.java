package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "friends")
public class Friend {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    public int id;

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