package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.codeandwords.model.StudySchedule;

import java.util.List;

@Dao
public interface StudyScheduleDao {

    // Локальное создание: ABORT чтобы поймать конфликт ID при ручном insert
    @Insert
    long insert(StudySchedule schedule);

    // Серверная синхронизация: REPLACE для безопасной перезаписи существующих записей
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(StudySchedule schedule);

    // Пакетная вставка с заменой для серверной синхронизации
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAllOrReplace(List<StudySchedule> schedules);

    @Update
    void update(StudySchedule schedule);

    @Delete
    void delete(StudySchedule schedule);

    @Query("SELECT * FROM study_schedule WHERE userId = :userId ORDER BY scheduleDate ASC, startTime ASC")
    List<StudySchedule> getByUser(int userId);

    @Query("SELECT * FROM study_schedule WHERE userId = :userId AND scheduleDate = :date ORDER BY startTime ASC")
    List<StudySchedule> getByDate(int userId, String date);

    @Query("SELECT * FROM study_schedule WHERE userId = :userId AND scheduleDate BETWEEN :startDate AND :endDate ORDER BY scheduleDate ASC, startTime ASC")
    List<StudySchedule> getByDateRange(int userId, String startDate, String endDate);

    @Query("SELECT * FROM study_schedule WHERE id = :id LIMIT 1")
    StudySchedule getById(int id);

    @Query("DELETE FROM study_schedule WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM study_schedule WHERE userId = :userId")
    void deleteAllByUser(int userId);
}