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

        int correctWords = Math.max(0, totalWords - mistakes);
        int accuracy = (totalWords > 0)
                ? Math.max(0, (correctWords * 100) / totalWords)
                : 100;

        tvScore.setText("+ " + score + " XP");
        tvAccuracy.setText(accuracy + "%");
        tvWordsCount.setText(String.valueOf(correctWords));

        triggerSuccessVibration();

        btnFinish.setOnClickListener(v -> finish());
    }

    private void triggerSuccessVibration() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long[] timings = {0, 150, 100, 400};
                int[] amplitudes = {0, 180, 0, 255};

                try {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                } catch (Exception e) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                vibrator.vibrate(500);
            }
        }
    }
}