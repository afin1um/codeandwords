package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.TeamChallengeProgress;

import java.util.List;

@Dao
public interface TeamChallengeProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TeamChallengeProgress progress);

    @Query("SELECT * FROM team_challenge_progress WHERE challenge_id = :challengeId ORDER BY progress DESC")
    List<TeamChallengeProgress> getByChallengeId(int challengeId);
}