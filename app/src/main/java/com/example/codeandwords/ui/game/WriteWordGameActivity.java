package com.example.codeandwords.ui.game;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Word;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WriteWordGameActivity extends AppCompatActivity {

    private TextView tvScore;
    private TextView tvMistakes;
    private TextView tvTranslation;
    private TextView tvCorrectionHeader;
    private TextView tvMistakesLeft;
    private MaterialCardView correctionBannerCard;
    private TextInputLayout tilInput;
    private TextInputEditText etInput;
    private Button btnCheck;
    private Button btnAddToDictionary;
    private ProgressBar progressBar;
    private LottieAnimationView lottieConfetti;

    private Repository repository;
    private Long themeId;
    private List<Word> allWords = new ArrayList<>();
    private List<Word> mistakenWords = new ArrayList<>();
    private Word currentWord;

    private int score = 0;
    private boolean isCorrectionMode = false;
    private int totalInitialWords = 0;
    private int totalMistakesMade = 0;
    private int fixedErrorsCount = 0;

    private SoundPool soundPool;
    private int soundSuccess;
    private int soundError;
    private boolean soundsLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_word_game);

        initSoundPool();
        initViews();

        repository = new Repository(this);
        themeId = getIntent().getLongExtra("THEME_ID", -1);

        if (themeId != -1) {
            loadWords();
        } else {
            finish();
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
        btnAddToDictionary = findViewById(R.id.btnAddToDictionary);
        progressBar = findViewById(R.id.progressBar);
        lottieConfetti = findViewById(R.id.lottieConfetti);

        btnCheck.setOnClickListener(v -> checkAnswer());

        btnAddToDictionary.setOnClickListener(v -> {
            if (currentWord == null) {
                Toast.makeText(this, "Слово ещё не загружено", Toast.LENGTH_SHORT).show();
                return;
            }

            repository.addWordToPersonalDictionary(currentWord, new Repository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(WriteWordGameActivity.this, "Слово добавлено в личный словарь", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(WriteWordGameActivity.this, error, Toast.LENGTH_SHORT).show();
                }
            });
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

    private void loadWords() {
        progressBar.setVisibility(View.VISIBLE);
        repository.getWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                progressBar.setVisibility(View.GONE);
                if (words.isEmpty()) {
                    Toast.makeText(WriteWordGameActivity.this, "Нет слов!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    allWords = new ArrayList<>(words);
                    totalInitialWords = allWords.size();
                    fixedErrorsCount = 0;
                    Collections.shuffle(allWords);
                    showNextQuestion();
                }
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                finish();
            }
        });
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
        tilInput.setError(null);
        btnCheck.setEnabled(true);
        btnAddToDictionary.setEnabled(true);

        updateCorrectionUi();
        etInput.requestFocus();
    }

    private void checkAnswer() {
        String userAnswer = etInput.getText().toString().trim();
        String correctAnswer = currentWord.getTerm();

        if (userAnswer.isEmpty()) {
            tilInput.setError("Введите слово");
            return;
        }

        btnCheck.setEnabled(false);

        if (userAnswer.equalsIgnoreCase(correctAnswer)) {
            playGameSound(soundSuccess);

            if (isCorrectionMode) {
                mistakenWords.remove(0);
                fixedErrorsCount++;
            } else {
                score += 20;
                tvScore.setText("Очки: " + score);
            }

            new Handler().postDelayed(this::showNextQuestion, 600);

        } else {
            playGameSound(soundError);

            if (!isCorrectionMode) {
                mistakenWords.add(currentWord);
                totalMistakesMade++;
                tvMistakes.setText("Ошибок: " + totalMistakesMade);
            } else {
                Collections.shuffle(mistakenWords);
            }

            updateCorrectionUi();
            tilInput.setError("Правильно: " + correctAnswer);
            new Handler().postDelayed(this::showNextQuestion, 1500);
        }
    }

    private void playGameSound(int soundId) {
        if (soundsLoaded && soundPool != null) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    private void finishGame() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        repository.recordLessonCompletion(
                "WRITE_WORD",
                themeId,
                score,
                totalInitialWords,
                totalMistakesMade,
                fixedErrorsCount,
                false
        );

        Intent intent = new Intent(this, GameResultActivity.class);
        intent.putExtra("SCORE", score);
        intent.putExtra("TOTAL_WORDS", totalInitialWords);
        intent.putExtra("MISTAKES_COUNT", totalMistakesMade);
        startActivity(intent);

        finish();
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