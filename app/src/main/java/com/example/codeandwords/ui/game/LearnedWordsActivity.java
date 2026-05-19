package com.example.codeandwords.ui.game;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.UserWord;
import com.example.codeandwords.model.Word;
import com.example.codeandwords.ui.adapters.WordListAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LearnedWordsActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final long FILTER_ALL_THEMES = -1L;

    private View btnBackLearned;
    private TextView tvLearnedCount;
    private TextView tvLearnedEmpty;
    private RecyclerView rvLearnedWords;
    private ProgressBar pbLearnedWords;

    private TextInputEditText etLearnedSearch;
    private HorizontalScrollView scrollThemeFilters;
    private LinearLayout themeFiltersContainer;

    private Repository repository;
    private WordListAdapter adapter;
    private TextToSpeech tts;
    private boolean isTtsReady = false;

    private final List<Word> allLearnedWords = new ArrayList<>();
    private final List<Theme> allThemes = new ArrayList<>();

    private long selectedThemeId = FILTER_ALL_THEMES;
    private String currentSearchQuery = "";

    // ✅ Цвета чипов из ресурсов (для авто-смены темы)
    private int chipSelectedBg;
    private int chipSelectedText;
    private int chipSelectedStroke;
    private int chipUnselectedBg;
    private int chipUnselectedText;
    private int chipUnselectedStroke;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learned_words);

        repository = Repository.getInstance(getApplicationContext());
        tts = new TextToSpeech(this, this);

        loadThemeColors();
        initViews();
        setupRecyclerView();
        setupSearch();
        loadThemesAndWords();
    }

    private void loadThemeColors() {
        chipSelectedBg     = ContextCompat.getColor(this, R.color.chip_selected_bg);
        chipSelectedText   = ContextCompat.getColor(this, R.color.chip_selected_text);
        chipSelectedStroke = ContextCompat.getColor(this, R.color.chip_selected_stroke);

        chipUnselectedBg     = ContextCompat.getColor(this, R.color.chip_unselected_bg);
        chipUnselectedText   = ContextCompat.getColor(this, R.color.chip_unselected_text);
        chipUnselectedStroke = ContextCompat.getColor(this, R.color.chip_unselected_stroke);
    }

    private void initViews() {
        btnBackLearned = findViewById(R.id.btnBackLearned);
        tvLearnedCount = findViewById(R.id.tvLearnedCount);
        tvLearnedEmpty = findViewById(R.id.tvLearnedEmpty);
        rvLearnedWords = findViewById(R.id.rvLearnedWords);
        pbLearnedWords = findViewById(R.id.pbLearnedWords);
        etLearnedSearch = findViewById(R.id.etLearnedSearch);
        scrollThemeFilters = findViewById(R.id.scrollThemeFilters);
        themeFiltersContainer = findViewById(R.id.themeFiltersContainer);

        btnBackLearned.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        rvLearnedWords.setLayoutManager(new LinearLayoutManager(this));

        adapter = new WordListAdapter(new WordListAdapter.OnWordClickListener() {
            @Override
            public void onSpeakClick(String term, boolean isSlow) {
                speakWord(term, isSlow);
            }

            @Override
            public void onAddToDictionaryClick(Word word) {
                addWordToPersonalDictionary(word);
            }
        });

        rvLearnedWords.setAdapter(adapter);
    }

    private void setupSearch() {
        etLearnedSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void loadThemesAndWords() {
        pbLearnedWords.setVisibility(View.VISIBLE);
        tvLearnedEmpty.setVisibility(View.GONE);
        rvLearnedWords.setVisibility(View.GONE);

        repository.getThemes(new Repository.DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> themes) {
                allThemes.clear();

                if (themes != null) {
                    allThemes.addAll(themes);
                }

                buildThemeFilterChips();
                loadLearnedWords();
            }

            @Override
            public void onError(String error) {
                allThemes.clear();
                buildThemeFilterChips();
                loadLearnedWords();
            }
        });
    }

    private void loadLearnedWords() {
        repository.getLearnedWords(new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                pbLearnedWords.setVisibility(View.GONE);

                allLearnedWords.clear();

                if (words != null) {
                    allLearnedWords.addAll(words);
                }

                applyFilters();
                loadPersonalDictionaryState();
            }

            @Override
            public void onError(String error) {
                pbLearnedWords.setVisibility(View.GONE);
                tvLearnedEmpty.setVisibility(View.VISIBLE);
                rvLearnedWords.setVisibility(View.GONE);
                tvLearnedEmpty.setText(error != null ? error : "Не удалось загрузить выученные слова");
                Toast.makeText(LearnedWordsActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buildThemeFilterChips() {
        themeFiltersContainer.removeAllViews();

        addThemeChip(FILTER_ALL_THEMES, "Все", selectedThemeId == FILTER_ALL_THEMES);

        for (Theme theme : allThemes) {
            if (theme == null || theme.getId() == null) continue;

            String title = theme.getTitle() != null && !theme.getTitle().trim().isEmpty()
                    ? theme.getTitle().trim()
                    : "Без названия";

            addThemeChip(theme.getId(), title, selectedThemeId == theme.getId());
        }

        scrollThemeFilters.setVisibility(View.VISIBLE);
    }

    /**
     * ✅ Цвета чипов теперь берутся из ресурсов (поддерживают день/ночь).
     */
    private void addThemeChip(long themeId, String title, boolean selected) {
        MaterialButton chip = new MaterialButton(this);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(42)
        );
        params.setMargins(0, 0, dp(10), 0);

        chip.setLayoutParams(params);
        chip.setMinWidth(0);
        chip.setMinimumWidth(0);
        chip.setInsetTop(0);
        chip.setInsetBottom(0);
        chip.setAllCaps(false);
        chip.setText(title);
        chip.setTextSize(13);

        // ✅ Применяем цвета в зависимости от состояния и темы
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
        chip.setCornerRadius(dp(18));

        chip.setOnClickListener(v -> {
            selectedThemeId = themeId;
            buildThemeFilterChips();
            applyFilters();
        });

        themeFiltersContainer.addView(chip);
    }

    private void applyFilters() {
        List<Word> filtered = new ArrayList<>();

        for (Word word : allLearnedWords) {
            if (word == null) continue;

            if (!matchesTheme(word)) {
                continue;
            }

            if (!matchesSearch(word)) {
                continue;
            }

            filtered.add(word);
        }

        updateList(filtered);
    }

    private boolean matchesTheme(Word word) {
        if (selectedThemeId == FILTER_ALL_THEMES) {
            return true;
        }

        return word.getThemeId() != null && word.getThemeId() == selectedThemeId;
    }

    private boolean matchesSearch(Word word) {
        if (currentSearchQuery == null || currentSearchQuery.isEmpty()) {
            return true;
        }

        String term = word.getTerm() == null ? "" : word.getTerm().toLowerCase(Locale.ROOT);
        String translation = word.getTranslation() == null ? "" : word.getTranslation().toLowerCase(Locale.ROOT);
        String transcription = word.getTranscription() == null ? "" : word.getTranscription().toLowerCase(Locale.ROOT);

        return term.contains(currentSearchQuery)
                || translation.contains(currentSearchQuery)
                || transcription.contains(currentSearchQuery);
    }

    private void updateList(List<Word> words) {
        int count = words != null ? words.size() : 0;

        tvLearnedCount.setText(count + " " + getWordEnding(count));

        if (count == 0) {
            rvLearnedWords.setVisibility(View.GONE);
            tvLearnedEmpty.setVisibility(View.VISIBLE);

            if (allLearnedWords.isEmpty()) {
                tvLearnedEmpty.setText("Вы пока не выучили ни одного слова.\nПройдите режим сопоставления или другие упражнения.");
            } else {
                tvLearnedEmpty.setText("По выбранному фильтру ничего не найдено.");
            }

            adapter.setWords(new ArrayList<>());
        } else {
            tvLearnedEmpty.setVisibility(View.GONE);
            rvLearnedWords.setVisibility(View.VISIBLE);
            adapter.setWords(words);
        }
    }

    private void loadPersonalDictionaryState() {
        repository.getUserPersonalWords(new Repository.DataCallback<List<UserWord>>() {
            @Override
            public void onSuccess(List<UserWord> words) {
                Set<String> addedTerms = new HashSet<>();

                if (words != null) {
                    for (UserWord userWord : words) {
                        if (userWord != null && userWord.getWord() != null) {
                            addedTerms.add(normalizeTerm(userWord.getWord()));
                        }
                    }
                }

                adapter.setAddedTerms(addedTerms);
            }

            @Override
            public void onError(String error) {
                Log.e("LearnedWordsActivity", "Не удалось загрузить состояние словаря: " + error);
            }
        });
    }

    private void addWordToPersonalDictionary(Word word) {
        if (word == null) {
            Toast.makeText(this, "Слово не найдено", Toast.LENGTH_SHORT).show();
            return;
        }

        repository.addWordToPersonalDictionary(word, new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                adapter.markWordAsAdded(word);
                Toast.makeText(
                        LearnedWordsActivity.this,
                        "Слово добавлено в личный словарь",
                        Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void onError(String error) {
                if (error != null && error.toLowerCase(Locale.ROOT).contains("уже есть")) {
                    adapter.markWordAsAdded(word);
                }

                Toast.makeText(
                        LearnedWordsActivity.this,
                        error != null ? error : "Не удалось добавить слово",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private String normalizeTerm(String term) {
        return term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
    }

    private String getWordEnding(int count) {
        int lastTwo = count % 100;
        int last = count % 10;

        if (lastTwo >= 11 && lastTwo <= 14) {
            return "слов";
        }

        if (last == 1) {
            return "слово";
        }

        if (last >= 2 && last <= 4) {
            return "слова";
        }

        return "слов";
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Английский язык не поддерживается", Toast.LENGTH_SHORT).show();
            } else {
                isTtsReady = true;
            }
        } else {
            Log.e("LearnedWordsActivity", "Ошибка инициализации TextToSpeech");
        }
    }

    private void speakWord(String text, boolean isSlow) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "Слово пустое", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isTtsReady || tts == null) {
            Toast.makeText(this, "Голосовой движок ещё не готов", Toast.LENGTH_SHORT).show();
            return;
        }

        tts.setSpeechRate(isSlow ? 0.45f : 1.0f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "learned_word_tts");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
        // Это важно! Иначе TTS и SoundPool остаются в памяти
        if (repository != null) {
            repository.onDestroy();
        }
    }
}