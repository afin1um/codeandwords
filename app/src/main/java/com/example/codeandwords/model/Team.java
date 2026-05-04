package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "teams")
public class Team {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    @ColumnInfo(name = "id")
    public int id;

    @SerializedName("team_name")
    @ColumnInfo(name = "team_name")
    public String teamName;

    @SerializedName("owner_id")
    @ColumnInfo(name = "owner_id")
    public int ownerId;

    public Team() {
    }

    public Team(String teamName, int ownerId) {
        this.teamName = teamName;
        this.ownerId = ownerId;
    }
}