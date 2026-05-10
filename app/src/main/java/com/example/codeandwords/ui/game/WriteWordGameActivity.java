package com.example.codeandwords.ui.game;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Word;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WriteWordGameActivity extends AppCompatActivity {

    private static final String TRAINING_MODE_LEARNED_WORDS = "LEARNED_WORDS";

    private static final int COLOR_BLUE = Color.rgb(28, 176, 246);
    private static final int COLOR_GREEN = Color.rgb(88, 204, 2);
    private static final int COLOR_GRAY = Color.rgb(138, 154, 165);
    private static final int COLOR_RED = Color.rgb(255, 75, 75);

    private int colorCardDefault;
    private int colorCardSuccess;
    private int colorTextPrimary;

    private TextView tvScore;
    private TextView tvMistakes;
    private TextView tvTranslation;
    private TextView tvCorrectionHeader;
    private TextView tvMistakesLeft;
    private MaterialCardView correctionBannerCard;
    private FrameLayout tilInput;            // ✅ Изменено с TextInputLayout
    private EditText etInput;                // ✅ Изменено с TextInputEditText
    private Button btnCheck;
    private ProgressBar progressBar;
    private LottieAnimationView lottieConfetti;

    private MaterialCardView cardWriteDictionaryState;
    private TextView tvWriteDictionaryIcon;
    private TextView tvWriteDictionaryText;

    private Repository repository;
    private Long themeId;

    private boolean isTrainingMode = false;

    private List<Word> allWords = new ArrayList<>();
    private List<Word> mistakenWords = new ArrayList<>();
    private Word currentWord;

    private int score = 0;
    private boolean isCorrectionMode = false;
    private int totalInitialWords = 0;
    private int totalMistakesMade = 0;
    private int fixedErrorsCount = 0;

    private boolean currentWordAlreadyInDictionary = false;
    private boolean dictionaryStateLoading = false;

    private SoundPool soundPool;
    private int soundSuccess;
    private int soundError;
    private boolean soundsLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_word_game);

        initThemeColors();

        initSoundPool();
        initViews();

        repository = new Repository(this);

        String trainingMode = getIntent().getStringExtra("TRAINING_MODE");
        isTrainingMode = TRAINING_MODE_LEARNED_WORDS.equals(trainingMode);

        if (isTrainingMode) {
            themeId = null;
            loadLearnedWordsForTraining();
        } else {
            themeId = getIntent().getLongExtra("THEME_ID", -1);

            if (themeId != -1) {
                loadWordsByTheme();
            } else {
                Toast.makeText(this, "Тема не выбрана", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build();

        soundSuccess = soundPool.load(this, R.raw.success, 1);
        soundError = soundPool.load(this, R.raw.error, 1);

        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> soundsLoaded = true);
    }

    private void initViews() {
        tvScore = findViewById(R.id.tvScore);
        tvMistakes = findViewById(R.id.tvMistakes);
        tvTranslation = findViewById(R.id.tvTranslation);
        tvCorrectionHeader = findViewById(R.id.tvCorrectionHeader);
        tvMistakesLeft = findViewById(R.id.tvMistakesLeft);
        correctionBannerCard = findViewById(R.id.correctionBannerCard);
        tilInput = findViewById(R.id.tilInput);
        etInput = findViewById(R.id.etInput);
        btnCheck = findViewById(R.id.btnCheck);
        progressBar = findViewById(R.id.progressBar);
        lottieConfetti = findViewById(R.id.lottieConfetti);

        cardWriteDictionaryState = findViewById(R.id.cardWriteDictionaryState);
        tvWriteDictionaryIcon = findViewById(R.id.tvWriteDictionaryIcon);
        tvWriteDictionaryText = findViewById(R.id.tvWriteDictionaryText);

        btnCheck.setOnClickListener(v -> checkAnswer());

        cardWriteDictionaryState.setOnClickListener(v -> {
            if (dictionaryStateLoading) {
                return;
            }

            if (currentWordAlreadyInDictionary) {
                Toast.makeText(
                        WriteWordGameActivity.this,
                        "Это слово уже есть в личном словаре",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            addCurrentWordToDictionary();
        });

        updateCorrectionUi();
    }

    private void updateCorrectionUi() {
        if (isCorrectionMode) {
            correctionBannerCard.setVisibility(View.VISIBLE);
            tvMistakesLeft.setText("Осталось исправить: " + mistakenWords.size());
        } else {
            correctionBannerCard.setVisibility(View.GONE);
        }
    }

    private void loadWordsByTheme() {
        progressBar.setVisibility(View.VISIBLE);
        cardWriteDictionaryState.setVisibility(View.GONE);

        repository.getWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                progressBar.setVisibility(View.GONE);

                List<Word> playableWords = preparePlayableWords(words);

                if (playableWords.size() < 5) {
                    Toast.makeText(
                            WriteWordGameActivity.this,
                            "Для режима нужно минимум 5 терминов",
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                    return;
                }

                startGame(playableWords);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(WriteWordGameActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadLearnedWordsForTraining() {
        progressBar.setVisibility(View.VISIBLE);
        cardWriteDictionaryState.setVisibility(View.GONE);

        repository.getLearnedWordsForTraining(new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                progressBar.setVisibility(View.GONE);

                List<Word> playableWords = preparePlayableWords(words);

                if (playableWords.isEmpty()) {
                    Toast.makeText(
                            WriteWordGameActivity.this,
                            "Пока нет изученных слов для тренировки",
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                    return;
                }

                startGame(playableWords);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(WriteWordGameActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void startGame(List<Word> playableWords) {
        allWords = new ArrayList<>(playableWords);
        Collections.shuffle(allWords);

        totalInitialWords = allWords.size();
        fixedErrorsCount = 0;
        totalMistakesMade = 0;
        score = 0;
        isCorrectionMode = false;

        mistakenWords.clear();

        if (isTrainingMode) {
            tvScore.setText("Тренировка");
        } else {
            tvScore.setText("Очки: 0");
        }

        tvMistakes.setText("Ошибок: 0");
        cardWriteDictionaryState.setVisibility(View.GONE);

        showNextQuestion();
    }

    private List<Word> preparePlayableWords(List<Word> words) {
        List<Word> result = new ArrayList<>();

        if (words == null) {
            return result;
        }

        for (Word word : words) {
            if (word == null) continue;

            String term = word.getTerm() == null ? "" : word.getTerm().trim();
            String translation = word.getTranslation() == null ? "" : word.getTranslation().trim();

            if (!term.isEmpty() && !translation.isEmpty()) {
                result.add(word);
            }
        }

        return result;
    }

    private void showNextQuestion() {
        if (allWords.isEmpty() && mistakenWords.isEmpty()) {
            finishGame();
            return;
        }

        if (!allWords.isEmpty()) {
            currentWord = allWords.remove(0);
        } else {
            if (!isCorrectionMode) {
                isCorrectionMode = true;
                updateCorrectionUi();
            }

            currentWord = mistakenWords.get(0);
        }

        tvTranslation.setText(currentWord.getTranslation());
        etInput.setText("");
        resetInputBorder();
        btnCheck.setEnabled(true);

        updateCorrectionUi();
        refreshDictionaryState();

        etInput.requestFocus();
        showKeyboard();
    }

    /**
     * ✅ Сбрасывает рамку поля ввода в нейтральное состояние (синюю).
     */
    private void resetInputBorder() {
        setInputBorder(COLOR_BLUE);
    }

    /**
     * ✅ Меняет цвет рамки поля ввода (для индикации ошибки).
     */
    private void setInputBorder(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorCardDefault);
        bg.setStroke(dp(1), color);
        bg.setCornerRadius(dp(14));
        tilInput.setBackground(bg);
    }

    private void refreshDictionaryState() {
        if (currentWord == null) {
            cardWriteDictionaryState.setVisibility(View.GONE);
            return;
        }

        dictionaryStateLoading = true;
        currentWordAlreadyInDictionary = false;

        cardWriteDictionaryState.setVisibility(View.VISIBLE);
        renderDictionaryLoadingState();

        repository.isWordInPersonalDictionary(currentWord, new Repository.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isAdded) {
                dictionaryStateLoading = false;
                currentWordAlreadyInDictionary = isAdded != null && isAdded;

                if (currentWordAlreadyInDictionary) {
                    renderDictionaryAddedState();
                } else {
                    renderDictionaryCanAddState();
                }
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
        cardWriteDictionaryState.setEnabled(false);
        cardWriteDictionaryState.setCardBackgroundColor(colorCardDefault);
        cardWriteDictionaryState.setStrokeColor(COLOR_GRAY);
        cardWriteDictionaryState.setStrokeWidth(dp(1));

        tvWriteDictionaryIcon.setText("…");
        tvWriteDictionaryIcon.setTextColor(COLOR_GRAY);

        tvWriteDictionaryText.setText("Проверяем");
        tvWriteDictionaryText.setTextColor(colorTextPrimary);
    }

    private void renderDictionaryCanAddState() {
        cardWriteDictionaryState.setEnabled(true);
        cardWriteDictionaryState.setCardBackgroundColor(colorCardDefault);
        cardWriteDictionaryState.setStrokeColor(COLOR_BLUE);
        cardWriteDictionaryState.setStrokeWidth(dp(1));

        tvWriteDictionaryIcon.setText("☆");
        tvWriteDictionaryIcon.setTextColor(COLOR_BLUE);

        tvWriteDictionaryText.setText("Сохранить");
        tvWriteDictionaryText.setTextColor(colorTextPrimary);
    }

    private void renderDictionaryAddedState() {
        cardWriteDictionaryState.setEnabled(true);
        cardWriteDictionaryState.setCardBackgroundColor(colorCardSuccess);
        cardWriteDictionaryState.setStrokeColor(COLOR_GREEN);
        cardWriteDictionaryState.setStrokeWidth(dp(1));

        tvWriteDictionaryIcon.setText("★");
        tvWriteDictionaryIcon.setTextColor(COLOR_GREEN);

        tvWriteDictionaryText.setText("В словаре");
        tvWriteDictionaryText.setTextColor(COLOR_GREEN);
    }

    private void addCurrentWordToDictionary() {
        if (currentWord == null) {
            Toast.makeText(this, "Слово ещё не загружено", Toast.LENGTH_SHORT).show();
            return;
        }

        dictionaryStateLoading = true;
        renderDictionaryLoadingState();

        repository.addWordToPersonalDictionary(currentWord, new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                dictionaryStateLoading = false;
                currentWordAlreadyInDictionary = true;
                renderDictionaryAddedState();

                Toast.makeText(
                        WriteWordGameActivity.this,
                        "Слово добавлено в личный словарь",
                        Toast.LENGTH_SHORT
                ).show();
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

                Toast.makeText(WriteWordGameActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAnswer() {
        if (currentWord == null) {
            return;
        }

        String userAnswer = etInput.getText() == null
                ? ""
                : etInput.getText().toString().trim();

        String correctAnswer = currentWord.getTerm() == null
                ? ""
                : currentWord.getTerm().trim();

        if (userAnswer.isEmpty()) {
            setInputBorder(COLOR_RED);
            Toast.makeText(this, "Введите слово", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCheck.setEnabled(false);

        if (userAnswer.equalsIgnoreCase(correctAnswer)) {
            handleCorrectAnswer();
        } else {
            handleWrongAnswer(correctAnswer);
        }
    }

    private void handleCorrectAnswer() {
        playGameSound(soundSuccess);
        setInputBorder(COLOR_GREEN);

        if (isCorrectionMode) {
            mistakenWords.remove(0);
            fixedErrorsCount++;

            if (isTrainingMode) {
                repository.resolveWordMistake(currentWord, new Repository.DataCallback<Void>() {
                    @Override public void onSuccess(Void data) {}
                    @Override public void onError(String error) {}
                });
            }

        } else {
            if (!isTrainingMode) {
                score += 20;
                tvScore.setText("Очки: " + score);
            }
        }

        new Handler().postDelayed(this::showNextQuestion, 600);
    }

    private void handleWrongAnswer(String correctAnswer) {
        playGameSound(soundError);
        setInputBorder(COLOR_RED);

        if (!isCorrectionMode) {
            mistakenWords.add(currentWord);
            totalMistakesMade++;
            tvMistakes.setText("Ошибок: " + totalMistakesMade);

            if (isTrainingMode) {
                repository.recordWordMistake(currentWord);
            }

        } else {
            Collections.shuffle(mistakenWords);

            if (isTrainingMode) {
                repository.recordWordMistake(currentWord);
            }
        }

        updateCorrectionUi();

        Toast.makeText(this, "Правильно: " + correctAnswer, Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(this::showNextQuestion, 1500);
    }

    private void playGameSound(int soundId) {
        if (soundsLoaded && soundPool != null) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    private void finishGame() {
        hideKeyboard();

        int finalScore = isTrainingMode ? 0 : score;
        String lessonType = isTrainingMode ? "TRAINING_WORDS" : "WRITE_WORD";

        repository.recordLessonCompletion(
                lessonType,
                themeId,
                finalScore,
                totalInitialWords,
                totalMistakesMade,
                fixedErrorsCount,
                false
        );

        Intent intent = new Intent(this, GameResultActivity.class);
        intent.putExtra("SCORE", finalScore);
        intent.putExtra("TOTAL_WORDS", totalInitialWords);
        intent.putExtra("MISTAKES_COUNT", totalMistakesMade);
        intent.putExtra("IS_TRAINING", isTrainingMode);
        startActivity(intent);

        finish();
    }

    private void showKeyboard() {
        etInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void initThemeColors() {
        boolean isDarkTheme =
                (getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;

        // ✅ Берём цвета из ресурсов (теперь они зависят от темы автоматически)
        colorCardDefault = ContextCompat.getColor(this, R.color.app_card_bg);
        colorTextPrimary = ContextCompat.getColor(this, R.color.app_text_primary);

        if (isDarkTheme) {
            colorCardSuccess = Color.rgb(9, 37, 26);
        } else {
            colorCardSuccess = Color.rgb(232, 255, 232);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}