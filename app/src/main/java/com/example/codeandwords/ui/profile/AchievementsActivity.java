package com.example.codeandwords.ui.profile;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.example.codeandwords.R;

public class AchievementsActivity extends AppCompatActivity {

    private static final String FRAGMENT_TAG = "ACHIEVEMENTS_FRAGMENT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievements);

        if (savedInstanceState == null) {
            openAchievementsFragment();
        }
    }

    private void openAchievementsFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(
                R.id.achievementsFragmentContainer,
                new AchievementsFragment(),
                FRAGMENT_TAG
        );
        transaction.commit();
    }
}