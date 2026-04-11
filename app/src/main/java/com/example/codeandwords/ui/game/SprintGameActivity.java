package com.example.codeandwords.ui.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SprintGameActivity extends AppCompatActivity {

    private static final long GAME_DURATION = 15000;
    private static final int WORDS_LIMIT = 20;
    private static final double XP_PER_WORD = 2.5;
    private static final int WIN_BONUS = 10;

    private TextView tvTimer;
    private TextView tvScore;
    private TextView tvWord;
    private TextView tvTranslation;
    private TextView tvCorrectionHeader;
    private TextView tvMistakesLeft;
    private TextView tvWordLabel;
    private TextView tvQuestionHint;
    private Button btnWrong;
    private Button btnCorrect;
    private ProgressBar progressBarLoading;
    private ProgressBar progressBarTimer;
    private LottieAnimationView confettiAnimation;
    private MaterialCardView correctionBannerCard;

    private Repository repository;
    private CountDownTimer timer;
    private List<Word> gameWords = new ArrayList<>();

    private int currentWordIndex = 0;
    private int score = 0;
    private int correctAnswers = 0;
    private boolean isGameActive = false;
    private boolean isAnimationLoaded = false;
    private boolean isCorrectionMode = false;

    private final Random random = new Random();
    private Word currentWord;
    private String displayedTranslation;
    private boolean isCorrectPair;

    private final List<SprintQuestion> mistakeQuestions = new ArrayList<>();
    private final List<SprintQuestion> correctionQuestions = new ArrayList<>();
    private int correctionIndex = 0;
    private int initialTotalWords = 0;
    private int fixedErrorsCount = 0;

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
        tvCorrectionHeader = findViewById(R.id.tvCorrectionHeader);
        tvMistakesLeft = findViewById(R.id.tvMistakesLeft);
        tvWordLabel = findViewById(R.id.tvWordLabel);
        tvQuestionHint = findViewById(R.id.tvQuestionHint);
        btnWrong = findViewById(R.id.btnWrong);
        btnCorrect = findViewById(R.id.btnCorrect);
        progressBarLoading = findViewById(R.id.progressBarLoading);
        progressBarTimer = findViewById(R.id.progressBarTimer);
        confettiAnimation = findViewById(R.id.confettiAnimation);
        correctionBannerCard = findViewById(R.id.correctionBannerCard);

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

        updateCorrectionUi();
    }

    private void updateCorrectionUi() {
        if (isCorrectionMode) {
            correctionBannerCard.setVisibility(View.VISIBLE);
            tvMistakesLeft.setText("Осталось исправить: " + (correctionQuestions.size() - correctionIndex));
            progressBarTimer.setVisibility(View.GONE);
            tvTimer.setText("Без времени");
            tvWordLabel.setText("ПРОВЕРЬ СЕБЯ");
            tvQuestionHint.setText("Исправьте ошибку");
        } else {
            correctionBannerCard.setVisibility(View.GONE);
            progressBarTimer.setVisibility(View.VISIBLE);
            tvWordLabel.setText("EN WORD");
            tvQuestionHint.setText("Это верно?");
        }
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

        initialTotalWords = gameWords.size();
        currentWordIndex = 0;
        correctAnswers = 0;
        score = 0;
        isGameActive = true;
        isCorrectionMode = false;
        correctionIndex = 0;
        fixedErrorsCount = 0;

        mistakeQuestions.clear();
        correctionQuestions.clear();

        tvScore.setText("Счет: 0");
        tvTimer.setText("15 сек");
        progressBarTimer.setVisibility(View.VISIBLE);

        updateCorrectionUi();
        startTimer();
        showNextWord();
    }

    private void startTimer() {
        progressBarTimer.setMax((int) GAME_DURATION / 1000);
        progressBarTimer.setProgress((int) GAME_DURATION / 1000);

        timer = new CountDownTimer(GAME_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(millisUntilFinished / 1000 + " сек");
                progressBarTimer.setProgress((int) (millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0 сек");
                finishMainPhase();
            }
        }.start();
    }

    private void showNextWord() {
        if (currentWordIndex >= gameWords.size()) {
            finishMainPhase();
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

    private void showCorrectionQuestion() {
        if (correctionIndex >= correctionQuestions.size()) {
            finishGame();
            return;
        }

        SprintQuestion question = correctionQuestions.get(correctionIndex);
        currentWord = question.word;
        displayedTranslation = question.displayedTranslation;
        isCorrectPair = question.isCorrectPair;

        tvWord.setText(currentWord.getTerm());
        tvTranslation.setText(displayedTranslation);

        updateCorrectionUi();
    }

    private void checkAnswer(boolean userSaidCorrect) {
        if (!isGameActive) return;

        if (!isCorrectionMode) {
            handleMainGameAnswer(userSaidCorrect);
        } else {
            handleCorrectionAnswer(userSaidCorrect);
        }
    }

    private void handleMainGameAnswer(boolean userSaidCorrect) {
        if (userSaidCorrect == isCorrectPair) {
            correctAnswers++;
            score += 10;

            repository.getCurrentUserId(userId -> {
                if (userId != -1) {
                    repository.incrementWordProgress(userId, currentWord.getId());
                }
            });
        } else {
            mistakeQuestions.add(new SprintQuestion(currentWord, displayedTranslation, isCorrectPair));
        }

        tvScore.setText("Счет: " + score);
        currentWordIndex++;
        showNextWord();
    }

    private void handleCorrectionAnswer(boolean userSaidCorrect) {
        if (userSaidCorrect == isCorrectPair) {
            fixedErrorsCount++;
            correctionIndex++;
            showCorrectionQuestion();
        } else {
            Toast.makeText(this, "Попробуйте ещё раз", Toast.LENGTH_SHORT).show();
        }
    }

    private void finishMainPhase() {
        if (!isGameActive) return;

        isGameActive = false;
        if (timer != null) {
            timer.cancel();
        }

        if (!mistakeQuestions.isEmpty()) {
            startCorrectionMode();
        } else {
            finishGame();
        }
    }

    private void startCorrectionMode() {
        isCorrectionMode = true;
        isGameActive = true;

        correctionQuestions.clear();
        correctionQuestions.addAll(mistakeQuestions);
        correctionIndex = 0;

        Toast.makeText(this, "Переходим к работе над ошибками", Toast.LENGTH_LONG).show();
        showCorrectionQuestion();
    }

    private void finishGame() {
        isGameActive = false;
        if (timer != null) {
            timer.cancel();
        }

        playLottieWinAnimation();

        int xpForWords = (int) (correctAnswers * XP_PER_WORD);
        boolean isPerfectGame = mistakeQuestions.isEmpty() && initialTotalWords > 0;
        int bonus = isPerfectGame ? WIN_BONUS : 0;
        int totalXp = xpForWords + bonus;

        repository.recordLessonCompletion(
                "SPRINT",
                getIntent().getLongExtra("THEME_ID", -1),
                totalXp,
                initialTotalWords,
                mistakeQuestions.size(),
                fixedErrorsCount,
                true
        );

        Intent intent = new Intent(this, GameResultActivity.class);
        intent.putExtra("SCORE", totalXp);
        intent.putExtra("TOTAL_WORDS", initialTotalWords);
        intent.putExtra("MISTAKES_COUNT", mistakeQuestions.size());
        startActivity(intent);
        finish();
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
        if (timer != null) {
            timer.cancel();
        }
    }

    private static class SprintQuestion {
        private final Word word;
        private final String displayedTranslation;
        private final boolean isCorrectPair;

        private SprintQuestion(Word word, String displayedTranslation, boolean isCorrectPair) {
            this.word = word;
            this.displayedTranslation = displayedTranslation;
            this.isCorrectPair = isCorrectPair;
        }
    }
}