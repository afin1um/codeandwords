package com.example.codeandwords.ui.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Word;
import com.example.codeandwords.ui.base.BaseBackActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// Режим спринта: проверка знания перевода на время с последующей работой над ошибками
public class SprintGameActivity extends BaseBackActivity {

    private static final long GAME_DURATION = 15000;
    private static final int WORDS_LIMIT = 20;
    private static final double XP_PER_WORD = 2.5;
    private static final int WIN_BONUS = 10;

    private static final int COLOR_BLUE = Color.rgb(28, 176, 246);
    private static final int COLOR_GREEN = Color.rgb(88, 204, 2);
    private static final int COLOR_GRAY = Color.rgb(138, 154, 165);

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

    private MaterialCardView cardSprintDictionaryState;
    private TextView tvSprintDictionaryIcon;
    private TextView tvSprintDictionaryText;

    private Repository repository;
    private CountDownTimer timer;
    private List<Word> gameWords = new ArrayList<>();

    private Long themeId;
    private String themeTitle;

    private int currentWordIndex = 0;
    private int score = 0;
    private int correctAnswers = 0;
    private boolean isGameActive = false;
    private boolean isAnimationLoaded = false;
    private boolean isCorrectionMode = false;

    private boolean currentWordAlreadyInDictionary = false;
    private boolean dictionaryStateLoading = false;

    private final Random random = new Random();
    private Word currentWord;
    private String displayedTranslation;
    private boolean isCorrectPair;

