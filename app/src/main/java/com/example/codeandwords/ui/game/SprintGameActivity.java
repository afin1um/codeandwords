package com.example.codeandwords.ui.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SprintGameActivity extends AppCompatActivity {

    private static final long GAME_DURATION = 15000;
    private static final int WORDS_LIMIT = 20;
    private static final double XP_PER_WORD = 2.5;
    private static final int WIN_BONUS = 10;

    private TextView tvTimer, tvScore, tvWord, tvTranslation;
    private Button btnWrong, btnCorrect;
    private ProgressBar progressBarLoading, progressBarTimer;
    private LottieAnimationView confettiAnimation;

    private Repository repository;
    private CountDownTimer timer;
    private List<Word> gameWords = new ArrayList<>();

    private int currentWordIndex = 0;
    private int score = 0;
    private int correctAnswers = 0;
    private boolean isGameActive = false;
    private boolean isAnimationLoaded = false;

    private Random random = new Random();
    private Word currentWord;
    private String displayedTranslation;
    private boolean isCorrectPair;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sprint_game);

        repository = new Repository(this);
        initViews();

        long themeId = getIntent().getLongExtra("THEME_ID", -1);
        if (themeId != -1) {
            loadWords(themeId);
        } else {
            finish();
        }
    }

    private void initViews() {
        tvTimer = findViewById(R.id.tvTimer);
        tvScore = findViewById(R.id.tvScore);
        tvWord = findViewById(R.id.tvWord);
        tvTranslation = findViewById(R.id.tvTranslation);
        btnWrong = findViewById(R.id.btnWrong);
        btnCorrect = findViewById(R.id.btnCorrect);
        progressBarLoading = findViewById(R.id.progressBarLoading);
        progressBarTimer = findViewById(R.id.progressBarTimer);
        confettiAnimation = findViewById(R.id.confettiAnimation);

        if (confettiAnimation != null) {
            confettiAnimation.setFailureListener(throwable -> isAnimationLoaded = false);
            try {
                confettiAnimation.setAnimationFromUrl("https://lottie.host/cdf24a8a-39d5-4c33-9b29-4cc066c6ea89/gctBV2eOOq.lottie");
                isAnimationLoaded = true;
            } catch (Exception e) {
                isAnimationLoaded = false;
            }
        }

        btnCorrect.setOnClickListener(v -> checkAnswer(true));
        btnWrong.setOnClickListener(v -> checkAnswer(false));
    }

    private void loadWords(long themeId) {
        progressBarLoading.setVisibility(View.VISIBLE);
        repository.getWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> data) {
                progressBarLoading.setVisibility(View.GONE);
                if (data.size() < 5) {
                    Toast.makeText(SprintGameActivity.this, "Мало слов для игры", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                startGame(data);
            }

            @Override
            public void onError(String error) {
                progressBarLoading.setVisibility(View.GONE);
                Toast.makeText(SprintGameActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void startGame(List<Word> allWords) {
        Collections.shuffle(allWords);
        int limit = Math.min(allWords.size(), WORDS_LIMIT);
        gameWords = new ArrayList<>(allWords.subList(0, limit));

        currentWordIndex = 0;
        correctAnswers = 0;
        score = 0;
        isGameActive = true;
        tvScore.setText("Счет: 0");

        startTimer();
        showNextWord();
    }

    private void startTimer() {
        progressBarTimer.setMax((int) GAME_DURATION / 1000);
        timer = new CountDownTimer(GAME_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(millisUntilFinished / 1000 + " сек");
                progressBarTimer.setProgress((int) (millisUntilFinished / 1000));
            }
            @Override
            public void onFinish() {
                tvTimer.setText("0 сек");
                finishGame(false);
            }
        }.start();
    }

    private void showNextWord() {
        if (currentWordIndex >= gameWords.size()) {
            finishGame(true);
            return;
        }

        currentWord = gameWords.get(currentWordIndex);
        tvWord.setText(currentWord.getTerm());

        if (random.nextBoolean()) {
            displayedTranslation = currentWord.getTranslation();
            isCorrectPair = true;
        } else {
            Word randomWord = gameWords.get(random.nextInt(gameWords.size()));
            while (randomWord.getId().equals(currentWord.getId()) && gameWords.size() > 1) {
                randomWord = gameWords.get(random.nextInt(gameWords.size()));
            }
            displayedTranslation = randomWord.getTranslation();
            isCorrectPair = false;
        }
        tvTranslation.setText(displayedTranslation);
    }

    private void checkAnswer(boolean userSaidCorrect) {
        if (!isGameActive) return;

        if (userSaidCorrect == isCorrectPair) {
            correctAnswers++;
            score += 10;

            // --- НОВОЕ: Прокачиваем слово в базе данных (Пункт 1.2.з) ---
            repository.getCurrentUserId(userId -> {
                if (userId != -1) {
                    repository.incrementWordProgress(userId, currentWord.getId());
                }
            });
        }

        tvScore.setText("Счет: " + score);
        currentWordIndex++;
        showNextWord();
    }

    private void finishGame(boolean isFinishedList) {
        isGameActive = false;
        if (timer != null) timer.cancel();

        playLottieWinAnimation();

        int xpForWords = (int) (correctAnswers * XP_PER_WORD);
        boolean isPerfectGame = isFinishedList && (correctAnswers == gameWords.size());
        int bonus = isPerfectGame ? WIN_BONUS : 0;
        int totalXp = xpForWords + bonus;

        if (totalXp > 0) {
            repository.addXp(totalXp);
        }

        String title = isPerfectGame ? "Идеально!" : (isFinishedList ? "Завершено" : "Время вышло");
        StringBuilder message = new StringBuilder();
        message.append("Правильно: ").append(correctAnswers).append(" из ").append(gameWords.size());
        message.append("\n\nНаграда: +").append(totalXp).append(" XP");

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message.toString())
                .setCancelable(false)
                .setPositiveButton("В меню", (dialog, which) -> finish())
                .setNegativeButton("Еще раз", (dialog, which) -> recreate())
                .show();
    }

    private void playLottieWinAnimation() {
        if (confettiAnimation != null && isAnimationLoaded) {
            confettiAnimation.setVisibility(View.VISIBLE);
            confettiAnimation.playAnimation();
            confettiAnimation.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    confettiAnimation.setVisibility(View.GONE);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}