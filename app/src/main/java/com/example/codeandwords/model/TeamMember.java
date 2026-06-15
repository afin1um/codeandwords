package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "team_members")
public class TeamMember {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    @ColumnInfo(name = "id")
    public int id;

    @SerializedName("team_id")
    @ColumnInfo(name = "team_id")
    public int teamId;

    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    public int userId;

    // На сервере поле называется joined_at; маппинг происходит в parseTeamMemberFromJson
    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    public String createdAt;

    public TeamMember() {
    }

    public TeamMember(int teamId, int userId) {
        this.teamId = teamId;
        this.userId = userId;
    }
}