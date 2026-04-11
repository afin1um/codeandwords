package com.example.codeandwords.ui.game;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
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

    private LinearLayout columnTerms;
    private LinearLayout columnTranslations;
    private ProgressBar progressBar;
    private ProgressBar loadingIndicator;
    private Button btnCheck;
    private Button btnCantListen;
    private TextView tvTitle;
    private TextView tvCorrectionHeader;
    private TextView tvMistakesLeft;
    private MaterialCardView correctionBannerCard;

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

    private List<Word> currentLevelWords = new ArrayList<>();
    private final List<String> solvedMatchIds = new ArrayList<>();
    private final Map<String, Word> mistakenWordsMap = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matching_game);

        initViews();
        initSounds();
        initTTS();

        repository = new Repository(this);
        themeId = getIntent().getLongExtra("THEME_ID", -1);

        if (themeId != -1) {
            loadGameData();
        } else {
            finish();
        }
    }

    private void initViews() {
        columnTerms = findViewById(R.id.columnTerms);
        columnTranslations = findViewById(R.id.columnTranslations);
        progressBar = findViewById(R.id.gameProgressBar);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        btnCheck = findViewById(R.id.btnCheck);
        btnCantListen = findViewById(R.id.btnCantListen);
        tvTitle = findViewById(R.id.tvTitle);
        tvCorrectionHeader = findViewById(R.id.tvCorrectionHeader);
        tvMistakesLeft = findViewById(R.id.tvMistakesLeft);
        correctionBannerCard = findViewById(R.id.correctionBannerCard);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        btnCheck.setEnabled(false);
        btnCheck.setText("В СЛОВАРЬ");
        btnCheck.setOnClickListener(v -> addSelectedWordToDictionary());

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

        repository.getWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                loadingIndicator.setVisibility(View.GONE);

                if (words == null || words.size() < 2) {
                    finish();
                    return;
                }

                currentLevelWords = new ArrayList<>(words.subList(0, Math.min(words.size(), 6)));
                totalPairs = currentLevelWords.size();
                initialTotalPairs = totalPairs;
                pairsFound = 0;
                score = 0;
                mistakesCount = 0;
                fixedErrorsCount = 0;
                isCorrectionMode = false;

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

    private void updateCorrectionUi() {
        if (isCorrectionMode) {
            tvTitle.setText("Исправьте пары");
            correctionBannerCard.setVisibility(View.VISIBLE);
            tvMistakesLeft.setText("Осталось пар: " + (totalPairs - pairsFound));
            btnCantListen.setVisibility(View.GONE);
        } else {
            tvTitle.setText("Нажмите на пары слов");
            correctionBannerCard.setVisibility(View.GONE);
            if (isAudioMode) {
                btnCantListen.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupGame() {
        columnTerms.removeAllViews();
        columnTranslations.removeAllViews();
        selectedTermBtn = null;
        selectedTranslationBtn = null;
        btnCheck.setEnabled(false);

        for (Word word : currentLevelWords) {
            if (!solvedMatchIds.contains(word.getTranslation())) {
                columnTerms.addView(createWordButton(word.getTerm(), word.getTranslation(), true));
            }
        }

        List<Word> shuffledTranslations = new ArrayList<>();
        for (Word word : currentLevelWords) {
            if (!solvedMatchIds.contains(word.getTranslation())) {
                shuffledTranslations.add(word);
            }
        }
        Collections.shuffle(shuffledTranslations);

        for (Word word : shuffledTranslations) {
            columnTranslations.addView(createWordButton(word.getTranslation(), word.getTranslation(), false));
        }

        updateProgress();
        updateCorrectionUi();
    }

    private void startFadeTransition() {
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(300);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                setupGame();

                AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                fadeIn.setDuration(300);
                columnTerms.startAnimation(fadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
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
            if (isTerm) {
                btn.setContentDescription(text);
            }
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                150
        );
        params.setMargins(12, 16, 12, 16);
        btn.setLayoutParams(params);

        btn.setTag(matchId);

        btn.setOnClickListener(v -> {
            if (isTerm) {
                if (isAudioMode && !isCorrectionMode && tts != null) {
                    tts.speak(btn.getContentDescription().toString(), TextToSpeech.QUEUE_FLUSH, null, null);
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
            if (selectedTermBtn != null) {
                resetButtonStyle(selectedTermBtn);
            }
            selectedTermBtn = btn;
            btnCheck.setEnabled(true);
        } else {
            if (selectedTranslationBtn != null) {
                resetButtonStyle(selectedTranslationBtn);
            }
            selectedTranslationBtn = btn;
        }

        btn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_blue));
        checkMatch();
    }

    private void addSelectedWordToDictionary() {
        if (selectedTermBtn == null) {
            Toast.makeText(this, "Сначала выберите слово", Toast.LENGTH_SHORT).show();
            return;
        }

        String term = selectedTermBtn.getContentDescription() != null
                ? selectedTermBtn.getContentDescription().toString()
                : selectedTermBtn.getText().toString();

        Word selectedWord = findWordByTerm(term);

        if (selectedWord == null) {
            Toast.makeText(this, "Не удалось найти слово", Toast.LENGTH_SHORT).show();
            return;
        }

        repository.addWordToPersonalDictionary(selectedWord, new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(MatchingGameActivity.this, "Слово добавлено в словарь", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MatchingGameActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Word findWordByTerm(String term) {
        for (Word word : currentLevelWords) {
            if (word.getTerm() != null && word.getTerm().equalsIgnoreCase(term)) {
                return word;
            }
        }
        return null;
    }

    private Word findWordByTranslationTag(String translationTag) {
        for (Word word : currentLevelWords) {
            if (word.getTranslation() != null && word.getTranslation().equals(translationTag)) {
                return word;
            }
        }
        return null;
    }

    private void checkMatch() {
        if (selectedTermBtn == null || selectedTranslationBtn == null) {
            return;
        }

        String termTag = (String) selectedTermBtn.getTag();
        String transTag = (String) selectedTranslationBtn.getTag();

        if (termTag.equals(transTag)) {
            soundPool.play(soundSuccess, 1f, 1f, 1, 0, 1f);

            pairsFound++;

            if (!isCorrectionMode) {
                score += 10;
            } else {
                fixedErrorsCount++;
            }

            solvedMatchIds.add(termTag);

            selectedTermBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_green));
            selectedTranslationBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_green));

            Button termButton = selectedTermBtn;
            Button translationButton = selectedTranslationBtn;

            selectedTermBtn = null;
            selectedTranslationBtn = null;
            btnCheck.setEnabled(false);

            new Handler().postDelayed(() -> {
                termButton.setVisibility(View.INVISIBLE);
                translationButton.setVisibility(View.INVISIBLE);

                updateCorrectionUi();

                if (pairsFound == totalPairs) {
                    onRoundCompleted();
                }
            }, 400);

        } else {
            soundPool.play(soundError, 1f, 1f, 1, 0, 1f);
            mistakesCount++;

            Word mistakenWord = findWordByTranslationTag(termTag);
            if (mistakenWord != null) {
                mistakenWordsMap.put(mistakenWord.getTranslation(), mistakenWord);
            }

            selectedTermBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_red));
            selectedTranslationBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_red));

            Button termButton = selectedTermBtn;
            Button translationButton = selectedTranslationBtn;

            selectedTermBtn = null;
            selectedTranslationBtn = null;
            btnCheck.setEnabled(false);

            new Handler().postDelayed(() -> {
                resetButtonStyle(termButton);
                resetButtonStyle(translationButton);
            }, 600);
        }
    }

    private void onRoundCompleted() {
        if (!isCorrectionMode && !mistakenWordsMap.isEmpty()) {
            startCorrectionMode();
        } else {
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
        btn.setBackgroundTintList(null);
        btn.setBackgroundResource(R.drawable.bg_word_chip);
    }

    private void updateProgress() {
        if (totalPairs == 0) {
            progressBar.setProgress(0);
            return;
        }

        int targetProgress = (pairsFound * 100) / totalPairs;
        android.animation.ObjectAnimator animation = android.animation.ObjectAnimator.ofInt(
                progressBar,
                "progress",
                progressBar.getProgress(),
                targetProgress
        );
        animation.setDuration(600);
        animation.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animation.start();
    }

    private void finishGame() {
        repository.recordLessonCompletion(
                "MATCHING",
                themeId,
                score,
                initialTotalPairs,
                mistakesCount,
                fixedErrorsCount,
                false
        );

        Intent intent = new Intent(this, GameResultActivity.class);
        intent.putExtra("SCORE", score);
        intent.putExtra("TOTAL_WORDS", initialTotalPairs);
        intent.putExtra("MISTAKES_COUNT", mistakesCount);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (soundPool != null) {
            soundPool.release();
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}