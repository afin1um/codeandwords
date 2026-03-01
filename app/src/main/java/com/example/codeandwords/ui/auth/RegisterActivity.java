package com.example.codeandwords.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.ui.dashboard.MainActivity;
import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.User;
import com.google.android.material.button.MaterialButton;

public class RegisterActivity extends AppCompatActivity {

    // Объявляем переменные
    private EditText etName, etEmail, etPassword;
    private MaterialButton btnRegister, btnBackToLogin; // MaterialButton т.к. в XML они такие
    private ProgressBar progressBar;
    private Repository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // --- ИСПРАВЛЕННЫЕ ID (теперь они совпадают с вашим XML) ---
        etName = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etRegEmail);
        etPassword = findViewById(R.id.etRegPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
        progressBar = findViewById(R.id.progressBarReg);

        repository = new Repository(this);

        btnRegister.setOnClickListener(v -> performRegistration());

        // Кнопка возврата на логин (если нужно)
        btnBackToLogin.setOnClickListener(v -> finish());
    }

    private void performRegistration() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

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

        // Создаем объект пользователя
        User newUser = new User(name, email, password);

        repository.register(newUser, new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(RegisterActivity.this, "Регистрация успешна!", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Ошибка: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}