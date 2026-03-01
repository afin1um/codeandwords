package com.example.codeandwords.ui.dashboard;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.codeandwords.R;
import com.example.codeandwords.db.PostgresHelper;
import com.example.codeandwords.ui.profile.LeaderboardFragment;
import com.example.codeandwords.ui.profile.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.sql.Connection;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Привязываем дизайн из файла activity_main.xml
        setContentView(R.layout.activity_main);

        // ВАЖНО: Мы удалили код ViewCompat.setOnApplyWindowInsetsListener,
        // из-за которого возникала ошибка "cannot find symbol variable main".
        // Теперь приложение будет искать только существующие кнопки.

        // 2. Находим нижнюю панель навигации (меню)
        BottomNavigationView navView = findViewById(R.id.nav_view);

        // 3. Настраиваем переключение между экранами при нажатии кнопок
        navView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_themes) {
                selectedFragment = new ThemesFragment();
            } else if (itemId == R.id.navigation_leaderboard) {
                selectedFragment = new LeaderboardFragment();
            } else if (itemId == R.id.navigation_profile) {
                selectedFragment = new ProfileFragment();
            }

            // Заменяем текущий экран на выбранный
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, selectedFragment)
                        .commit();
            }
            return true;
        });

        // 4. При первом запуске показываем экран с Темами (Обучение)
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, new ThemesFragment())
                    .commit();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Connection conn = PostgresHelper.connect();
                    if (conn != null) {
                        // Подключение есть, можно делать запросы
                        // conn.createStatement().execute("...");
                        conn.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}