    // Ошибки сохраняются для последующего исправления
    private final List<SprintQuestion> mistakeQuestions = new ArrayList<>();
    private final List<SprintQuestion> correctionQuestions = new ArrayList<>();
    private int correctionIndex = 0;
    private int initialTotalWords = 0;
    private int fixedErrorsCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sprint_game);

        repository = Repository.getInstance(getApplicationContext());
        themeId = getIntent().getLongExtra("THEME_ID", -1);
        themeTitle = getIntent().getStringExtra("THEME_TITLE");

        initViews();

        if (themeId != -1) {
            loadWords(themeId);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        goBackToGameSelection();
    }

    // Возврат в экран выбора режима текущей темы
    private void goBackToGameSelection() {
        Intent intent = new Intent(this, GameSelectionActivity.class);
        intent.putExtra("THEME_ID", themeId != null ? themeId : -1L);
        intent.putExtra("THEME_TITLE", themeTitle);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
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

        cardSprintDictionaryState = findViewById(R.id.cardSprintDictionaryState);
        tvSprintDictionaryIcon = findViewById(R.id.tvSprintDictionaryIcon);
        tvSprintDictionaryText = findViewById(R.id.tvSprintDictionaryText);

        View btnClose = findViewById(R.id.btnUniClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> goBackToGameSelection());
        }

        if (confettiAnimation != null) {
            confettiAnimation.setFailureListener(throwable -> isAnimationLoaded = false);
            try {
                confettiAnimation.setAnimationFromUrl(
                        "https://lottie.host/cdf24a8a-39d5-4c33-9b29-4cc066c6ea89/gctBV2eOOq.lottie");
                isAnimationLoaded = true;
            } catch (Exception e) {
                isAnimationLoaded = false;
            }
        }

        btnCorrect.setOnClickListener(v -> checkAnswer(true));
        btnWrong.setOnClickListener(v -> checkAnswer(false));

        cardSprintDictionaryState.setVisibility(View.GONE);
        cardSprintDictionaryState.setOnClickListener(v -> {
            if (dictionaryStateLoading) return;
            if (currentWord == null) {
                Toast.makeText(this, "Слово ещё не загружено", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentWordAlreadyInDictionary) {
                Toast.makeText(this, "Это слово уже есть в личном словаре", Toast.LENGTH_SHORT).show();
                return;
            }
            addCurrentWordToDictionary();
        });

        updateCorrectionUi();
    }

    // Переключает интерфейс между основным режимом и режимом исправления ошибок
    private void updateCorrectionUi() {
        if (isCorrectionMode) {
            correctionBannerCard.setVisibility(View.VISIBLE);
            tvMistakesLeft.setText("Осталось исправить: "
                    + (correctionQuestions.size() - correctionIndex));
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
        cardSprintDictionaryState.setVisibility(View.GONE);

        repository.getWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> data) {
                progressBarLoading.setVisibility(View.GONE);
                List<Word> playableWords = preparePlayableWords(data);

                if (playableWords.isEmpty()) {
                    Toast.makeText(SprintGameActivity.this,
                            "В теме нет доступных терминов", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                startGame(playableWords);
            }

            @Override
            public void onError(String error) {
                progressBarLoading.setVisibility(View.GONE);
                Toast.makeText(SprintGameActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // Инициализирует игровые параметры и запускает таймер
    private void startGame(List<Word> allWords) {
        List<Word> shuffledWords = new ArrayList<>(allWords);
        Collections.shuffle(shuffledWords);

        int limit = Math.min(shuffledWords.size(), WORDS_LIMIT);
        gameWords = new ArrayList<>(shuffledWords.subList(0, limit));

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

    // Запускает обратный отсчёт; по завершении переходит к фазе исправления ошибок
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

    // Отображает следующее слово; с вероятностью 50% показывает верный или неверный перевод
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
            int safety = 0;
            while (randomWord.getId() != null && currentWord.getId() != null
                    && randomWord.getId().equals(currentWord.getId())
                    && gameWords.size() > 1 && safety < 10) {
                randomWord = gameWords.get(random.nextInt(gameWords.size()));
                safety++;
            }
            displayedTranslation = randomWord.getTranslation();
            isCorrectPair = false;
        }

        tvTranslation.setText(displayedTranslation);
        refreshDictionaryState();
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
        refreshDictionaryState();
    }

    // Проверяет наличие текущего слова в личном словаре и обновляет состояние карточки
    private void refreshDictionaryState() {
        if (currentWord == null) {
            cardSprintDictionaryState.setVisibility(View.GONE);
            return;
        }

        dictionaryStateLoading = true;
        currentWordAlreadyInDictionary = false;

        cardSprintDictionaryState.setVisibility(View.VISIBLE);
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
        cardSprintDictionaryState.setEnabled(false);
        cardSprintDictionaryState.setCardBackgroundColor(getDictionaryCardBackgroundColor());
        cardSprintDictionaryState.setStrokeColor(COLOR_GRAY);
        cardSprintDictionaryState.setStrokeWidth(dp(1));
        tvSprintDictionaryIcon.setText("…");
        tvSprintDictionaryIcon.setTextColor(COLOR_GRAY);
        tvSprintDictionaryText.setText("Проверяем");
        tvSprintDictionaryText.setTextColor(getDictionaryCardTextColor());
    }

    private void renderDictionaryCanAddState() {
        cardSprintDictionaryState.setEnabled(true);
        cardSprintDictionaryState.setCardBackgroundColor(getDictionaryCardBackgroundColor());
        cardSprintDictionaryState.setStrokeColor(COLOR_BLUE);
        cardSprintDictionaryState.setStrokeWidth(dp(1));
        tvSprintDictionaryIcon.setText("☆");
        tvSprintDictionaryIcon.setTextColor(COLOR_BLUE);
        tvSprintDictionaryText.setText("Сохранить");
        tvSprintDictionaryText.setTextColor(getDictionaryCardTextColor());
    }

    private void renderDictionaryAddedState() {
        cardSprintDictionaryState.setEnabled(true);
        cardSprintDictionaryState.setCardBackgroundColor(getDictionaryAddedBackgroundColor());
        cardSprintDictionaryState.setStrokeColor(COLOR_GREEN);
        cardSprintDictionaryState.setStrokeWidth(dp(1));
        tvSprintDictionaryIcon.setText("★");
        tvSprintDictionaryIcon.setTextColor(COLOR_GREEN);
        tvSprintDictionaryText.setText("В словаре");
        tvSprintDictionaryText.setTextColor(COLOR_GREEN);
    }

    private void addCurrentWordToDictionary() {
        if (currentWord == null) return;

        dictionaryStateLoading = true;
        renderDictionaryLoadingState();

        repository.addWordToPersonalDictionary(currentWord,
                new Repository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        dictionaryStateLoading = false;
                        currentWordAlreadyInDictionary = true;
                        renderDictionaryAddedState();
                        Toast.makeText(SprintGameActivity.this,
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
                        Toast.makeText(SprintGameActivity.this, error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkAnswer(boolean userSaidCorrect) {
        if (!isGameActive) return;

        if (!isCorrectionMode) {
            handleMainGameAnswer(userSaidCorrect);
        } else {
            handleCorrectionAnswer(userSaidCorrect);
        }
    }

    // Обрабатывает ответ в основном режиме: начисляет очки или сохраняет ошибку
    private void handleMainGameAnswer(boolean userSaidCorrect) {
        if (userSaidCorrect == isCorrectPair) {
            correctAnswers++;
            score += 10;

            final Word w = currentWord;
            repository.getCurrentUserId(userId -> {
                if (userId != null && userId > 0 && w != null) {
                    repository.markSprintPassed(userId, w.getId());
                }
            });
        } else {
            mistakeQuestions.add(new SprintQuestion(
                    currentWord, displayedTranslation, isCorrectPair));
            repository.recordWordMistake(currentWord);
        }

        tvScore.setText("Счет: " + score);
        currentWordIndex++;
        showNextWord();
    }

    // Обрабатывает ответ в режиме исправления: уменьшает счётчик ошибок или повторяет попытку
    private void handleCorrectionAnswer(boolean userSaidCorrect) {
        if (userSaidCorrect == isCorrectPair) {
            fixedErrorsCount++;

            final Word w = currentWord;

            repository.resolveWordMistake(w, new Repository.DataCallback<Void>() {
                @Override public void onSuccess(Void data) { }
                @Override public void onError(String error) { }
            });

            repository.getCurrentUserId(userId -> {
                if (userId != null && userId > 0 && w != null) {
                    repository.markSprintPassed(userId, w.getId());
                }
            });

            correctionIndex++;
            showCorrectionQuestion();
        } else {
            repository.recordWordMistake(currentWord);
            Toast.makeText(this, "Попробуйте ещё раз", Toast.LENGTH_SHORT).show();
        }
    }

    // Завершает основную фазу игры и инициирует работу над ошибками при их наличии
    private void finishMainPhase() {
        if (!isGameActive) return;
        isGameActive = false;
        if (timer != null) timer.cancel();

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
        Toast.makeText(this, "Переходим к работе над ошибками",
                Toast.LENGTH_LONG).show();
        showCorrectionQuestion();
    }

    // Завершает игру, рассчитывает XP, воспроизводит анимацию и переходит к экрану результатов
    private void finishGame() {
        isGameActive = false;
        if (timer != null) timer.cancel();

        playLottieWinAnimation();

        int xpForWords = (int) (correctAnswers * XP_PER_WORD);
        boolean isPerfectGame = mistakeQuestions.isEmpty() && initialTotalWords > 0;
        int bonus = isPerfectGame ? WIN_BONUS : 0;
        int totalXp = xpForWords + bonus;

        repository.recordLessonCompletion(
                "SPRINT",
                themeId != null ? themeId : -1L,
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
        intent.putExtra("THEME_ID", themeId != null ? themeId : -1L);
        intent.putExtra("THEME_TITLE", themeTitle);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isDarkTheme() {
        int nightModeFlags = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private int getDictionaryCardBackgroundColor() {
        return isDarkTheme()
                ? Color.parseColor("#102733")
                : Color.parseColor("#FFFFFF");
    }

    private int getDictionaryCardTextColor() {
        return isDarkTheme()
                ? Color.WHITE
                : Color.parseColor("#14303C");
    }

    private int getDictionaryAddedBackgroundColor() {
        return isDarkTheme()
                ? Color.parseColor("#09251A")
                : Color.parseColor("#EAF9DF");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }

    private static class SprintQuestion {
        final Word word;
        final String displayedTranslation;
        final boolean isCorrectPair;

        SprintQuestion(Word word, String displayedTranslation, boolean isCorrectPair) {
            this.word = word;
            this.displayedTranslation = displayedTranslation;
            this.isCorrectPair = isCorrectPair;
        }
    }
}