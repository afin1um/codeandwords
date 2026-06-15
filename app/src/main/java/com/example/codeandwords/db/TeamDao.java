package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.Team;

import java.util.List;

@Dao
public interface TeamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Team team);

    @Query("SELECT * FROM teams WHERE id = :teamId LIMIT 1")
    Team getById(int teamId);

    @Query("SELECT * FROM teams WHERE owner_id = :userId ORDER BY id DESC")
    List<Team> getOwnedTeams(int userId);

    @Query("SELECT * FROM teams ORDER BY id DESC")
    List<Team> getAll();

    @Query("DELETE FROM teams WHERE id = :teamId")
    void deleteById(int teamId);

    @Query("DELETE FROM teams WHERE owner_id = :userId")
    void deleteAllTeamsByUser(int userId);

    @Query("DELETE FROM team_members WHERE user_id = :userId")
    void deleteAllMembersByUser(int userId);
}