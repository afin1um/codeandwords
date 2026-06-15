package com.example.codeandwords.ui.game;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Word;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

// Экран выбора режима игры для темы: блокирует режимы при нехватке слов
public class GameSelectionActivity extends AppCompatActivity {

    // Минимальное количество слов для запуска игрового режима
    private static final int MIN_WORDS_FOR_MODE = 5;

    private TextView tvThemeName;
    private MaterialCardView cardMatching;
    private MaterialCardView cardSprint;
    private MaterialCardView cardWriteWord;
    private MaterialCardView cardDictionary;
    private MaterialCardView cardTheory;
    private View btnBack;

    private Repository repository;

    private Long themeId;
    private String themeTitle;

    private List<Word> themeWords = new ArrayList<>();
    private boolean wordsLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_selection);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        readIntent();
        setupThemeTitle();
        setupListeners();
        loadThemeWords();
    }

    private void initViews() {
        tvThemeName = findViewById(R.id.tvThemeName);
        cardMatching = findViewById(R.id.cardMatching);
        cardSprint = findViewById(R.id.cardSprint);
        cardWriteWord = findViewById(R.id.cardWriteWord);
        cardDictionary = findViewById(R.id.cardDictionary);
        cardTheory = findViewById(R.id.cardTheory);
        btnBack = findViewById(R.id.btnBack);
    }

    private void readIntent() {
        if (getIntent() != null) {
            themeId = getIntent().getLongExtra("THEME_ID", -1);
            themeTitle = getIntent().getStringExtra("THEME_TITLE");
        } else {
            themeId = -1L;
            themeTitle = null;
        }
    }

    private void setupThemeTitle() {
        if (themeTitle != null && !themeTitle.trim().isEmpty()) {
            tvThemeName.setText("Тема: " + themeTitle);
        } else {
            tvThemeName.setText("Тема не выбрана");
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        cardMatching.setOnClickListener(v ->
                openModeIfEnoughWords(MatchingGameActivity.class, false));
        cardSprint.setOnClickListener(v ->
                openModeIfEnoughWords(SprintGameActivity.class, false));
        cardWriteWord.setOnClickListener(v ->
                openModeIfEnoughWords(WriteWordGameActivity.class, false));
        cardDictionary.setOnClickListener(v -> openDictionaryMode());

        cardTheory.setOnClickListener(v -> {
            if (!isThemeValid()) return;

            Intent intent = new Intent(GameSelectionActivity.this, TheoryActivity.class);
            intent.putExtra("THEME_ID", themeId);
            intent.putExtra("THEME_TITLE", themeTitle);
            startActivity(intent);
        });
    }

    // Проверяет минимальное количество слов перед запуском режима
    private void openModeIfEnoughWords(Class<?> targetActivity, boolean includeThemeTitle) {
        if (!isThemeValid()) return;

        if (!wordsLoaded) {
            Toast.makeText(this, "Термины ещё загружаются", Toast.LENGTH_SHORT).show();
            return;
        }

        if (themeWords.size() < MIN_WORDS_FOR_MODE) {
            Toast.makeText(this,
                    "Для запуска режима нужно минимум 5 терминов. Сейчас: "
                            + themeWords.size(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(GameSelectionActivity.this, targetActivity);
        intent.putExtra("THEME_ID", themeId);

        if (includeThemeTitle) {
            intent.putExtra("THEME_TITLE", themeTitle);
        }

        startActivity(intent);
    }

    private boolean isThemeValid() {
        if (themeId == null || themeId == -1L) {
            Toast.makeText(this, "Тема не выбрана", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // Управляет доступностью карточек режимов и их визуальным состоянием
    private void setModesEnabled(boolean enabled) {
        cardMatching.setEnabled(enabled);
        cardSprint.setEnabled(enabled);
        cardWriteWord.setEnabled(enabled);
        cardDictionary.setEnabled(enabled);

        cardMatching.setAlpha(enabled ? 1f : 0.55f);
        cardSprint.setAlpha(enabled ? 1f : 0.55f);
        cardWriteWord.setAlpha(enabled ? 1f : 0.55f);
        cardDictionary.setAlpha(enabled ? 1f : 0.55f);
    }

    private List<Word> filterValidWords(List<Word> words) {
        List<Word> result = new ArrayList<>();
        if (words == null) return result;

        for (Word word : words) {
            if (word == null) continue;
            String term = word.getTerm() == null ? "" : word.getTerm().trim();
            String translation = word.getTranslation() == null ? "" : word.getTranslation().trim();
            if (!term.isEmpty() && !translation.isEmpty()) result.add(word);
        }

        return result;
    }

    private void openDictionaryMode() {
        if (!isThemeValid()) return;

        Intent intent = new Intent(GameSelectionActivity.this, DictionaryActivity.class);
        intent.putExtra("THEME_ID", themeId);
        intent.putExtra("THEME_TITLE", themeTitle);
        startActivity(intent);
    }

    // Загружает слова темы; блокирует режимы до завершения загрузки
    private void loadThemeWords() {
        if (!isThemeValid()) {
            setModesEnabled(false);
            return;
        }

        setModesEnabled(false);

        repository.getWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> data) {
                // Обновляем список — может вызваться повторно со свежими данными с сервера
                themeWords = filterValidWords(data);
                wordsLoaded = true;
                setModesEnabled(true);
            }

            @Override
            public void onError(String error) {
                // Не показываем ошибку, если локальные данные уже загружены
                if (wordsLoaded && !themeWords.isEmpty()) return;

                wordsLoaded = true;
                themeWords.clear();
                setModesEnabled(true);

                Toast.makeText(GameSelectionActivity.this,
                        "Не удалось загрузить термины темы", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (repository != null) {
            repository.onDestroy();
        }
    }
}