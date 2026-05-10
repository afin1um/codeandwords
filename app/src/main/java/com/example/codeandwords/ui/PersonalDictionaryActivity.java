package com.example.codeandwords.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.UserWord;
import com.example.codeandwords.ui.adapters.UserWordAdapter;
import com.example.codeandwords.ui.game.WriteWordGameActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PersonalDictionaryActivity extends AppCompatActivity {

    private static final String THEME_ALL = "Все темы";
    private static final String THEME_NONE = "Без темы";

    private Repository repository;
    private UserWordAdapter adapter;

    private RecyclerView rvWords;
    private FloatingActionButton fabAdd;
    private TextView tvWordsCount;
    private TextView tvSort;
    private TextView tvEmpty;
    private MaterialButton btnStartWordsTraining;
    private ImageButton btnBack;
    private LinearLayout themeFilterContainer;

    private boolean sortAscending = true;

    private String selectedThemeTitle = THEME_ALL;
    private Long selectedThemeId = null;

    private final List<ThemeFilterItem> themeFilters = new ArrayList<>();
    private final Map<Long, Integer> themeCountById = new HashMap<>();
    private int withoutThemeCount = 0;
    private int totalWordsCount = 0;

    // ✅ Адаптивные цвета чипов (день/ночь)
    private int chipUnselectedBg;
    private int chipUnselectedText;
    private int chipUnselectedStroke;
    private int chipSelectedBg;
    private int chipSelectedText;
    private int chipSelectedStroke;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_dictionary);

        repository = new Repository(this);

        loadThemeColors();
        initViews();
        setupRecycler();
        setupClicks();

        // ИЗМЕНЕНО: Вызываем синхронизацию перед загрузкой
        syncAndLoadDictionary();
    }

    /**
     * ✅ Загружаем цвета чипов из ресурсов (поддержка светлой/тёмной темы).
     */
    private void loadThemeColors() {
        chipUnselectedBg     = ContextCompat.getColor(this, R.color.dict_chip_unselected_bg);
        chipUnselectedText   = ContextCompat.getColor(this, R.color.dict_chip_unselected_text);
        chipUnselectedStroke = ContextCompat.getColor(this, R.color.dict_chip_unselected_stroke);

        chipSelectedBg       = ContextCompat.getColor(this, R.color.dict_chip_selected_bg);
        chipSelectedText     = ContextCompat.getColor(this, R.color.dict_chip_selected_text);
        chipSelectedStroke   = ContextCompat.getColor(this, R.color.dict_chip_selected_stroke);
    }

    private void initViews() {
        rvWords = findViewById(R.id.rvUserWords);
        fabAdd = findViewById(R.id.fabAddWord);
        tvWordsCount = findViewById(R.id.tvWordsCount);
        tvSort = findViewById(R.id.tvSort);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnStartWordsTraining = findViewById(R.id.btnStartWordsTraining);
        btnBack = findViewById(R.id.btnBack);
        themeFilterContainer = findViewById(R.id.themeFilterContainer);
    }

    private void setupRecycler() {
        rvWords.setLayoutManager(new LinearLayoutManager(this));
        // При удалении слова из адаптера, он сам должен перезагрузить слова и обновить счетчики
        adapter = new UserWordAdapter(repository, this::reloadCountsAndWords);
        rvWords.setAdapter(adapter);
    }

    private void setupClicks() {
        fabAdd.setOnClickListener(v -> showAddWordDialog());

        btnBack.setOnClickListener(v -> finish());

        btnStartWordsTraining.setOnClickListener(v -> {
            Intent intent = new Intent(this, WriteWordGameActivity.class);
            intent.putExtra("TRAINING_MODE", "LEARNED_WORDS");
            startActivity(intent);
        });

        tvSort.setOnClickListener(v -> {
            sortAscending = !sortAscending;
            adapter.setSortAscending(sortAscending);
            adapter.sortWords();
            tvSort.setText(sortAscending ? "СОРТИРОВКА: А-Я" : "СОРТИРОВКА: Я-А");
        });
    }

    private void syncAndLoadDictionary() {
        repository.syncPersonalWords(new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                // После успешной синхронизации, восстанавливаем темы и загружаем слова
                repairDictionaryAndLoadThemes();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(
                        PersonalDictionaryActivity.this,
                        "Ошибка синхронизации словаря: " + error + ". Загрузка локальных данных.",
                        Toast.LENGTH_LONG
                ).show();
                // В случае ошибки синхронизации, все равно пытаемся загрузить локальные данные
                repairDictionaryAndLoadThemes();
            }
        });
    }

    private void repairDictionaryAndLoadThemes() {
        repository.repairPersonalDictionaryThemes(new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                loadThemesFromDatabase();
            }

            @Override
            public void onError(String error) {
                // Если ремонт тем не удался, всё равно пробуем загрузить темы
                Toast.makeText(
                        PersonalDictionaryActivity.this,
                        "Ошибка восстановления тем: " + error,
                        Toast.LENGTH_SHORT
                ).show();
                loadThemesFromDatabase();
            }
        });
    }

    private void loadThemesFromDatabase() {
        repository.getThemes(new Repository.DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> themes) {
                themeFilters.clear();

                themeFilters.add(new ThemeFilterItem(null, THEME_ALL));
                themeFilters.add(new ThemeFilterItem(null, THEME_NONE));

                if (themes != null) {
                    for (Theme theme : themes) {
                        if (theme == null) continue;

                        Long id = theme.getId();
                        String title = theme.getTitle() == null ? "" : theme.getTitle().trim();

                        if (id == null || id <= 0 || title.isEmpty()) continue;

                        themeFilters.add(new ThemeFilterItem(id, title));
                    }
                }

                reloadCountsAndWords();
            }

            @Override
            public void onError(String error) {
                themeFilters.clear();
                themeFilters.add(new ThemeFilterItem(null, THEME_ALL));
                themeFilters.add(new ThemeFilterItem(null, THEME_NONE));

                reloadCountsAndWords();

                Toast.makeText(
                        PersonalDictionaryActivity.this,
                        "Темы не загрузились: " + error,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void reloadCountsAndWords() {
        repository.getUserPersonalWords(new Repository.DataCallback<List<UserWord>>() {
            @Override
            public void onSuccess(List<UserWord> words) {
                calculateThemeCounts(words);
                renderThemeFilters();
                loadWordsForSelectedTheme();
            }

            @Override
            public void onError(String error) {
                themeCountById.clear();
                withoutThemeCount = 0;
                totalWordsCount = 0;

                renderThemeFilters();
                loadWordsForSelectedTheme();
            }
        });
    }

    private void calculateThemeCounts(List<UserWord> words) {
        themeCountById.clear();
        withoutThemeCount = 0;
        totalWordsCount = 0;

        if (words == null) {
            return;
        }

        totalWordsCount = words.size();

        for (UserWord word : words) {
            if (word == null) continue;

            Long themeId = word.getThemeId();
            String themeTitle = word.getThemeTitle();

            boolean withoutTheme = themeId == null
                    || themeTitle == null
                    || themeTitle.trim().isEmpty()
                    || THEME_NONE.equals(themeTitle.trim());

            if (withoutTheme) {
                withoutThemeCount++;
            } else {
                int oldCount = themeCountById.containsKey(themeId)
                        ? themeCountById.get(themeId)
                        : 0;

                themeCountById.put(themeId, oldCount + 1);
            }
        }
    }

    /**
     * ✅ Чипы используют адаптивные цвета.
     */
    private void renderThemeFilters() {
        themeFilterContainer.removeAllViews();

        for (ThemeFilterItem item : themeFilters) {
            MaterialButton chip = new MaterialButton(this);

            chip.setAllCaps(false);
            chip.setText(getChipText(item));
            chip.setTextSize(14f);
            chip.setMinHeight(dp(40));
            chip.setMinimumHeight(dp(40));
            chip.setCornerRadius(dp(18));
            chip.setInsetTop(0);
            chip.setInsetBottom(0);

            boolean selected = item.title.equals(selectedThemeTitle);

            // ✅ Адаптивные цвета
            if (selected) {
                chip.setTextColor(chipSelectedText);
                chip.setBackgroundTintList(ColorStateList.valueOf(chipSelectedBg));
                chip.setStrokeColor(ColorStateList.valueOf(chipSelectedStroke));
            } else {
                chip.setTextColor(chipUnselectedText);
                chip.setBackgroundTintList(ColorStateList.valueOf(chipUnselectedBg));
                chip.setStrokeColor(ColorStateList.valueOf(chipUnselectedStroke));
            }
            chip.setStrokeWidth(dp(1));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(44)
            );
            params.setMargins(0, 0, dp(10), 0);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                selectedThemeId = item.id;
                selectedThemeTitle = item.title;

                renderThemeFilters();
                loadWordsForSelectedTheme();
            });

            themeFilterContainer.addView(chip);
        }
    }

    private String getChipText(ThemeFilterItem item) {
        if (THEME_ALL.equals(item.title)) {
            return THEME_ALL + " (" + totalWordsCount + ")";
        }

        if (THEME_NONE.equals(item.title)) {
            return THEME_NONE + " (" + withoutThemeCount + ")";
        }

        int count = 0;

        if (item.id != null && themeCountById.containsKey(item.id)) {
            count = themeCountById.get(item.id);
        }

        return item.title + " (" + count + ")";
    }

    private void loadWordsForSelectedTheme() {
        Repository.DataCallback<List<UserWord>> callback = new Repository.DataCallback<List<UserWord>>() {
            @Override
            public void onSuccess(List<UserWord> data) {
                int count = data != null ? data.size() : 0;

                if (THEME_ALL.equals(selectedThemeTitle)) {
                    tvWordsCount.setText(count + " " + getWordCountLabel(count));
                } else {
                    tvWordsCount.setText(count + " " + getWordCountLabel(count) + " • " + selectedThemeTitle);
                }

                if (count == 0) {
                    tvEmpty.setText(THEME_ALL.equals(selectedThemeTitle)
                            ? "Словарь пока пуст"
                            : "В этой теме пока нет слов");

                    tvEmpty.setVisibility(View.VISIBLE);
                    rvWords.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rvWords.setVisibility(View.VISIBLE);
                }

                adapter.setWords(data);
                adapter.setSortAscending(sortAscending);
                adapter.sortWords();
            }

            @Override
            public void onError(String error) {
                tvWordsCount.setText("0 слов");
                tvEmpty.setVisibility(View.VISIBLE);
                rvWords.setVisibility(View.GONE);

                Toast.makeText(
                        PersonalDictionaryActivity.this,
                        "Ошибка: " + error,
                        Toast.LENGTH_SHORT
                ).show();
            }
        };

        if (THEME_ALL.equals(selectedThemeTitle)) {
            repository.getUserPersonalWords(callback);
        } else if (THEME_NONE.equals(selectedThemeTitle)) {
            repository.getUserPersonalWordsByTheme(null, THEME_NONE, callback);
        } else {
            repository.getUserPersonalWordsByTheme(selectedThemeId, selectedThemeTitle, callback);
        }
    }

    private void showAddWordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_word, null);

        EditText etWord = view.findViewById(R.id.etNewWord);
        EditText etTranslation = view.findViewById(R.id.etNewTranslation);
        EditText etTranscription = view.findViewById(R.id.etNewTranscription);
        EditText etNotes = view.findViewById(R.id.etNewNotes);

        builder.setView(view)
                .setTitle("Добавить новое слово")
                .setPositiveButton("Добавить", (dialog, which) -> {
                    String word = etWord.getText().toString().trim();
                    String translation = etTranslation.getText().toString().trim();
                    String transcription = etTranscription.getText().toString().trim();
                    String notes = etNotes.getText().toString().trim();

                    if (word.isEmpty() || translation.isEmpty()) {
                        Toast.makeText(
                                PersonalDictionaryActivity.this,
                                "Заполните слово и перевод",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    Long themeIdForWord;
                    String themeTitleForWord;

                    if (THEME_ALL.equals(selectedThemeTitle)) {
                        themeIdForWord = null;
                        themeTitleForWord = THEME_NONE;
                    } else if (THEME_NONE.equals(selectedThemeTitle)) {
                        themeIdForWord = null;
                        themeTitleForWord = THEME_NONE;
                    } else {
                        themeIdForWord = selectedThemeId;
                        themeTitleForWord = selectedThemeTitle;
                    }

                    repository.addUserWord(
                            themeIdForWord,
                            themeTitleForWord,
                            word,
                            translation,
                            transcription,
                            notes,
                            new Repository.DataCallback<Void>() {
                                @Override
                                public void onSuccess(Void data) {
                                    // После добавления инициируем повторную синхронизацию
                                    // для обновления отображения и отправки на сервер
                                    syncAndLoadDictionary();
                                    Toast.makeText(
                                            PersonalDictionaryActivity.this,
                                            "Добавлено!",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }

                                @Override
                                public void onError(String error) {
                                    Toast.makeText(
                                            PersonalDictionaryActivity.this,
                                            error,
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                    );
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private String getWordCountLabel(int count) {
        int lastDigit = count % 10;
        int lastTwoDigits = count % 100;

        if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
            return "слов";
        }

        if (lastDigit == 1) {
            return "слово";
        }

        if (lastDigit >= 2 && lastDigit <= 4) {
            return "слова";
        }

        return "слов";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (repository != null) {
            repository.onDestroy();
        }
    }

    private static class ThemeFilterItem {
        Long id;
        String title;

        ThemeFilterItem(Long id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}