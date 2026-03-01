package com.example.codeandwords.ui.game;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.codeandwords.R;

public class GameResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_result);

        TextView tvScore = findViewById(R.id.tvResultScore);
        TextView tvAccuracy = findViewById(R.id.tvAccuracy);
        TextView tvWordsCount = findViewById(R.id.tvWordsCount);
        Button btnFinish = findViewById(R.id.btnFinish);

        int score = getIntent().getIntExtra("SCORE", 0);
        int totalWords = getIntent().getIntExtra("TOTAL_WORDS", 0);
        int mistakes = getIntent().getIntExtra("MISTAKES_COUNT", 0);

        int accuracy = (totalWords > 0) ? Math.max(0, ((totalWords - mistakes) * 100) / totalWords) : 100;

        tvScore.setText("+ " + score + " XP");
        tvAccuracy.setText(accuracy + "%");
        tvWordsCount.setText(String.valueOf(totalWords));

        // Запускаем вибрацию сразу при создании экрана
        triggerSuccessVibration();

        btnFinish.setOnClickListener(v -> finish());
    }

    private void triggerSuccessVibration() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Более надежный паттерн: ПАУЗА, ВИБРО, ПАУЗА, ВИБРО
                // (в миллисекундах)
                long[] timings = {0, 150, 100, 400};
                // Амплитуды (0 - выкл, 255 - макс)
                int[] amplitudes = {0, 180, 0, 255};

                try {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                } catch (Exception e) {
                    // Если устройство не поддерживает амплитуду, используем обычный метод
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                // Для старых устройств (Android < 8.0)
                // Вибрировать 500 миллисекунд
                vibrator.vibrate(500);
            }
        }
    }
}