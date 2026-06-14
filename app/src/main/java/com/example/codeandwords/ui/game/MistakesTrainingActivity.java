package com.example.codeandwords.ui.game;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Word;
import com.example.codeandwords.ui.base.BaseBackActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MistakesTrainingActivity extends BaseBackActivity {

    private static final int POINTS_PER_FIXED_MISTAKE = 5;
    private static final long LOAD_TIMEOUT_MS = 15_000;

    private ProgressBar progressMistakes;
    private ProgressBar pbMistakes;
    private TextView tvMistakesCounter;
    private TextView tvMistakeTranslation;
    private TextInputLayout tilMistakeAnswer;
    private TextInputEditText etMistakeAnswer;
    private com.google.android.material.button.MaterialButton btnCheckMistake;

    private MaterialCardView cardMistakeDictionaryState;
    private TextView tvMistakeDictionaryIcon;
    private TextView tvMistakeDictionaryText;

    private Repository repository;

    private final List<Word> mistakeWords = new ArrayList<>();
    private Word currentWord;
    private int currentIndex = 0;
    private int score = 0;
    private int mistakesMade = 0;
    private int fixedErrorsCount = 0;

    private boolean currentWordAlreadyInDictionary = false;
    private boolean dictionaryStateLoading = false;
    private boolean isLoading = false;

    private android.media.SoundPool soundPool;
    private int soundSuccess;
    private int soundError;
    private boolean soundsLoaded = false;

    private int colorCardDefault;
    private int colorCardAdded;
    private int colorBlue;
    private int colorGreen;
    private int colorGray;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorTextOnPrimary;

    private final Handler timeoutHandler = new Handler();
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mistakes_training);

        repository = Repository.getInstance(getApplicationContext());

        loadThemeColors();
        initSoundPool();
        initViews();

        // ✅ Крестик → TrainingFragment
        setupCloseToTrainingButton(R.id.btnUniClose);

        loadMistakeWords();
    }

    @Override
    public void onBackPressed() {
        goToTraining();
    }

    private void loadThemeColors() {
        colorCardDefault = ContextCompat.getColor(this, R.color.mistakes_dictionary_default_bg);
        colorCardAdded = ContextCompat.getColor(this, R.color.mistakes_dictionary_added_bg);
        colorBlue = ContextCompat.getColor(this, R.color.mistakes_blue);
        colorGreen = ContextCompat.getColor(this, R.color.mistakes_green);
        colorGray = ContextCompat.getColor(this, R.color.mistakes_gray);
        colorTextPrimary = ContextCompat.getColor(this, R.color.mistakes_text_primary);
        colorTextSecondary = ContextCompat.getColor(this, R.color.mistakes_text_secondary);
        colorTextOnPrimary = ContextCompat.getColor(this, R.color.mistakes_text_on_primary);
    }

    private void initSoundPool() {
        android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new android.media.SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build();

        soundSuccess = soundPool.load(this, R.raw.success, 1);
        soundError = soundPool.load(this, R.raw.error, 1);
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> soundsLoaded = true);
    }

    private void initViews() {
        progressMistakes = findViewById(R.id.progressMistakes);
        pbMistakes = findViewById(R.id.pbMistakes);
        tvMistakesCounter = findViewById(R.id.tvMistakesCounter);
        tvMistakeTranslation = findViewById(R.id.tvMistakeTranslation);
        tilMistakeAnswer = findViewById(R.id.tilMistakeAnswer);
        etMistakeAnswer = findViewById(R.id.etMistakeAnswer);
        btnCheckMistake = findViewById(R.id.btnCheckMistake);
        cardMistakeDictionaryState = findViewById(R.id.cardMistakeDictionaryState);
        tvMistakeDictionaryIcon = findViewById(R.id.tvMistakeDictionaryIcon);
        tvMistakeDictionaryText = findViewById(R.id.tvMistakeDictionaryText);

        btnCheckMistake.setOnClickListener(v -> checkAnswer());

        cardMistakeDictionaryState.setOnClickListener(v -> {
            if (dictionaryStateLoading) return;
            if (currentWordAlreadyInDictionary) {
                Toast.makeText(this, "Это слово уже есть в личном словаре", Toast.LENGTH_SHORT).show();
                return;
            }
            addCurrentWordToDictionary();
        });

        etMistakeAnswer.setOnEditorActionListener((v, actionId, event) -> {
            checkAnswer();
            return true;
        });
    }

    private void loadMistakeWords() {
        if (isLoading) return;
        isLoading = true;

        pbMistakes.setVisibility(View.VISIBLE);
        cardMistakeDictionaryState.setVisibility(View.GONE);

        timeoutRunnable = () -> {
            if (!isFinishing() && isLoading) {
                isLoading = false;
                pbMistakes.setVisibility(View.GONE);
                Toast.makeText(this,
                        "Загрузка занимает слишком долго. Проверьте соединение.",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS);

        repository.getMistakeWordsForTraining(new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> data) {
                cancelTimeout();
                isLoading = false;
                pbMistakes.setVisibility(View.GONE);

                mistakeWords.clear();
                if (data != null) mistakeWords.addAll(preparePlayableWords(data));

                if (mistakeWords.isEmpty()) {
                    Toast.makeText(MistakesTrainingActivity.this,
                            "Ошибок для повторения пока нет", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                Collections.shuffle(mistakeWords);
                currentIndex = 0;
                score = 0;
                mistakesMade = 0;
                fixedErrorsCount = 0;
                showNextQuestion();
            }

            @Override
            public void onError(String error) {
                cancelTimeout();
                isLoading = false;
                pbMistakes.setVisibility(View.GONE);
                Toast.makeText(MistakesTrainingActivity.this,
                        "Ошибка загрузки: " + error, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private List<Word> preparePlayableWords(List<Word> words) {
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

    private void showNextQuestion() {
        if (currentIndex >= mistakeWords.size()) {
            finishTraining();
            return;
        }

        currentWord = mistakeWords.get(currentIndex);
        tvMistakesCounter.setText((currentIndex + 1) + " / " + mistakeWords.size());
        tvMistakeTranslation.setText(currentWord.getTranslation());
        etMistakeAnswer.setText("");
        tilMistakeAnswer.setError(null);
        btnCheckMistake.setEnabled(true);

        refreshDictionaryState();
        updateProgress();
        etMistakeAnswer.requestFocus();
        showKeyboard();
    }

    private void refreshDictionaryState() {
        if (currentWord == null) {
            cardMistakeDictionaryState.setVisibility(View.GONE);
            return;
        }

        dictionaryStateLoading = true;
        currentWordAlreadyInDictionary = false;
        cardMistakeDictionaryState.setVisibility(View.VISIBLE);
        renderDictionaryLoadingState();

        repository.isWordInPersonalDictionary(currentWord,
                new Repository.DataCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isAdded) {
                        dictionaryStateLoading = false;
                        currentWordAlreadyInDictionary = isAdded != null && isAdded;
                        if (currentWordAlreadyInDictionary) renderDictionaryAddedState();
                        else renderDictionaryCanAddState();
                    }

                    @Override
                    public void onError(String error) {
                        dictionaryStateLoading = false;
                        currentWordAlreadyInDictionary = false;
                        renderDictionaryCanAddState();
                    }
                });
    }

    private void renderDictionaryLoadingState() {
        cardMistakeDictionaryState.setEnabled(false);
        cardMistakeDictionaryState.setCardBackgroundColor(colorCardDefault);
        cardMistakeDictionaryState.setStrokeColor(colorGray);
        cardMistakeDictionaryState.setStrokeWidth(dp(1));
        tvMistakeDictionaryIcon.setText("…");
        tvMistakeDictionaryIcon.setTextColor(colorGray);
        tvMistakeDictionaryText.setText("Проверяем");
        tvMistakeDictionaryText.setTextColor(colorTextPrimary);
    }

    private void renderDictionaryCanAddState() {
        cardMistakeDictionaryState.setEnabled(true);
        cardMistakeDictionaryState.setCardBackgroundColor(colorCardDefault);
        cardMistakeDictionaryState.setStrokeColor(colorBlue);
        cardMistakeDictionaryState.setStrokeWidth(dp(1));
        tvMistakeDictionaryIcon.setText("☆");
        tvMistakeDictionaryIcon.setTextColor(colorBlue);
        tvMistakeDictionaryText.setText("Сохранить");
        tvMistakeDictionaryText.setTextColor(colorTextPrimary);
    }

    private void renderDictionaryAddedState() {
        cardMistakeDictionaryState.setEnabled(true);
        cardMistakeDictionaryState.setCardBackgroundColor(colorCardAdded);
        cardMistakeDictionaryState.setStrokeColor(colorGreen);
        cardMistakeDictionaryState.setStrokeWidth(dp(1));
        tvMistakeDictionaryIcon.setText("★");
        tvMistakeDictionaryIcon.setTextColor(colorGreen);
        tvMistakeDictionaryText.setText("В словаре");
        tvMistakeDictionaryText.setTextColor(colorGreen);
    }

    private void addCurrentWordToDictionary() {
        if (currentWord == null) return;
        dictionaryStateLoading = true;
        renderDictionaryLoadingState();

        repository.addWordToPersonalDictionary(currentWord, new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                dictionaryStateLoading = false;
                currentWordAlreadyInDictionary = true;
                renderDictionaryAddedState();
                Toast.makeText(MistakesTrainingActivity.this,
                        "Слово добавлено в личный словарь", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                dictionaryStateLoading = false;
                if (error != null && error.toLowerCase().contains("уже есть")) {
                    currentWordAlreadyInDictionary = true;
                    renderDictionaryAddedState();
                } else {
                    currentWordAlreadyInDictionary = false;
                    renderDictionaryCanAddState();
                }
                Toast.makeText(MistakesTrainingActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAnswer() {
        if (currentWord == null) return;

        String userAnswer = etMistakeAnswer.getText() == null
                ? "" : etMistakeAnswer.getText().toString().trim();
        String correctAnswer = currentWord.getTerm() == null
                ? "" : currentWord.getTerm().trim();

        if (userAnswer.isEmpty()) {
            tilMistakeAnswer.setError("Введите термин");
            return;
        }

        btnCheckMistake.setEnabled(false);

        if (userAnswer.equalsIgnoreCase(correctAnswer)) {
            playSound(soundSuccess);
            tilMistakeAnswer.setError(null);
            score += POINTS_PER_FIXED_MISTAKE;
            fixedErrorsCount++;

            repository.resolveWordMistake(currentWord, new Repository.DataCallback<Void>() {
                @Override public void onSuccess(Void data) { }
                @Override public void onError(String error) { }
            });

            final Word fixedWord = currentWord;
            repository.getCurrentUserId(userId -> {
                if (userId != null && userId > 0 && fixedWord != null) {
                    repository.markSprintPassed(userId, fixedWord.getId());
                    repository.markMatchingPassed(userId, fixedWord.getId());
                    repository.markWritingPassed(userId, fixedWord.getId());
                }
            });

            new Handler().postDelayed(() -> {
                currentIndex++;
                showNextQuestion();
            }, 600);

        } else {
            playSound(soundError);
            mistakesMade++;
            repository.recordWordMistake(currentWord);
            tilMistakeAnswer.setError("Правильно: " + correctAnswer);

            new Handler().postDelayed(() -> {
                currentIndex++;
                showNextQuestion();
            }, 1300);
        }
    }

    private void updateProgress() {
        if (mistakeWords.isEmpty()) {
            progressMistakes.setProgress(0);
            return;
        }
        int progress = (currentIndex * 100) / mistakeWords.size();
        progressMistakes.setProgress(progress);
    }

    private void finishTraining() {
        hideKeyboard();
        progressMistakes.setProgress(100);

        repository.recordLessonCompletion("TRAINING_MISTAKES", null, 0,
                mistakeWords.size(), mistakesMade, fixedErrorsCount, false);

        Intent intent = new Intent(this, GameResultActivity.class);
        intent.putExtra("SCORE", 0);
        intent.putExtra("TOTAL_WORDS", mistakeWords.size());
        intent.putExtra("MISTAKES_COUNT", mistakesMade);
        intent.putExtra("IS_TRAINING", true);
        startActivity(intent);
        finish();
    }

    private void playSound(int soundId) {
        if (soundsLoaded && soundPool != null) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void showKeyboard() {
        etMistakeAnswer.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etMistakeAnswer,
                        android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimeout();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}