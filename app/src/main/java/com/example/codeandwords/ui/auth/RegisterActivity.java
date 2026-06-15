package com.example.codeandwords.ui.auth;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.User;
import com.example.codeandwords.ui.base.BaseBackActivity;
import com.example.codeandwords.ui.profile.AvatarConfig;
import com.example.codeandwords.ui.profile.AvatarEditorActivity;
import com.example.codeandwords.ui.profile.AvatarPrefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

// Экран регистрации с выбором пола и созданием дефолтного аватара
public class RegisterActivity extends BaseBackActivity {

    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;

    private MaterialButton btnRegister;
    private MaterialButton btnBackToLogin;
    private MaterialButton btnGenderFemale;
    private MaterialButton btnGenderMale;

    private View btnBack;
    private ProgressBar progressBar;

    private Repository repository;

    private String selectedGender = "female";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        setupBackButton(R.id.btnUniBack);
        setupListeners();
        updateGenderButtons();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnUniBack);

        etName = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etRegEmail);
        etPassword = findViewById(R.id.etRegPassword);

        btnRegister = findViewById(R.id.btnRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
        btnGenderFemale = findViewById(R.id.btnGenderFemale);
        btnGenderMale = findViewById(R.id.btnGenderMale);

        progressBar = findViewById(R.id.progressBarReg);
    }

    private void setupListeners() {
        btnGenderFemale.setOnClickListener(v -> {
            selectedGender = "female";
            updateGenderButtons();
        });

        btnGenderMale.setOnClickListener(v -> {
            selectedGender = "male";
            updateGenderButtons();
        });

        btnRegister.setOnClickListener(v -> performRegistration());

        btnBackToLogin.setOnClickListener(v -> finish());
    }

    // Блокирует случайный выход во время активной регистрации
    @Override
    protected void handleBack() {
        if (isLoading()) {
            Toast.makeText(this, "Дождитесь завершения регистрации",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        super.handleBack();
    }

    // Активная кнопка получает толстую голубую обводку, неактивная — тонкую серую
    private void updateGenderButtons() {
        boolean isFemale = "female".equals(selectedGender);

        int activeStroke = ContextCompat.getColor(this, R.color.app_blue);
        int inactiveStroke = ContextCompat.getColor(this, R.color.app_card_stroke);

        btnGenderFemale.setStrokeWidth(isFemale ? dp(4) : dp(1));
        btnGenderFemale.setStrokeColor(
                ColorStateList.valueOf(isFemale ? activeStroke : inactiveStroke));
        btnGenderFemale.setAlpha(isFemale ? 1f : 0.65f);

        btnGenderMale.setStrokeWidth(!isFemale ? dp(4) : dp(1));
        btnGenderMale.setStrokeColor(
                ColorStateList.valueOf(!isFemale ? activeStroke : inactiveStroke));
        btnGenderMale.setAlpha(!isFemale ? 1f : 0.65f);
    }

    private void performRegistration() {
        String name = etName.getText() != null
                ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null
                ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null
                ? etPassword.getText().toString().trim() : "";

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, заполните все поля",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Пароль должен быть не менее 6 символов",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Создаём дефолтный аватар по выбранному полу и сохраняем как черновик
        AvatarConfig defaultAvatar = createDefaultAvatar(selectedGender);
        AvatarPrefs.saveDraft(this, defaultAvatar);
        AvatarPrefs.setAvatarCreated(this, false);
        AvatarPrefs.setNeedsAvatarSetup(this, true);

        User newUser = new User(name, email, password);
        newUser.setGender(selectedGender);
        newUser.setAvatarConfig(defaultAvatar.toJson());

        repository.register(newUser, new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                showLoading(false);

                Toast.makeText(RegisterActivity.this,
                        "Регистрация успешна! Настройте аватар.",
                        Toast.LENGTH_SHORT).show();

                // После регистрации сразу переходим в редактор аватара
                Intent intent = new Intent(RegisterActivity.this, AvatarEditorActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                Toast.makeText(RegisterActivity.this,
                        "Ошибка: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Создаёт дефолтный аватар с параметрами, зависящими от пола
    private AvatarConfig createDefaultAvatar(String gender) {
        AvatarConfig config = new AvatarConfig();
        config.gender = "male".equals(gender) ? "male" : "female";
        config.facialHairStyle = 0;

        if ("male".equals(config.gender)) {
            config.hairStyle = 1;
            config.earringsStyle = 0;
        } else {
            config.hairStyle = 0;
            config.earringsStyle = 1;
        }

        return config;
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);

        btnRegister.setEnabled(!isLoading);
        btnBackToLogin.setEnabled(!isLoading);
        btnGenderFemale.setEnabled(!isLoading);
        btnGenderMale.setEnabled(!isLoading);

        if (btnBack != null) {
            btnBack.setEnabled(!isLoading);
            btnBack.setAlpha(isLoading ? 0.4f : 1f);
        }
    }

    private boolean isLoading() {
        return progressBar != null && progressBar.getVisibility() == View.VISIBLE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}