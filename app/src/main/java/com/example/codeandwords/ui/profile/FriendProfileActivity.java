package com.example.codeandwords.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.google.android.material.button.MaterialButton;

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
    private MaterialButton btnAddFriend;

    private Repository repository;
    private int userId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_profile);

        repository = new Repository(this);

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
        btnAddFriend = findViewById(R.id.btnAddFriend);
    }

    private void loadDataFromIntent() {
        userId = getIntent().getIntExtra("user_id", -1);
        String username = getIntent().getStringExtra("username");
        String email = getIntent().getStringExtra("email");
        String avatarJson = getIntent().getStringExtra("avatar_config");
        boolean showAddFriendButton = getIntent().getBooleanExtra("show_add_friend_button", false);

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

        if (showAddFriendButton && userId > 0) {
            btnAddFriend.setVisibility(View.VISIBLE);
            btnAddFriend.setOnClickListener(v -> addFriend());
        } else {
            btnAddFriend.setVisibility(View.GONE);
        }
    }

    private void addFriend() {
        if (userId <= 0) {
            Toast.makeText(this, "Не удалось определить пользователя", Toast.LENGTH_SHORT).show();
            return;
        }

        btnAddFriend.setEnabled(false);
        btnAddFriend.setText("ДОБАВЛЕНИЕ...");

        repository.addFriend(userId, new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(FriendProfileActivity.this, "Друг добавлен!", Toast.LENGTH_SHORT).show();
                btnAddFriend.setText("✓ В ДРУЗЬЯХ");
                btnAddFriend.setEnabled(false);
            }

            @Override
            public void onError(String error) {
                btnAddFriend.setEnabled(true);
                btnAddFriend.setText("ДОБАВИТЬ В ДРУЗЬЯ");
                Toast.makeText(
                        FriendProfileActivity.this,
                        error != null ? error : "Не удалось добавить друга",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}