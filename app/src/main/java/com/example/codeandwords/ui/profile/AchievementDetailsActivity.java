package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.example.codeandwords.R;

import java.util.Locale;

public class AchievementDetailsActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private ImageView ivAchievement;
    private ImageView ivLock;
    private TextView tvTitle;
    private TextView tvDescription;
    private TextView tvStatus;
    private TextView tvProgress;
    private TextView tvXpReward;
    private TextView tvCondition;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievement_details);

        initViews();
        bindData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBackAchievement);
        ivAchievement = findViewById(R.id.ivAchievementLarge);
        ivLock = findViewById(R.id.ivAchievementLock);
        tvTitle = findViewById(R.id.tvAchievementTitle);
        tvDescription = findViewById(R.id.tvAchievementDescription);
        tvStatus = findViewById(R.id.tvAchievementStatus);
        tvProgress = findViewById(R.id.tvAchievementProgress);
        tvXpReward = findViewById(R.id.tvAchievementXpReward);
        tvCondition = findViewById(R.id.tvAchievementCondition);
        progressBar = findViewById(R.id.progressAchievementDetails);

        // ✅ Переход в AchievementsActivity
        btnBack.setOnClickListener(v -> goToAchievements());
    }

    private void bindData() {
        String title = getIntent().getStringExtra("title");
        String description = getIntent().getStringExtra("description");
        String iconResName = getIntent().getStringExtra("iconResName");
        String conditionType = getIntent().getStringExtra("conditionType");

        int progress = getIntent().getIntExtra("progress", 0);
        int maxProgress = getIntent().getIntExtra("maxProgress", 0);
        int xpReward = getIntent().getIntExtra("xpReward", 0);

        boolean isUnlocked = getIntent().getBooleanExtra("isUnlocked", false);

        if (maxProgress <= 0) {
            maxProgress = 1;
        }

        int safeProgress = Math.max(0, Math.min(progress, maxProgress));

        tvTitle.setText(title != null && !title.trim().isEmpty() ? title : "Достижение");
        tvDescription.setText(description != null ? description : "");
        tvStatus.setText(isUnlocked ? "Получено" : "В процессе");
        tvProgress.setText(safeProgress + " / " + maxProgress);
        tvXpReward.setText(xpReward > 0 ? "+" + xpReward + " XP" : "Без награды XP");
        tvCondition.setText(makeConditionText(conditionType));

        progressBar.setMax(maxProgress);
        progressBar.setProgress(safeProgress);

        @DrawableRes int iconRes = resolveIcon(iconResName, conditionType, title);
        Drawable drawable = AppCompatResources.getDrawable(this, iconRes);

        if (drawable != null) {
            ivAchievement.setImageDrawable(drawable.mutate());
        } else {
            ivAchievement.setImageResource(R.drawable.ic_achievement_default);
        }

        if (isUnlocked) {
            ivAchievement.clearColorFilter();
            ivAchievement.setAlpha(1f);
            ivLock.setAlpha(0f);

            tvStatus.setTextColor(Color.parseColor("#58CC02"));
            progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#58CC02")));
        } else {
            applyGrayFilter(ivAchievement);
            ivAchievement.setAlpha(0.78f);
            ivLock.setAlpha(1f);

            tvStatus.setTextColor(Color.parseColor("#8A9AA5"));
            progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#1CB0F6")));
        }
    }

    private String makeConditionText(String conditionType) {
        String type = normalizeKey(conditionType);

        switch (type) {
            case "LOGIN_STREAK": return "Заходите в приложение несколько дней подряд.";
            case "MAX_XP_DAY": return "Набирайте много опыта за один день.";
            case "PERFECT_STREAK": return "Проходите уроки без ошибок подряд.";
            case "EARLY_BIRD": return "Занимайтесь утром.";
            case "ERROR_FIXER": return "Исправляйте ошибки в тренировке.";
            case "TASK_MASTER": return "Выполняйте задания и тренировки.";
            case "NIGHT_OWL": return "Занимайтесь поздно вечером.";
            case "TOTAL_XP": return "Набирайте общий опыт.";
            case "PERFECT_TOTAL": return "Проходите уроки без ошибок.";
            case "SPRINT_XP": return "Зарабатывайте опыт в режиме спринта.";
            default: return "Продолжайте учиться, чтобы открыть это достижение.";
        }
    }

    private void applyGrayFilter(ImageView imageView) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);

        ColorMatrix brightnessMatrix = new ColorMatrix(new float[]{
                0.85f, 0, 0, 0, 0,
                0, 0.85f, 0, 0, 0,
                0, 0, 0.85f, 0, 0,
                0, 0, 0, 1f, 0
        });

        matrix.postConcat(brightnessMatrix);
        imageView.setColorFilter(new ColorMatrixColorFilter(matrix));
    }

    @DrawableRes
    private int resolveIcon(String iconName, String conditionType, String title) {
        String normalizedIconName = normalizeResourceName(iconName);

        if (!normalizedIconName.isEmpty()) {
            int byName = getResources().getIdentifier(
                    normalizedIconName,
                    "drawable",
                    getPackageName()
            );

            if (byName != 0) {
                return byName;
            }
        }

        String normalizedConditionType = normalizeKey(conditionType);

        switch (normalizedConditionType) {
            case "LOGIN_STREAK": return R.drawable.ic_ach_streak;
            case "MAX_XP_DAY": return R.drawable.ic_ach_max_day_xp;
            case "PERFECT_STREAK": return R.drawable.ic_ach_perfect_streak;
            case "EARLY_BIRD": return R.drawable.ic_ach_early_bird;
            case "ERROR_FIXER": return R.drawable.ic_ach_technician;
            case "TASK_MASTER": return R.drawable.ic_ach_mission;
            case "NIGHT_OWL": return R.drawable.ic_ach_night;
            case "TOTAL_XP": return R.drawable.ic_ach_xp_peak;
            case "PERFECT_TOTAL": return R.drawable.ic_ach_bullseye;
            case "SPRINT_XP": return R.drawable.ic_ach_sprinter;
        }

        String normalizedTitle = normalizeKey(title);

        switch (normalizedTitle) {
            case "УДАРНЫЙ_РЕКОРД": return R.drawable.ic_ach_streak;
            case "МАКСИМУМ_ОПЫТА": return R.drawable.ic_ach_max_day_xp;
            case "УРОКИ_БЕЗ_ОШИБОК": return R.drawable.ic_ach_perfect_streak;
            case "ПРОСНИСЬ_И_ПОЙ": return R.drawable.ic_ach_early_bird;
            case "ТЕХНИК": return R.drawable.ic_ach_technician;
            case "МИССИЯ_ВЫПОЛНИМА": return R.drawable.ic_ach_mission;
            case "ПОД_ПОКРОВОМ_НОЧИ": return R.drawable.ic_ach_night;
            case "ВЕРШИНЫ_ОПЫТА": return R.drawable.ic_ach_xp_peak;
            case "В_ЯБЛОЧКО": return R.drawable.ic_ach_bullseye;
            case "СПРИНТЕР": return R.drawable.ic_ach_sprinter;
        }

        return R.drawable.ic_achievement_default;
    }

    private String normalizeResourceName(String value) {
        if (value == null) return "";

        String result = value.trim().toLowerCase(Locale.ROOT);

        if (result.endsWith(".xml")) {
            result = result.substring(0, result.length() - 4);
        }

        return result;
    }

    private String normalizeKey(String value) {
        if (value == null) return "";

        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace("«", "")
                .replace("»", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("-", "_")
                .replace(" ", "_");
    }

    // ✅ Перехват системной кнопки "назад"
    @Override
    public void onBackPressed() {
        goToAchievements();
    }

    // ✅ Метод явного возврата
    private void goToAchievements() {
        Intent intent = new Intent(this, AchievementsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}