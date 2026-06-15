package com.example.codeandwords.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.User;
import com.example.codeandwords.ui.dashboard.MainActivity;
import com.example.codeandwords.ui.profile.AvatarConfig;
import com.example.codeandwords.ui.profile.AvatarPrefs;
import com.example.codeandwords.utils.SecurityUtils;
import com.google.android.material.textfield.TextInputEditText;

// Экран входа: хеширует пароль в фоновом потоке, делегирует авторизацию в Repository
public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private Button btnLogin;
    private Button btnGoToRegister;
    private ProgressBar progressBar;
    private Repository repository;

    private static final String TEST_EMAIL = "tester1@mail.com";
    private static final String TEST_PASSWORD = "tester1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Используем singleton для доступа к общему состоянию приложения
        repository = Repository.getInstance(getApplicationContext());

        initViews();
        fillTestUserData();
        setupListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoToRegister = findViewById(R.id.btnGoToRegister);
        progressBar = findViewById(R.id.progressBar);
    }

    private void fillTestUserData() {
        etEmail.setText(TEST_EMAIL);
        etPassword.setText(TEST_PASSWORD);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText() != null
                    ? etEmail.getText().toString().trim() : "";
            String password = etPassword.getText() != null
                    ? etPassword.getText().toString().trim() : "";

            if (validateInput(email, password)) {
                performLogin(email, password);
            }
        });

        btnGoToRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private boolean validateInput(String email, String password) {
        if (email.isEmpty()) {
            etEmail.setError("Введите Email");
            return false;
        }
        if (password.isEmpty()) {
            etPassword.setError("Введите пароль");
            return false;
        }
        return true;
    }

    // Хеширование выполняется в фоне (~50 мс), чтобы не блокировать UI-поток
    private void performLogin(String email, String password) {
        showLoading(true);

        new Thread(() -> {
            String hashedPassword = SecurityUtils.hashPassword(password);

            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(hashedPassword);

            runOnUiThread(() -> {
                repository.login(user, new Repository.DataCallback<User>() {
                    @Override
                    public void onSuccess(User data) {
                        showLoading(false);
                        prepareAvatarSetupState(data);

                        Toast.makeText(LoginActivity.this,
                                "Добро пожаловать, " + data.getUsername() + "!",
                                Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String error) {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this,
                                "Ошибка входа: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }).start();
    }

    // Определяет, нужна ли настройка аватара после входа
    private void prepareAvatarSetupState(User user) {
        if (user == null) {
            AvatarPrefs.setNeedsAvatarSetup(this, true);
            return;
        }

        String avatarJson = user.getAvatarConfig();

        if (avatarJson == null || avatarJson.trim().isEmpty()
                || "null".equals(avatarJson.trim())) {
            // Аватара нет — создаём дефолтный по полу пользователя
            AvatarConfig defaultConfig = new AvatarConfig();
            defaultConfig.gender = user.getGender();

            if ("male".equals(defaultConfig.gender)) {
                defaultConfig.hairStyle = 1;
                defaultConfig.earringsStyle = 0;
            } else {
                defaultConfig.hairStyle = 0;
                defaultConfig.earringsStyle = 1;
            }

            defaultConfig.facialHairStyle = 0;

            AvatarPrefs.saveDraft(this, defaultConfig);
            AvatarPrefs.setAvatarCreated(this, false);
            AvatarPrefs.setNeedsAvatarSetup(this, true);
        } else {
            AvatarPrefs.setAvatarCreated(this, true);
            AvatarPrefs.setNeedsAvatarSetup(this, false);
        }
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        btnGoToRegister.setEnabled(!isLoading);
    }
}