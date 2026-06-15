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

// Репозиторий тем: cache-first загрузка с фоновым обновлением с сервера
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

    // Cache-first: сначала отдаёт локальные темы, затем обновляет с сервера
    public void getThemes(DataCallback<List<Theme>> callback) {
        executor.execute(() -> {
            boolean hadLocal = false;
            int localCount = 0;

            try {
                List<Theme> localThemes = themeDao.getAllThemes();

                if (localThemes != null && !localThemes.isEmpty()) {
                    hadLocal = true;
                    localCount = localThemes.size();
                    mainHandler.post(() -> callback.onSuccess(localThemes));
                }

                refreshThemesFromServer(hadLocal, localCount, callback);

            } catch (Exception e) {
                Log.e("ThemeRepository",
                        "Ошибка локальной загрузки тем: " + e.getMessage(), e);
                refreshThemesFromServer(false, 0, callback);
            }
        });
    }

    // Загружает темы с сервера, заменяет локальный кэш и уведомляет UI
    private void refreshThemesFromServer(boolean hadLocal,
                                         int localCount,
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

                        // Если локально уже отдавали столько же тем — повторно не дёргаем UI
                        if (!hadLocal || remoteThemes.size() != localCount) {
                            mainHandler.post(() -> callback.onSuccess(remoteThemes));
                        }
                    });
                } else {
                    Log.e("ThemeRepository",
                            "Supabase не загрузил themes: "
                                    + response.code() + " | " + getErrorBody(response));

                    if (!hadLocal) {
                        mainHandler.post(() -> callback.onError("Темы не найдены"));
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Theme>> call, Throwable t) {
                Log.e("ThemeRepository", "Ошибка сети getThemes: " + t.getMessage(), t);

                if (!hadLocal) {
                    mainHandler.post(() -> callback.onError(
                            "Ошибка загрузки тем: " + t.getMessage()));
                }
            }
        });
    }

    // Возвращает темы только из локальной БД без сетевого запроса
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

    // Возвращает тему по ID из локальной БД
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

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}