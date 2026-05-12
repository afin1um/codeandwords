package com.example.codeandwords.data.theme;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.ThemeDao;
import com.example.codeandwords.model.Theme;

import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ThemeRepository {

    private final ThemeDao themeDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public ThemeRepository(ThemeDao themeDao,
                           ApiService apiService,
                           ExecutorService executor,
                           Handler mainHandler) {
        this.themeDao = themeDao;
        this.apiService = apiService;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    public void getThemes(DataCallback<List<Theme>> callback) {
        // 1. СНАЧАЛА быстро отдаём локальные данные
        executor.execute(() -> {
            try {
                List<Theme> localThemes = themeDao.getAllThemes();

                if (localThemes != null && !localThemes.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(localThemes));
                }

                // 2. ПОТОМ в фоне обновляем с сервера
                refreshThemesFromServer(localThemes, callback);

            } catch (Exception e) {
                Log.e("ThemeRepository",
                        "Ошибка локальной загрузки тем: " + e.getMessage(), e);
                // Если локальные данные не загрузились — пробуем сервер
                refreshThemesFromServer(null, callback);
            }
        });
    }

    private void refreshThemesFromServer(List<Theme> localThemes,
                                         DataCallback<List<Theme>> callback) {
        apiService.getThemes("*", "id.asc").enqueue(new Callback<List<Theme>>() {
            @Override
            public void onResponse(Call<List<Theme>> call, Response<List<Theme>> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {

                    List<Theme> remoteThemes = response.body();

                    executor.execute(() -> {
                        try {
                            themeDao.deleteAll();
                            themeDao.insertAll(remoteThemes);
                        } catch (Exception e) {
                            Log.e("ThemeRepository",
                                    "Ошибка сохранения тем локально: " + e.getMessage(), e);
                        }

                        // Обновляем UI только если данные с сервера отличаются
                        // или локальных данных не было
                        if (localThemes == null || localThemes.isEmpty()) {
                            mainHandler.post(() -> callback.onSuccess(remoteThemes));
                        } else {
                            // Тихо обновляем локальные — UI уже показал данные
                            mainHandler.post(() -> callback.onSuccess(remoteThemes));
                        }
                    });
                } else {
                    Log.e("ThemeRepository",
                            "Supabase не загрузил themes: "
                                    + response.code() + " | " + getErrorBody(response));

                    // Если локальных данных не было — отдаём ошибку
                    if (localThemes == null || localThemes.isEmpty()) {
                        mainHandler.post(() -> callback.onError("Темы не найдены"));
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Theme>> call, Throwable t) {
                Log.e("ThemeRepository", "Ошибка сети getThemes: " + t.getMessage(), t);

                // Если локальных данных не было — отдаём ошибку
                if (localThemes == null || localThemes.isEmpty()) {
                    mainHandler.post(() -> callback.onError(
                            "Ошибка загрузки тем: " + t.getMessage()));
                }
            }
        });
    }

    public void loadThemesLocal(DataCallback<List<Theme>> callback) {
        executor.execute(() -> {
            try {
                List<Theme> themes = themeDao.getAllThemes();

                if (themes != null && !themes.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(themes));
                } else {
                    mainHandler.post(() -> callback.onError("Темы не найдены"));
                }
            } catch (Exception e) {
                Log.e("ThemeRepository",
                        "Ошибка локальной загрузки тем: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки тем"));
            }
        });
    }

    public void getThemeById(long themeId, DataCallback<Theme> callback) {
        executor.execute(() -> {
            try {
                Theme theme = themeDao.getThemeById(themeId);
                mainHandler.post(() -> {
                    if (theme != null) {
                        callback.onSuccess(theme);
                    } else {
                        callback.onError("Тема не найдена");
                    }
                });
            } catch (Exception e) {
                Log.e("ThemeRepository",
                        "Ошибка загрузки темы: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки темы"));
            }
        });
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e("ThemeRepository",
                    "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // ===== ИНТЕРФЕЙС CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}