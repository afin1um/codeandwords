package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.TeamChallenge;

import java.util.List;

@Dao
public interface TeamChallengeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TeamChallenge challenge);

    @Query("SELECT * FROM team_challenges WHERE team_id = :teamId ORDER BY id DESC LIMIT 1")
    TeamChallenge getLatestByTeamId(int teamId);

    @Query("SELECT * FROM team_challenges WHERE team_id = :teamId ORDER BY id DESC")
    List<TeamChallenge> getAllByTeamId(int teamId);

    @Query("SELECT * FROM team_challenges WHERE id = :challengeId LIMIT 1")
    TeamChallenge getById(int challengeId);

    // ДОБАВЛЕНО: удалить все челленджи команды — ИСПРАВЛЕНИЕ ОШИБКИ
    @Query("DELETE FROM team_challenges WHERE team_id = :teamId")
    void deleteByTeamId(int teamId);

    // ДОБАВЛЕНО: удалить конкретный челлендж по id
    @Query("DELETE FROM team_challenges WHERE id = :challengeId")
    void deleteById(int challengeId);

    // ДОБАВЛЕНО: удалить все
    @Query("DELETE FROM team_challenges")
    void deleteAll();
}