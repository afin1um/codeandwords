package com.example.codeandwords.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.User;
import com.example.codeandwords.ui.profile.AvatarConfig;
import com.example.codeandwords.ui.profile.AvatarEditorActivity;
import com.example.codeandwords.ui.profile.AvatarPrefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;

    private MaterialButton btnRegister;
    private MaterialButton btnBackToLogin;
    private MaterialButton btnGenderFemale;
    private MaterialButton btnGenderMale;

    private ProgressBar progressBar;
    private Repository repository;

    private String selectedGender = "female";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etRegEmail);
        etPassword = findViewById(R.id.etRegPassword);

        btnRegister = findViewById(R.id.btnRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
        btnGenderFemale = findViewById(R.id.btnGenderFemale);
        btnGenderMale = findViewById(R.id.btnGenderMale);

        progressBar = findViewById(R.id.progressBarReg);

        repository = new Repository(this);

        updateGenderButtons();

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

    private void updateGenderButtons() {
        boolean isFemale = "female".equals(selectedGender);

        btnGenderFemale.setStrokeWidth(isFemale ? 4 : 1);
        btnGenderMale.setStrokeWidth(!isFemale ? 4 : 1);

        btnGenderFemale.setAlpha(isFemale ? 1f : 0.65f);
        btnGenderMale.setAlpha(!isFemale ? 1f : 0.65f);
    }

    private void performRegistration() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, заполните все поля", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Пароль должен быть не менее 6 символов", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);
        btnBackToLogin.setEnabled(false);
        btnGenderFemale.setEnabled(false);
        btnGenderMale.setEnabled(false);

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
                progressBar.setVisibility(View.GONE);

                Toast.makeText(
                        RegisterActivity.this,
                        "Регистрация успешна! Настройте аватар.",
                        Toast.LENGTH_SHORT
                ).show();

                Intent intent = new Intent(RegisterActivity.this, AvatarEditorActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);

                btnRegister.setEnabled(true);
                btnBackToLogin.setEnabled(true);
                btnGenderFemale.setEnabled(true);
                btnGenderMale.setEnabled(true);

                Toast.makeText(RegisterActivity.this, "Ошибка: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

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
}