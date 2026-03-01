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

    @Query("SELECT * FROM themes")
    List<Theme> getAllThemes();

    @Query("SELECT * FROM themes WHERE id = :id LIMIT 1")
    Theme getThemeById(Long id);

    // --- ДОБАВЛЕН НОВЫЙ МЕТОД ОЧИСТКИ ---
    @Query("DELETE FROM themes")
    void deleteAll();
}