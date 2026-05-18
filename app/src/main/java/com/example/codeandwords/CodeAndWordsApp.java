package com.example.codeandwords;

import android.app.Application;

import com.example.codeandwords.data.Repository;
import com.example.codeandwords.ui.profile.ThemePrefs;

public class CodeAndWordsApp extends Application {

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
    }
}