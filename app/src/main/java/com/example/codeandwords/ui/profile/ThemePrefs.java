package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemePrefs {

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    public static void saveThemeMode(Context context, int mode) {

        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putInt(KEY_THEME_MODE, mode)
                .apply();

        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static int getSavedThemeMode(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        return prefs.getInt(
                KEY_THEME_MODE,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        );
    }

    public static void applySavedTheme(Context context) {

        int mode = getSavedThemeMode(context);

        AppCompatDelegate.setDefaultNightMode(mode);
    }
}