package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.TeamMember;

import java.util.List;

@Dao
public interface TeamMemberDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TeamMember member);

    @Query("SELECT * FROM team_members WHERE team_id = :teamId")
    List<TeamMember> getByTeamId(int teamId);

    @Query("SELECT * FROM team_members WHERE user_id = :userId")
    List<TeamMember> getByUserId(int userId);

    // ДОБАВЛЕНО: удалить конкретного участника из конкретной команды
    @Query("DELETE FROM team_members WHERE team_id = :teamId AND user_id = :userId")
    void deleteByTeamAndUser(int teamId, int userId);

    // ДОБАВЛЕНО: удалить всех участников команды
    @Query("DELETE FROM team_members WHERE team_id = :teamId")
    void deleteByTeamId(int teamId);

    // ДОБАВЛЕНО: удалить все записи пользователя
    @Query("DELETE FROM team_members WHERE user_id = :userId")
    void deleteByUserId(int userId);
}