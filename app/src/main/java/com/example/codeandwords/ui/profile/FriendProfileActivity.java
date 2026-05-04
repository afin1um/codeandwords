package com.example.codeandwords.ui.profile;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;

import java.util.Locale;

public class FriendProfileActivity extends AppCompatActivity {

    private AvatarPreviewView avatarPreview;
    private TextView tvUsername;
    private TextView tvEmail;
    private TextView tvLevel;
    private TextView tvXp;
    private TextView tvHandle;
    private TextView tvXpProgress;
    private ProgressBar progressXp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_profile);

        initViews();
        loadDataFromIntent();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBackFriendProfile);
        btnBack.setOnClickListener(v -> finish());

        avatarPreview = findViewById(R.id.friendAvatarPreview);
        tvUsername = findViewById(R.id.tvFriendProfileUsername);
        tvEmail = findViewById(R.id.tvFriendProfileEmail);
        tvLevel = findViewById(R.id.tvFriendProfileLevel);
        tvXp = findViewById(R.id.tvFriendProfileXp);
        tvHandle = findViewById(R.id.tvFriendProfileHandle);
        tvXpProgress = findViewById(R.id.tvFriendXpProgress);
        progressXp = findViewById(R.id.progressFriendXp);
    }

    private void loadDataFromIntent() {
        String username = getIntent().getStringExtra("username");
        String email = getIntent().getStringExtra("email");
        String avatarJson = getIntent().getStringExtra("avatar_config");

        int totalXp = getIntent().getIntExtra("total_xp", 0);
        int currentLevel = getIntent().getIntExtra("current_level", 1);

        if (username == null || username.trim().isEmpty()) {
            username = "Пользователь";
        }

        if (email == null) {
            email = "";
        }

        AvatarConfig avatarConfig = AvatarConfig.fromJson(avatarJson);
        avatarPreview.setAvatarConfig(avatarConfig);

        int xpInCurrentLevel = totalXp % 100;
        int xpToNextLevel = 100 - xpInCurrentLevel;

        progressXp.setMax(100);
        progressXp.setProgress(xpInCurrentLevel);

        tvUsername.setText(username);
        tvEmail.setText(email);
        tvHandle.setText("@" + username.toLowerCase(Locale.getDefault()));
        tvLevel.setText(String.valueOf(currentLevel));
        tvXp.setText(String.valueOf(totalXp));
        tvXpProgress.setText(xpInCurrentLevel + " / 100 XP • осталось " + xpToNextLevel + " XP");
    }
}