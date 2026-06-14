package com.example.codeandwords.ui.base;

import android.content.Intent;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.ui.dashboard.MainActivity;

public abstract class BaseBackActivity extends AppCompatActivity {

    protected void setupBackButton(@IdRes int buttonId) {
        View btn = findViewById(buttonId);
        if (btn != null) {
            btn.setOnClickListener(v -> handleBack());
        }
    }

    protected void setupCloseButton(@IdRes int buttonId) {
        setupBackButton(buttonId);
    }

    protected void setupCloseToMainButton(@IdRes int buttonId,
                                          Class<?> mainActivityClass) {
        View btn = findViewById(buttonId);
        if (btn != null) {
            btn.setOnClickListener(v -> goToMain(mainActivityClass));
        }
    }

    /**
     * ✅ Универсальный setter для кнопки, которая должна возвращать
     * в TrainingFragment через MainActivity.
     */
    protected void setupCloseToTrainingButton(@IdRes int buttonId) {
        View btn = findViewById(buttonId);
        if (btn != null) {
            btn.setOnClickListener(v -> goToTraining());
        }
    }

    protected void handleBack() {
        finish();
    }

    protected void goToMain(@Nullable Class<?> mainActivityClass) {
        if (mainActivityClass == null) {
            finish();
            return;
        }
        Intent intent = new Intent(this, mainActivityClass);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }

    /**
     * ✅ Возврат в TrainingFragment через MainActivity.
     * Используется в активити, запущенных из режима «Тренировка».
     */
    protected void goToTraining() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_FRAGMENT, MainActivity.FRAGMENT_TRAINING);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }
}