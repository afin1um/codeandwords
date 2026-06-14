package com.example.codeandwords.ui.game;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Word;
import com.example.codeandwords.ui.base.BaseBackActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListeningGameActivity extends BaseBackActivity {

    private static final int OPTIONS_COUNT = 5;
    private static final int POINTS_PER_CORRECT = 10;
    private ProgressBar progressListening;
    private ProgressBar pbListening;
    private TextView tvListeningCounter;
    private MaterialButton btnPlayListening;

    private MaterialCardView cardListeningDictionaryState;
    private TextView tvListeningDictionaryIcon;
    private TextView tvListeningDictionaryText;

    private LinearLayout optionsListeningContainer;

    private Repository repository;

    private final List<Word> learnedWords = new ArrayList<>();

    private int currentIndex = 0;
    private int score = 0;
    private int mistakesCount = 0;
    private int fixedErrorsCount = 0;

    private Word currentWord;
    private boolean answeredCurrentQuestion = false;

    private boolean currentWordAlreadyInDictionary = false;
    private boolean dictionaryStateLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening_game);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        setupClicks();
        loadLearnedWords();
    }

    @Override
    public void onBackPressed() {
        goToTraining();
    }

    private void initViews() {
        progressListening = findViewById(R.id.progressListening);
        pbListening = findViewById(R.id.pbListening);
        tvListeningCounter = findViewById(R.id.tvListeningCounter);
        btnPlayListening = findViewById(R.id.btnPlayListening);

        cardListeningDictionaryState = findViewById(R.id.cardListeningDictionaryState);
        tvListeningDictionaryIcon = findViewById(R.id.tvListeningDictionaryIcon);
        tvListeningDictionaryText = findViewById(R.id.tvListeningDictionaryText);

        optionsListeningContainer = findViewById(R.id.optionsListeningContainer);
    }

    private void setupClicks() {
        // ✅ Крестик → TrainingFragment
        setupCloseToTrainingButton(R.id.btnUniClose);

        btnPlayListening.setOnClickListener(v -> {
            if (currentWord != null) {
                repository.speak(currentWord.getTerm(), false);
            }
        });

        cardListeningDictionaryState.setOnClickListener(v -> {
            if (!dictionaryStateLoading && !currentWordAlreadyInDictionary) {
                addCurrentWordToDictionary();
            }
        });
    }

    private void loadLearnedWords() {
        pbListening.setVisibility(View.VISIBLE);

        repository.getLearnedWordsForTraining(new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> data) {
                pbListening.setVisibility(View.GONE);

                learnedWords.clear();
                if (data != null) learnedWords.addAll(data);

                if (learnedWords.isEmpty()) {
                    Toast.makeText(ListeningGameActivity.this,
                            "Нет слов для тренировки", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                Collections.shuffle(learnedWords);
                showNextQuestion();
            }

            @Override
            public void onError(String error) {
                pbListening.setVisibility(View.GONE);
                Toast.makeText(ListeningGameActivity.this,
                        error, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void showNextQuestion() {
        if (currentIndex >= learnedWords.size()) {
            finish();
            return;
        }

        currentWord = learnedWords.get(currentIndex);
        answeredCurrentQuestion = false;
        currentWordAlreadyInDictionary = false;

        tvListeningDictionaryIcon.setText("📖");
        tvListeningDictionaryText.setText("Добавить в словарь");
        cardListeningDictionaryState.setAlpha(1.0f);

        tvListeningCounter.setText((currentIndex + 1) + " / " + learnedWords.size());

        if (progressListening != null) {
            int progress = (int) (((float) currentIndex / learnedWords.size()) * 100);
            progressListening.setProgress(progress);
        }

        createOptions();
        repository.speak(currentWord.getTerm(), false);
    }

    private void createOptions() {
        optionsListeningContainer.removeAllViews();

        List<String> options = new ArrayList<>();
        options.add(currentWord.getTranslation());

        List<Word> shuffled = new ArrayList<>(learnedWords);
        Collections.shuffle(shuffled);

        for (Word w : shuffled) {
            if (options.size() >= OPTIONS_COUNT) break;
            if (!options.contains(w.getTranslation())) {
                options.add(w.getTranslation());
            }
        }

        Collections.shuffle(options);

        for (String opt : options) {
            MaterialButton btn = new MaterialButton(this);
            btn.setText(opt);
            btn.setAllCaps(false);
            btn.setTextSize(15f);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(60)
            );
            params.setMargins(0, 0, 0, dp(10));
            btn.setLayoutParams(params);

            final String selectedOption = opt;

            btn.setOnClickListener(v -> {
                if (answeredCurrentQuestion) return;
                answeredCurrentQuestion = true;

                if (selectedOption.equals(currentWord.getTranslation())) {
                    score += POINTS_PER_CORRECT;
                    btn.setBackgroundTintList(
                            androidx.core.content.res.ResourcesCompat
                                    .getColorStateList(getResources(),
                                            R.color.listening_green, getTheme()));

                    new android.os.Handler().postDelayed(() -> {
                        currentIndex++;
                        showNextQuestion();
                    }, 700);

                } else {
                    mistakesCount++;
                    btn.setBackgroundTintList(
                            androidx.core.content.res.ResourcesCompat
                                    .getColorStateList(getResources(),
                                            android.R.color.holo_red_light, getTheme()));

                    new android.os.Handler().postDelayed(() -> {
                        currentIndex++;
                        showNextQuestion();
                    }, 1000);
                }
            });

            optionsListeningContainer.addView(btn);
        }
    }

    private void addCurrentWordToDictionary() {
        if (currentWord == null) return;

        dictionaryStateLoading = true;
        tvListeningDictionaryText.setText("Добавляем...");
        cardListeningDictionaryState.setAlpha(0.6f);

        repository.addWordToPersonalDictionary(currentWord, new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                dictionaryStateLoading = false;
                currentWordAlreadyInDictionary = true;

                tvListeningDictionaryIcon.setText("✅");
                tvListeningDictionaryText.setText("Добавлено в словарь");
                cardListeningDictionaryState.setAlpha(0.5f);

                Toast.makeText(ListeningGameActivity.this,
                        "Добавлено в словарь", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                dictionaryStateLoading = false;
                cardListeningDictionaryState.setAlpha(1.0f);
                tvListeningDictionaryText.setText("Добавить в словарь");

                Toast.makeText(ListeningGameActivity.this,
                        error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}