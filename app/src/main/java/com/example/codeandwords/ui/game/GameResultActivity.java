package com.example.codeandwords.ui.game;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.codeandwords.R;
import com.example.codeandwords.ui.base.BaseBackActivity;
import com.example.codeandwords.ui.dashboard.MainActivity;

public class GameResultActivity extends BaseBackActivity {

    private boolean isTraining = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySystemBarsTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_result);

        // ✅ Читаем флаг тренировки заранее — он влияет на навигацию
        isTraining = getIntent().getBooleanExtra("IS_TRAINING", false);

        initViews();

        // ✅ Крестик: тренировка → TrainingFragment, обычная игра → главный экран
        if (isTraining) {
            setupCloseToTrainingButton(R.id.btnUniClose);
        } else {
            setupCloseToMainButton(R.id.btnUniClose, MainActivity.class);
        }
    }

    private void initViews() {
        TextView tvResultTitle = findViewById(R.id.tvResultTitle);
        TextView tvScore = findViewById(R.id.tvResultScore);
        TextView tvAccuracy = findViewById(R.id.tvAccuracy);
        TextView tvWordsCount = findViewById(R.id.tvWordsCount);
        TextView tvWordsLabel = findViewById(R.id.tvWordsLabel);
        Button btnFinish = findViewById(R.id.btnFinish);

        int score = getIntent().getIntExtra("SCORE", 0);
        int totalWords = getIntent().getIntExtra("TOTAL_WORDS", 0);
        int mistakes = getIntent().getIntExtra("MISTAKES_COUNT", 0);

        int correctWords = Math.max(0, totalWords - mistakes);
        int accuracy = (totalWords > 0)
                ? Math.max(0, (correctWords * 100) / totalWords)
                : 100;

        if (isTraining) {
            tvResultTitle.setText("Тренировка завершена!");
            tvScore.setText("XP не начисляется");
            tvWordsLabel.setText("ПОВТОРЕНО");
            tvWordsCount.setText(String.valueOf(totalWords));
        } else {
            tvResultTitle.setText("Урок завершён!");
            tvScore.setText("+ " + score + " XP");
            tvWordsLabel.setText("СЛОВ");
            tvWordsCount.setText(String.valueOf(correctWords));
        }

        tvAccuracy.setText(accuracy + "%");

        triggerSuccessVibration();

        // ✅ Кнопка «ПРОДОЛЖИТЬ»: тренировка → TrainingFragment, иначе → главный экран
        btnFinish.setOnClickListener(v -> {
            if (isTraining) {
                goToTraining();
            } else {
                goToMain(MainActivity.class);
            }
        });
    }

    /**
     * ✅ Системная кнопка «Назад» ведёт туда же, куда крестик и «Продолжить».
     */
    @Override
    public void onBackPressed() {
        if (isTraining) {
            goToTraining();
        } else {
            goToMain(MainActivity.class);
        }
    }

    private void applySystemBarsTheme() {
        Window window = getWindow();

        int statusColor = ContextCompat.getColor(this, R.color.game_result_status_bar);
        int navColor = ContextCompat.getColor(this, R.color.game_result_navigation_bar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(statusColor);
            window.setNavigationBarColor(navColor);
        }

        boolean isDarkTheme = isDarkTheme();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();

            if (!isDarkTheme) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!isDarkTheme) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }

            decor.setSystemUiVisibility(flags);
        }
    }

    private boolean isDarkTheme() {
        int nightModeFlags = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void triggerSuccessVibration() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long[] timings = {0, 150, 100, 400};
                int[] amplitudes = {0, 180, 0, 255};

                try {
                    vibrator.vibrate(
                            VibrationEffect.createWaveform(timings, amplitudes, -1)
                    );
                } catch (Exception e) {
                    vibrator.vibrate(VibrationEffect.createOneShot(
                            500,
                            VibrationEffect.DEFAULT_AMPLITUDE
                    ));
                }
            } else {
                vibrator.vibrate(500);
            }
        }
    }
}