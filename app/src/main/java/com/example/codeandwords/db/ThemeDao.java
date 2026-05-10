package com.example.codeandwords.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.codeandwords.model.Theme;

import java.util.List;

@Dao
public interface ThemeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Theme> themes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Theme theme);

    @Query("SELECT * FROM themes ORDER BY id ASC")
    List<Theme> getAllThemes();

    @Query("SELECT * FROM themes WHERE id = :id LIMIT 1")
    Theme getThemeById(Long id);

    @Query("SELECT MAX(id) FROM themes")
    Long getMaxThemeId();

    @Query("DELETE FROM themes WHERE id = :id")
    void deleteThemeById(Long id);

    @Query("DELETE FROM themes")
    void deleteAll();
}