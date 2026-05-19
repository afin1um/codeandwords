package com.example.codeandwords.ui.game;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Word;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MatchingGameActivity extends AppCompatActivity {

    private static final int REQUIRED_PAIRS_COUNT = 5;
    private static final int POINTS_PER_FIRST_TRY_PAIR = 10;

    private static final int COLOR_CARD_DEFAULT = Color.rgb(16, 39, 51);
    private static final int COLOR_BLUE = Color.rgb(28, 176, 246);
    private static final int COLOR_GREEN = Color.rgb(88, 204, 2);
    private static final int COLOR_GRAY = Color.rgb(138, 154, 165);
    private static final int COLOR_WHITE = Color.WHITE;

    private LinearLayout columnTerms;
    private LinearLayout columnTranslations;
    private ProgressBar progressBar;
    private ProgressBar loadingIndicator;
    private Button btnCantListen;
    private TextView tvTitle;
    private TextView tvCorrectionHeader;
    private TextView tvMistakesLeft;
    private MaterialCardView correctionBannerCard;

    private MaterialCardView cardMatchingDictionaryState;
    private TextView tvMatchingDictionaryIcon;
    private TextView tvMatchingDictionaryText;

    private Repository repository;
    private Long themeId;

    private int score = 0;
    private int totalPairs = 0;
    private int pairsFound = 0;
    private int mistakesCount = 0;
    private int initialTotalPairs = 0;
    private int fixedErrorsCount = 0;

    private Button selectedTermBtn = null;
    private Button selectedTranslationBtn = null;

    private SoundPool soundPool;
    private int soundSuccess;
    private int soundError;
    private TextToSpeech tts;

    private boolean isAudioMode = true;
    private boolean isCorrectionMode = false;

    private Word selectedDictionaryWord;
    private boolean selectedWordAlreadyInDictionary = false;
    private boolean dictionaryStateLoading = false;

    private List<Word> currentLevelWords = new ArrayList<>();
    private List<Word> roundWords = new ArrayList<>();

    private final List<String> solvedMatchIds = new ArrayList<>();
    private final Map<String, Word> mistakenWordsMap = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matching_game);

        initViews();
        initSounds();
        initTTS();

        repository = Repository.getInstance(getApplicationContext());
        themeId = getIntent().getLongExtra("THEME_ID", -1);

        if (themeId != -1) {
            loadGameData();
        } else {
            Toast.makeText(this, "Тема не выбрана", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        columnTerms = findViewById(R.id.columnTerms);
        columnTranslations = findViewById(R.id.columnTranslations);
        progressBar = findViewById(R.id.gameProgressBar);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        btnCantListen = findViewById(R.id.btnCantListen);
        tvTitle = findViewById(R.id.tvTitle);
        tvCorrectionHeader = findViewById(R.id.tvCorrectionHeader);
        tvMistakesLeft = findViewById(R.id.tvMistakesLeft);
        correctionBannerCard = findViewById(R.id.correctionBannerCard);

        cardMatchingDictionaryState = findViewById(R.id.cardMatchingDictionaryState);
        tvMatchingDictionaryIcon = findViewById(R.id.tvMatchingDictionaryIcon);
        tvMatchingDictionaryText = findViewById(R.id.tvMatchingDictionaryText);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        cardMatchingDictionaryState.setVisibility(View.GONE);
        cardMatchingDictionaryState.setOnClickListener(v -> {
            if (dictionaryStateLoading) return;

            if (selectedDictionaryWord == null) {
                Toast.makeText(this, "Сначала выберите слово", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedWordAlreadyInDictionary) {
                Toast.makeText(this, "Это слово уже есть в личном словаре", Toast.LENGTH_SHORT).show();
                return;
            }

            addSelectedWordToDictionary();
        });

        btnCantListen.setOnClickListener(v -> {
            isAudioMode = false;
            btnCantListen.setVisibility(View.GONE);
            startFadeTransition();
        });

        updateCorrectionUi();
    }

    private void initSounds() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attrs)
                .build();

        soundSuccess = soundPool.load(this, R.raw.success, 1);
        soundError = soundPool.load(this, R.raw.error, 1);
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });
    }

    private void loadGameData() {
        loadingIndicator.setVisibility(View.VISIBLE);

        repository.getUnlearnedWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                loadingIndicator.setVisibility(View.GONE);

                List<Word> playableWords = preparePlayableWords(words);

                if (playableWords.isEmpty()) {
                    Toast.makeText(MatchingGameActivity.this,
                            "Все доступные термины этой темы уже изучены.",
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                if (playableWords.size() < REQUIRED_PAIRS_COUNT) {
                    Toast.makeText(MatchingGameActivity.this,
                            "Для режима нужно минимум 5 неизученных терминов. Сейчас: "
                                    + playableWords.size(),
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                Collections.shuffle(playableWords);

                currentLevelWords = new ArrayList<>(
                        playableWords.subList(0, REQUIRED_PAIRS_COUNT));

                roundWords = new ArrayList<>(currentLevelWords);

                totalPairs = currentLevelWords.size();
                initialTotalPairs = totalPairs;
                pairsFound = 0;
                score = 0;
                mistakesCount = 0;
                fixedErrorsCount = 0;
                isCorrectionMode = false;

                selectedDictionaryWord = null;
                selectedWordAlreadyInDictionary = false;
                dictionaryStateLoading = false;

                solvedMatchIds.clear();
                mistakenWordsMap.clear();

                updateCorrectionUi();
                setupGame();
            }

            @Override
            public void onError(String error) {
                loadingIndicator.setVisibility(View.GONE);
                Toast.makeText(MatchingGameActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private List<Word> preparePlayableWords(List<Word> words) {
        List<Word> result = new ArrayList<>();
        if (words == null) return result;

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

    private void updateCorrectionUi() {
        if (isCorrectionMode) {
            tvTitle.setText("Исправьте пары");
            correctionBannerCard.setVisibility(View.VISIBLE);
            tvMistakesLeft.setText("Осталось пар: " + (totalPairs - pairsFound));
            btnCantListen.setVisibility(View.GONE);
        } else {
            tvTitle.setText("Нажмите на пары слов");
            correctionBannerCard.setVisibility(View.GONE);
            if (isAudioMode) btnCantListen.setVisibility(View.VISIBLE);
        }
    }

    private void setupGame() {
        columnTerms.removeAllViews();
        columnTranslations.removeAllViews();

        selectedTermBtn = null;
        selectedTranslationBtn = null;
        selectedDictionaryWord = null;
        cardMatchingDictionaryState.setVisibility(View.GONE);

        for (Word word : currentLevelWords) {
            if (!solvedMatchIds.contains(makeMatchId(word))) {
                columnTerms.addView(createWordButton(
                        word.getTerm(), makeMatchId(word), true));
            }
        }

        List<Word> shuffledTranslations = new ArrayList<>();
        for (Word word : currentLevelWords) {
            if (!solvedMatchIds.contains(makeMatchId(word))) {
                shuffledTranslations.add(word);
            }
        }
        Collections.shuffle(shuffledTranslations);

        for (Word word : shuffledTranslations) {
            columnTranslations.addView(createWordButton(
                    word.getTranslation(), makeMatchId(word), false));
        }

        updateProgress();
        updateCorrectionUi();
    }

    private String makeMatchId(Word word) {
        if (word == null) return "";
        if (word.getId() != null) return String.valueOf(word.getId());
        String term = word.getTerm() == null ? "" : word.getTerm().trim();
        String translation = word.getTranslation() == null ? "" : word.getTranslation().trim();
        return term + "_" + translation;
    }

    private void startFadeTransition() {
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(300);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) { }
            @Override
            public void onAnimationEnd(Animation animation) {
                setupGame();
                AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                fadeIn.setDuration(300);
                columnTerms.startAnimation(fadeIn);
            }
            @Override public void onAnimationRepeat(Animation animation) { }
        });
        columnTerms.startAnimation(fadeOut);
    }

    private Button createWordButton(String text, String matchId, boolean isTerm) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setTextColor(ContextCompat.getColor(this, R.color.duo_text_white));
        btn.setBackgroundResource(R.drawable.bg_word_chip);

        if (isTerm && isAudioMode && !isCorrectionMode) {
            btn.setText("");
            btn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_speaker, 0, 0, 0);
            btn.setPadding(40, 0, 0, 0);
            btn.setContentDescription(text);
        } else {
            btn.setText(text);
            btn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            btn.setPadding(0, 0, 0, 0);
            if (isTerm) btn.setContentDescription(text);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 150);
        params.setMargins(12, 16, 12, 16);
        btn.setLayoutParams(params);
        btn.setTag(matchId);

        btn.setOnClickListener(v -> {
            if (isTerm) {
                if (isAudioMode && !isCorrectionMode && tts != null) {
                    CharSequence content = btn.getContentDescription();
                    if (content != null) {
                        tts.speak(content.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                }
                handleSelection(btn, true);
            } else {
                handleSelection(btn, false);
            }
        });

        return btn;
    }

    private void handleSelection(Button btn, boolean isTerm) {
        if (isTerm) {
            if (selectedTermBtn != null) resetButtonStyle(selectedTermBtn);
            selectedTermBtn = btn;
            selectedDictionaryWord = findWordByMatchId(String.valueOf(btn.getTag()));
            refreshDictionaryState();
        } else {
            if (selectedTranslationBtn != null) resetButtonStyle(selectedTranslationBtn);
            selectedTranslationBtn = btn;
        }

        btn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_blue));
        checkMatch();
    }

    private void refreshDictionaryState() {
        if (selectedDictionaryWord == null) {
            cardMatchingDictionaryState.setVisibility(View.GONE);
            return;
        }

        dictionaryStateLoading = true;
        selectedWordAlreadyInDictionary = false;

        cardMatchingDictionaryState.setVisibility(View.VISIBLE);
        renderDictionaryLoadingState();

        repository.isWordInPersonalDictionary(selectedDictionaryWord,
                new Repository.DataCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isAdded) {
                        dictionaryStateLoading = false;
                        selectedWordAlreadyInDictionary = isAdded != null && isAdded;
                        if (selectedWordAlreadyInDictionary) {
                            renderDictionaryAddedState();
                        } else {
                            renderDictionaryCanAddState();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        dictionaryStateLoading = false;
                        selectedWordAlreadyInDictionary = false;
                        renderDictionaryCanAddState();
                    }
                });
    }

    private void renderDictionaryLoadingState() {
        cardMatchingDictionaryState.setEnabled(false);
        cardMatchingDictionaryState.setCardBackgroundColor(COLOR_CARD_DEFAULT);
        cardMatchingDictionaryState.setStrokeColor(COLOR_GRAY);
        cardMatchingDictionaryState.setStrokeWidth(dp(1));
        tvMatchingDictionaryIcon.setText("…");
        tvMatchingDictionaryIcon.setTextColor(COLOR_GRAY);
        tvMatchingDictionaryText.setText("Проверяем");
        tvMatchingDictionaryText.setTextColor(COLOR_WHITE);
    }

    private void renderDictionaryCanAddState() {
        cardMatchingDictionaryState.setEnabled(true);
        cardMatchingDictionaryState.setCardBackgroundColor(COLOR_CARD_DEFAULT);
        cardMatchingDictionaryState.setStrokeColor(COLOR_BLUE);
        cardMatchingDictionaryState.setStrokeWidth(dp(1));
        tvMatchingDictionaryIcon.setText("☆");
        tvMatchingDictionaryIcon.setTextColor(COLOR_BLUE);
        tvMatchingDictionaryText.setText("Сохранить");
        tvMatchingDictionaryText.setTextColor(COLOR_WHITE);
    }

    private void renderDictionaryAddedState() {
        cardMatchingDictionaryState.setEnabled(true);
        cardMatchingDictionaryState.setCardBackgroundColor(Color.rgb(9, 37, 26));
        cardMatchingDictionaryState.setStrokeColor(COLOR_GREEN);
        cardMatchingDictionaryState.setStrokeWidth(dp(1));
        tvMatchingDictionaryIcon.setText("★");
        tvMatchingDictionaryIcon.setTextColor(COLOR_GREEN);
        tvMatchingDictionaryText.setText("В словаре");
        tvMatchingDictionaryText.setTextColor(COLOR_GREEN);
    }

    private void addSelectedWordToDictionary() {
        if (selectedDictionaryWord == null) return;

        dictionaryStateLoading = true;
        renderDictionaryLoadingState();

        repository.addWordToPersonalDictionary(selectedDictionaryWord,
                new Repository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        dictionaryStateLoading = false;
                        selectedWordAlreadyInDictionary = true;
                        renderDictionaryAddedState();
                        Toast.makeText(MatchingGameActivity.this,
                                "Слово добавлено в личный словарь", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        dictionaryStateLoading = false;
                        if (error != null && error.toLowerCase().contains("уже есть")) {
                            selectedWordAlreadyInDictionary = true;
                            renderDictionaryAddedState();
                        } else {
                            selectedWordAlreadyInDictionary = false;
                            renderDictionaryCanAddState();
                        }
                        Toast.makeText(MatchingGameActivity.this, error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private Word findWordByMatchId(String matchId) {
        for (Word word : currentLevelWords) {
            if (makeMatchId(word).equals(matchId)) return word;
        }
        for (Word word : roundWords) {
            if (makeMatchId(word).equals(matchId)) return word;
        }
        return null;
    }

    private void checkMatch() {
        if (selectedTermBtn == null || selectedTranslationBtn == null) return;

        String termTag = String.valueOf(selectedTermBtn.getTag());
        String transTag = String.valueOf(selectedTranslationBtn.getTag());

        if (termTag.equals(transTag)) {
            handleCorrectMatch(termTag);
        } else {
            handleWrongMatch(termTag);
        }
    }

    private void handleCorrectMatch(String matchId) {
        soundPool.play(soundSuccess, 1f, 1f, 1, 0, 1f);

        pairsFound++;

        Word matchedWord = findWordByMatchId(matchId);

        if (isCorrectionMode) {
            fixedErrorsCount++;

            // ✅ ИСПРАВЛЕНИЕ ОШИБКИ — вызываем resolveWordMistake
            if (matchedWord != null) {
                repository.resolveWordMistake(matchedWord, new Repository.DataCallback<Void>() {
                    @Override public void onSuccess(Void data) { }
                    @Override public void onError(String error) { }
                });
            }
        } else {
            if (!mistakenWordsMap.containsKey(matchId)) {
                score += POINTS_PER_FIRST_TRY_PAIR;
            }

            // ✅ ПРАВИЛЬНЫЙ ОТВЕТ — начисляем прогресс
            if (matchedWord != null) {
                repository.getCurrentUserId(userId -> {
                    if (userId != null && userId > 0) {
                        repository.incrementWordProgress(userId, matchedWord.getId());
                    }
                });
            }
        }

        solvedMatchIds.add(matchId);

        selectedTermBtn.setBackgroundTintList(
                ContextCompat.getColorStateList(this, R.color.duo_green));
        selectedTranslationBtn.setBackgroundTintList(
                ContextCompat.getColorStateList(this, R.color.duo_green));

        Button termButton = selectedTermBtn;
        Button translationButton = selectedTranslationBtn;

        selectedTermBtn = null;
        selectedTranslationBtn = null;
        selectedDictionaryWord = null;
        cardMatchingDictionaryState.setVisibility(View.GONE);

        new Handler().postDelayed(() -> {
            termButton.setVisibility(View.INVISIBLE);
            translationButton.setVisibility(View.INVISIBLE);
            updateProgress();
            updateCorrectionUi();

            if (pairsFound == totalPairs) {
                onRoundCompleted();
            }
        }, 400);
    }

    private void handleWrongMatch(String termMatchId) {
        soundPool.play(soundError, 1f, 1f, 1, 0, 1f);
        mistakesCount++;

        Word mistakenWord = findWordByMatchId(termMatchId);

        if (mistakenWord != null) {
            mistakenWordsMap.put(makeMatchId(mistakenWord), mistakenWord);
            repository.recordWordMistake(mistakenWord);
        }

        selectedTermBtn.setBackgroundTintList(
                ContextCompat.getColorStateList(this, R.color.duo_red));
        selectedTranslationBtn.setBackgroundTintList(
                ContextCompat.getColorStateList(this, R.color.duo_red));

        Button termButton = selectedTermBtn;
        Button translationButton = selectedTranslationBtn;

        selectedTermBtn = null;
        selectedTranslationBtn = null;
        selectedDictionaryWord = null;
        cardMatchingDictionaryState.setVisibility(View.GONE);

        new Handler().postDelayed(() -> {
            resetButtonStyle(termButton);
            resetButtonStyle(translationButton);
        }, 600);
    }

    private void onRoundCompleted() {
        if (!isCorrectionMode && !mistakenWordsMap.isEmpty()) {
            startCorrectionMode();
        } else {
            // ✅ Прогресс уже начислен поштучно — просто завершаем
            finishGame();
        }
    }

    private void startCorrectionMode() {
        isCorrectionMode = true;
        currentLevelWords = new ArrayList<>(mistakenWordsMap.values());
        totalPairs = currentLevelWords.size();
        pairsFound = 0;
        solvedMatchIds.clear();
        Toast.makeText(this, "Переходим к работе над ошибками", Toast.LENGTH_SHORT).show();
        setupGame();
    }

    private void resetButtonStyle(Button btn) {
        if (btn == null) return;
        btn.setBackgroundTintList(null);
        btn.setBackgroundResource(R.drawable.bg_word_chip);
    }

    private void updateProgress() {
        if (totalPairs == 0) {
            progressBar.setProgress(0);
            return;
        }
        int targetProgress = (pairsFound * 100) / totalPairs;
        ObjectAnimator animation = ObjectAnimator.ofInt(progressBar, "progress",
                progressBar.getProgress(), targetProgress);
        animation.setDuration(600);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.start();
    }

    private void finishGame() {
        repository.recordLessonCompletion("MATCHING", themeId, score,
                initialTotalPairs, mistakesCount, fixedErrorsCount, false);

        Intent intent = new Intent(this, GameResultActivity.class);
        intent.putExtra("SCORE", score);
        intent.putExtra("TOTAL_WORDS", initialTotalPairs);
        intent.putExtra("MISTAKES_COUNT", mistakesCount);
        startActivity(intent);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Это важно! Иначе TTS и SoundPool остаются в памяти
        if (repository != null) {
            repository.onDestroy();
        }
        if (soundPool != null) soundPool.release();

        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}