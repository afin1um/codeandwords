package com.example.codeandwords.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.ui.profile.LeaderboardFragment;
import com.example.codeandwords.ui.profile.ProfileFragment;
import com.example.codeandwords.ui.profile.ThemePrefs;
import com.google.android.material.bottomnavigation.BottomNavigationView;

// Главная активность: нижняя навигация, обработка deep-link через extra и фоновая синхронизация
public class MainActivity extends AppCompatActivity {

    // Ключи для открытия конкретного фрагмента через Intent extra
    public static final String EXTRA_OPEN_FRAGMENT = "OPEN_FRAGMENT";
    public static final String FRAGMENT_THEMES = "themes";
    public static final String FRAGMENT_TRAINING = "training";
    public static final String FRAGMENT_LEADERBOARD = "leaderboard";
    public static final String FRAGMENT_PROFILE = "profile";

    private BottomNavigationView navView;
    private Repository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePrefs.applySavedTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        setupBottomNavigation();

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }

        triggerBackgroundSync();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    // Открывает нужный фрагмент по extra или ThemesFragment по умолчанию
    private void handleIntent(Intent intent) {
        String fragmentToOpen = null;
        if (intent != null) {
            fragmentToOpen = intent.getStringExtra(EXTRA_OPEN_FRAGMENT);
        }

        if (FRAGMENT_TRAINING.equals(fragmentToOpen)) {
            navView.setSelectedItemId(R.id.navigation_training);
        } else if (FRAGMENT_LEADERBOARD.equals(fragmentToOpen)) {
            navView.setSelectedItemId(R.id.navigation_leaderboard);
        } else if (FRAGMENT_PROFILE.equals(fragmentToOpen)) {
            navView.setSelectedItemId(R.id.navigation_profile);
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, new ThemesFragment())
                    .commit();
            navView.setSelectedItemId(R.id.navigation_themes);
        }
    }

    private void initViews() {
        navView = findViewById(R.id.nav_view);
    }

    // Запускает фоновую синхронизацию данных в отдельном потоке
    private void triggerBackgroundSync() {
        new Thread(() -> {
            try {
                Log.d("MainActivity", "Запуск фоновой синхронизации");
                repository.syncAllDataFromServer();
            } catch (Exception e) {
                Log.e("MainActivity", "Ошибка фоновой синхронизации: " + e.getMessage(), e);
            }
        }).start();
    }

    private void setupBottomNavigation() {
        navView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_themes) {
                selectedFragment = new ThemesFragment();
            } else if (itemId == R.id.navigation_training) {
                selectedFragment = new TrainingFragment();
            } else if (itemId == R.id.navigation_leaderboard) {
                selectedFragment = new LeaderboardFragment();
            } else if (itemId == R.id.navigation_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.nav_host_fragment, selectedFragment)
                        .commit();
                return true;
            }

            return false;
        });
    }
}