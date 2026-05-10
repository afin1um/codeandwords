package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.google.android.material.button.MaterialButton;

public class AdminPanelActivity extends AppCompatActivity {

    private ImageButton btnBackAdmin;
    private MaterialButton btnOpenAdminThemes;
    private MaterialButton btnOpenAdminWords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_panel);

        initViews();
        setupClicks();
    }

    private void initViews() {
        btnBackAdmin = findViewById(R.id.btnBackAdmin);
        btnOpenAdminThemes = findViewById(R.id.btnOpenAdminThemes);
        btnOpenAdminWords = findViewById(R.id.btnOpenAdminWords);
    }

    private void setupClicks() {
        btnBackAdmin.setOnClickListener(v -> finish());

        btnOpenAdminThemes.setOnClickListener(v -> {
            Intent intent = new Intent(AdminPanelActivity.this, AdminThemesActivity.class);
            startActivity(intent);
        });

        btnOpenAdminWords.setOnClickListener(v -> {
            Intent intent = new Intent(AdminPanelActivity.this, AdminWordsActivity.class);
            startActivity(intent);
        });
    }
}