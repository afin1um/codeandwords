package com.example.codeandwords.ui.game;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.google.android.material.card.MaterialCardView;

public class GameSelectionActivity extends AppCompatActivity {

    private TextView tvThemeName;
    // ИСПРАВЛЕНИЕ: Добавили cardTheory в список переменных
    private MaterialCardView cardMatching, cardSprint, cardWriteWord, cardDictionary, cardTheory;
    private View btnBack;

    private Long themeId;
    private String themeTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_selection);

        initViews();

        if (getIntent() != null) {
            themeId = getIntent().getLongExtra("THEME_ID", -1);
            themeTitle = getIntent().getStringExtra("THEME_TITLE");
        }

        if (themeTitle != null) {
            tvThemeName.setText("Тема: " + themeTitle);
        } else {
            tvThemeName.setText("Тема не выбрана");
        }

        setupListeners();
    }

    private void initViews() {
        tvThemeName = findViewById(R.id.tvThemeName);
        cardMatching = findViewById(R.id.cardMatching);
        cardSprint = findViewById(R.id.cardSprint);
        cardWriteWord = findViewById(R.id.cardWriteWord);
        cardDictionary = findViewById(R.id.cardDictionary);

        // ИСПРАВЛЕНИЕ: Инициализация кнопки Теории
        cardTheory = findViewById(R.id.cardTheory);

        btnBack = findViewById(R.id.btnBack);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // 1. Сопоставление
        cardMatching.setOnClickListener(v -> {
            if (themeId != -1) {
                Intent intent = new Intent(GameSelectionActivity.this, MatchingGameActivity.class);
                intent.putExtra("THEME_ID", themeId);
                startActivity(intent);
            }
        });

        // 2. Спринт
        cardSprint.setOnClickListener(v -> {
            if (themeId != -1) {
                Intent intent = new Intent(GameSelectionActivity.this, SprintGameActivity.class);
                intent.putExtra("THEME_ID", themeId);
                startActivity(intent);
            }
        });

        // 3. Вписать слово
        cardWriteWord.setOnClickListener(v -> {
            if (themeId != -1) {
                Intent intent = new Intent(GameSelectionActivity.this, WriteWordGameActivity.class);
                intent.putExtra("THEME_ID", themeId);
                startActivity(intent);
            }
        });

        // 4. Словарь (Учить слова)
        // ИСПРАВЛЕНИЕ: корректно закрыт блок
        cardDictionary.setOnClickListener(v -> {
            if (themeId != -1) {
                Intent intent = new Intent(GameSelectionActivity.this, DictionaryActivity.class);
                intent.putExtra("THEME_ID", themeId);
                intent.putExtra("THEME_TITLE", themeTitle);
                startActivity(intent);
            }
        });

        // 5. Теория
        // ИСПРАВЛЕНИЕ: Вынесли обработчик отдельно
        cardTheory.setOnClickListener(v -> {
            if (themeId != -1) {
                Intent intent = new Intent(GameSelectionActivity.this, TheoryActivity.class);
                intent.putExtra("THEME_ID", themeId);
                startActivity(intent);
            }
        });
    }
}