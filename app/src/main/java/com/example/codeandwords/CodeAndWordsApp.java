package com.example.codeandwords;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.codeandwords.api.RetrofitClient;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.ui.profile.ThemePrefs;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CodeAndWordsApp extends Application {

    private static final String TAG = "AppWarmup";

    @Override
    public void onCreate() {
        super.onCreate();

        // Применяем сохранённую тему
        ThemePrefs.applySavedTheme(this);

        // Прогреваем Repository заранее в фоновом потоке,
        // чтобы первый вход/login был быстрее
        new Thread(() -> {
            try {
                Repository.getInstance(getApplicationContext());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Прогрев HTTPS-соединений с Supabase.
        // Первый запрос всегда долгий из-за TLS handshake — делаем его пораньше,
        // пока пользователь смотрит на splash или авторизуется
        warmupConnection();
    }

    // Лёгкие запросы для установки TCP+TLS соединений заранее в обоих клиентах
    private void warmupConnection() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                // Прогрев основного клиента (getApiService)
                RetrofitClient.getApiService()
                        .getThemes("id", "id.asc")
                        .enqueue(new Callback<List<Theme>>() {
                            @Override
                            public void onResponse(Call<List<Theme>> call,
                                                   Response<List<Theme>> response) {
                                Log.d(TAG, "Основной клиент прогрет: код "
                                        + response.code());
                            }

                            @Override
                            public void onFailure(Call<List<Theme>> call, Throwable t) {
                                // Не критично — даже неудачный warmup устанавливает соединение
                                Log.w(TAG, "Warmup основного клиента не удался: "
                                        + t.getMessage());
                            }
                        });

                // Прогрев быстрого клиента (getFastApiService)
                RetrofitClient.getFastApiService()
                        .getThemes("id", "id.asc")
                        .enqueue(new Callback<List<Theme>>() {
                            @Override
                            public void onResponse(Call<List<Theme>> call,
                                                   Response<List<Theme>> response) {
                                Log.d(TAG, "Быстрый клиент прогрет: код "
                                        + response.code());
                            }

                            @Override
                            public void onFailure(Call<List<Theme>> call, Throwable t) {
                                Log.w(TAG, "Warmup быстрого клиента не удался: "
                                        + t.getMessage());
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Ошибка warmup: " + e.getMessage(), e);
            }
        }, 300);
    }
}