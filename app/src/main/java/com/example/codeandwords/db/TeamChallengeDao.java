package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.TeamChallenge;

@Dao
public interface TeamChallengeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TeamChallenge challenge);

    @Query("SELECT * FROM team_challenges WHERE team_id = :teamId ORDER BY id DESC LIMIT 1")
    TeamChallenge getActiveByTeamId(int teamId);
}