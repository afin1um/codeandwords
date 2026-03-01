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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WriteWordGameActivity extends AppCompatActivity {

    private TextView tvScore, tvMistakes, tvTranslation, tvCorrectionHeader;
    private TextInputLayout tilInput;
    private TextInputEditText etInput;
    private Button btnCheck;
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

    // Звуковой движок SoundPool
    private SoundPool soundPool;
    private int soundSuccess, soundError;
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

        // Предварительная загрузка звуков
        soundSuccess = soundPool.load(this, R.raw.success, 1);
        soundError = soundPool.load(this, R.raw.error, 1);

        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> soundsLoaded = true);
    }

    private void initViews() {
        tvScore = findViewById(R.id.tvScore);
        tvMistakes = findViewById(R.id.tvMistakes);
        tvTranslation = findViewById(R.id.tvTranslation);
        tvCorrectionHeader = findViewById(R.id.tvCorrectionHeader);
        tilInput = findViewById(R.id.tilInput);
        etInput = findViewById(R.id.etInput);
        btnCheck = findViewById(R.id.btnCheck);
        progressBar = findViewById(R.id.progressBar);
        lottieConfetti = findViewById(R.id.lottieConfetti);

        btnCheck.setOnClickListener(v -> checkAnswer());
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
                tvCorrectionHeader.setVisibility(View.VISIBLE);
                tvCorrectionHeader.setText("ИСПРАВЛЯЕМ ОШИБКИ");
            }
            currentWord = mistakenWords.get(0);
        }

        tvTranslation.setText(currentWord.getTranslation());
        etInput.setText("");
        tilInput.setError(null);
        btnCheck.setEnabled(true);

        // Фокус на поле ввода и открытие клавиатуры
        etInput.requestFocus();
    }

    private void checkAnswer() {
        String userAnswer = etInput.getText().toString().trim();
        String correctAnswer = currentWord.getTerm();

        if (userAnswer.isEmpty()) {
            tilInput.setError("Введите слово");
            return;
        }

        btnCheck.setEnabled(false); // Защита от двойного клика

        if (userAnswer.equalsIgnoreCase(correctAnswer)) {
            // ПРАВИЛЬНО
            playGameSound(soundSuccess);

            if (isCorrectionMode) {
                mistakenWords.remove(0);
            } else {
                score += 20;
                tvScore.setText("Очки: " + score);
            }

            // Быстрый переход к следующему вопросу
            new Handler().postDelayed(this::showNextQuestion, 600);

        } else {
            // ОШИБКА
            playGameSound(soundError);

            if (!isCorrectionMode) {
                mistakenWords.add(currentWord);
                totalMistakesMade++;
                tvMistakes.setText("Ошибок: " + mistakenWords.size());
            } else {
                Collections.shuffle(mistakenWords);
            }

            tilInput.setError("Правильно: " + correctAnswer);

            // Пауза подольше, чтобы игрок увидел правильный ответ
            new Handler().postDelayed(this::showNextQuestion, 1500);
        }
    }

    private void playGameSound(int soundId) {
        if (soundsLoaded && soundPool != null) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    private void finishGame() {
        repository.addXp(score);

        // Скрываем клавиатуру перед выходом
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

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