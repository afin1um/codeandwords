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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MatchingGameActivity extends AppCompatActivity {

    private LinearLayout columnTerms, columnTranslations;
    private ProgressBar progressBar, loadingIndicator;
    private Button btnCheck, btnCantListen;

    private Repository repository;
    private Long themeId;

    private int score = 0;
    private int totalPairs = 0;
    private int pairsFound = 0;
    private int mistakesCount = 0;

    private Button selectedTermBtn = null;
    private Button selectedTranslationBtn = null;

    private SoundPool soundPool;
    private int soundSuccess, soundError;
    private TextToSpeech tts;

    private boolean isAudioMode = true;
    private List<Word> currentLevelWords = new ArrayList<>();

    // Множество для хранения ID (переводов) уже найденных пар
    private Set<String> solvedMatchIds = new HashSet<>();

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

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        btnCantListen.setOnClickListener(v -> {
            isAudioMode = false;
            btnCantListen.setVisibility(View.GONE);
            startFadeTransition();
        });
    }

    private void startFadeTransition() {
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(300);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                refreshTermsColumn(); // Теперь этот метод учитывает solvedMatchIds

                AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                fadeIn.setDuration(300);
                columnTerms.startAnimation(fadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        columnTerms.startAnimation(fadeOut);
    }

    private void initSounds() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build();
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
                if (words.size() < 2) {
                    finish();
                    return;
                }
                currentLevelWords = words.subList(0, Math.min(words.size(), 6));
                setupGame();
            }

            @Override
            public void onError(String error) {
                loadingIndicator.setVisibility(View.GONE);
                finish();
            }
        });
    }

    private void setupGame() {
        totalPairs = currentLevelWords.size();
        columnTerms.removeAllViews();
        columnTranslations.removeAllViews();

        for (Word word : currentLevelWords) {
            columnTerms.addView(createWordButton(word.getTerm(), word.getTranslation(), true));
        }

        List<Word> shuffledTranslations = new ArrayList<>(currentLevelWords);
        Collections.shuffle(shuffledTranslations);
        for (Word word : shuffledTranslations) {
            columnTranslations.addView(createWordButton(word.getTranslation(), word.getTranslation(), false));
        }
    }

    private void refreshTermsColumn() {
        columnTerms.removeAllViews();
        selectedTermBtn = null;
        for (Word word : currentLevelWords) {
            // ИСПРАВЛЕНИЕ: Добавляем кнопку только если это слово еще не отгадано
            if (!solvedMatchIds.contains(word.getTranslation())) {
                columnTerms.addView(createWordButton(word.getTerm(), word.getTranslation(), true));
            } else {
                // Добавляем пустую невидимую заглушку, чтобы сохранить позиции остальных кнопок,
                // если хочешь, чтобы они не прыгали. Если хочешь, чтобы список "схлопнулся",
                // просто ничего не добавляй (текущий вариант).
            }
        }
    }

    private Button createWordButton(String text, String matchId, boolean isTerm) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setTextColor(ContextCompat.getColor(this, R.color.duo_text_white));
        btn.setBackgroundResource(R.drawable.bg_word_chip);

        if (isTerm && isAudioMode) {
            btn.setText("");
            btn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_speaker, 0, 0, 0);
            btn.setPadding(40, 0, 0, 0);
        } else {
            btn.setText(text);
            btn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            btn.setPadding(0, 0, 0, 0);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 150);
        params.setMargins(12, 16, 12, 16);
        btn.setLayoutParams(params);

        btn.setTag(matchId);
        if (isTerm) btn.setContentDescription(text);

        btn.setOnClickListener(v -> {
            if (isTerm) {
                if (isAudioMode) {
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
            if (selectedTermBtn != null) resetButtonStyle(selectedTermBtn);
            selectedTermBtn = btn;
        } else {
            if (selectedTranslationBtn != null) resetButtonStyle(selectedTranslationBtn);
            selectedTranslationBtn = btn;
        }

        btn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_blue));
        checkMatch();
    }

    private void checkMatch() {
        if (selectedTermBtn != null && selectedTranslationBtn != null) {
            String termTag = (String) selectedTermBtn.getTag();
            String transTag = (String) selectedTranslationBtn.getTag();

            if (termTag.equals(transTag)) {
                soundPool.play(soundSuccess, 1f, 1f, 1, 0, 1f);
                score += 10;
                pairsFound++;
                updateProgress();

                // Сохраняем ID отгаданной пары
                solvedMatchIds.add(termTag);

                selectedTermBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_green));
                selectedTranslationBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_green));

                Button b1 = selectedTermBtn;
                Button b2 = selectedTranslationBtn;
                new Handler().postDelayed(() -> {
                    b1.setVisibility(View.INVISIBLE);
                    b2.setVisibility(View.INVISIBLE);
                    if (pairsFound == totalPairs) finishGame();
                }, 400);

            } else {
                soundPool.play(soundError, 1f, 1f, 1, 0, 1f);
                mistakesCount++;
                selectedTermBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_red));
                selectedTranslationBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.duo_red));

                Button b1 = selectedTermBtn;
                Button b2 = selectedTranslationBtn;
                new Handler().postDelayed(() -> {
                    resetButtonStyle(b1);
                    resetButtonStyle(b2);
                }, 600);
            }

            selectedTermBtn = null;
            selectedTranslationBtn = null;
        }
    }

    private void resetButtonStyle(Button btn) {
        btn.setBackgroundTintList(null);
        btn.setBackgroundResource(R.drawable.bg_word_chip);
    }

    private void updateProgress() {
        int targetProgress = (pairsFound * 100) / totalPairs;
        android.animation.ObjectAnimator animation = android.animation.ObjectAnimator.ofInt(
                progressBar, "progress", progressBar.getProgress(), targetProgress);
        animation.setDuration(600);
        animation.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animation.start();
    }

    private void finishGame() {
        repository.addXp(score);
        Intent intent = new Intent(this, GameResultActivity.class);
        intent.putExtra("SCORE", score);
        intent.putExtra("TOTAL_WORDS", totalPairs);
        intent.putExtra("MISTAKES_COUNT", mistakesCount);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) soundPool.release();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}