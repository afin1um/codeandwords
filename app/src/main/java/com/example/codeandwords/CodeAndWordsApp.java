package com.example.codeandwords;

import android.app.Application;

import com.example.codeandwords.ui.profile.ThemePrefs;

public class CodeAndWordsApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.applySavedTheme(this);
    }
}