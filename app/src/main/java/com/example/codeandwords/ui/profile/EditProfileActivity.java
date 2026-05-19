package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class EditProfileActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextInputEditText etUsername;
    private TextInputEditText etEmail;
    private MaterialButton btnChangeAvatar;
    private MaterialButton btnSaveProfile;

    private Repository repository;
    private User currentUser;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        setupClicks();
        loadUser();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBackEditProfile);
        etUsername = findViewById(R.id.etEditUsername);
        etEmail = findViewById(R.id.etEditEmail);
        btnChangeAvatar = findViewById(R.id.btnChangeAvatarFromEdit);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
    }

    private void setupClicks() {
        btnBack.setOnClickListener(v -> finish());

        btnChangeAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(EditProfileActivity.this, AvatarEditorActivity.class);
            startActivity(intent);
        });

        btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void loadUser() {
        repository.getCurrentUser(new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;

                if (user == null) return;

                etUsername.setText(user.getUsername() != null ? user.getUsername() : "");
                etEmail.setText(user.getEmail() != null ? user.getEmail() : "");
                etEmail.setEnabled(false);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EditProfileActivity.this, "Не удалось загрузить профиль", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void saveProfile() {
        String username = etUsername.getText() != null
                ? etUsername.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Введите имя пользователя");
            return;
        }

        if (username.length() < 3) {
            etUsername.setError("Минимум 3 символа");
            return;
        }

        btnSaveProfile.setEnabled(false);

        repository.updateUsername(username, new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                btnSaveProfile.setEnabled(true);
                Toast.makeText(EditProfileActivity.this, "Профиль обновлён", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                btnSaveProfile.setEnabled(true);
                Toast.makeText(
                        EditProfileActivity.this,
                        error != null ? error : "Не удалось обновить профиль",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}