package com.example.codeandwords.ui.dashboard;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.codeandwords.R;
import com.example.codeandwords.ui.profile.LeaderboardFragment;
import com.example.codeandwords.ui.profile.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView navView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupBottomNavigation();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, new ThemesFragment())
                    .commit();
        }
    }

    private void initViews() {
        navView = findViewById(R.id.nav_view);
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
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, selectedFragment)
                        .commit();
                return true;
            }

            return false;
        });
    }
}