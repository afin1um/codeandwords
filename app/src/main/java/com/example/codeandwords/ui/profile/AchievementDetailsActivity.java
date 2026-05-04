package com.example.codeandwords.ui.profile;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
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
    private TextView tvTitle;
    private TextView tvDescription;
    private TextView tvStatus;
    private TextView tvProgress;
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
        tvTitle = findViewById(R.id.tvAchievementTitle);
        tvDescription = findViewById(R.id.tvAchievementDescription);
        tvStatus = findViewById(R.id.tvAchievementStatus);
        tvProgress = findViewById(R.id.tvAchievementProgress);
        progressBar = findViewById(R.id.progressAchievementDetails);

        btnBack.setOnClickListener(v -> finish());
    }

    private void bindData() {
        String title = getIntent().getStringExtra("title");
        String description = getIntent().getStringExtra("description");
        String iconResName = getIntent().getStringExtra("iconResName");
        String conditionType = getIntent().getStringExtra("conditionType");
        int progress = getIntent().getIntExtra("progress", 0);
        int maxProgress = getIntent().getIntExtra("maxProgress", 0);
        boolean isUnlocked = getIntent().getBooleanExtra("isUnlocked", false);

        tvTitle.setText(title != null ? title : "Достижение");
        tvDescription.setText(description != null ? description : "");
        tvStatus.setText(isUnlocked ? "Получено" : "Не получено");

        if (maxProgress <= 0) {
            maxProgress = 1;
        }

        progressBar.setMax(maxProgress);
        progressBar.setProgress(Math.min(progress, maxProgress));
        tvProgress.setText(progress + " / " + maxProgress);

        @DrawableRes int iconRes = resolveIcon(iconResName, conditionType, title);
        Log.d("AchievementDetails", "title=" + title
                + ", iconResName=" + iconResName
                + ", conditionType=" + conditionType
                + ", resolvedRes=" + iconRes);

        Drawable drawable = AppCompatResources.getDrawable(this, iconRes);
        if (drawable != null) {
            ivAchievement.setImageDrawable(drawable.mutate());
        } else {
            ivAchievement.setImageResource(R.drawable.ic_achievement_default);
        }

        if (isUnlocked) {
            ivAchievement.clearColorFilter();
            progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FFD54F")));
            tvStatus.setTextColor(Color.parseColor("#7ED957"));
        } else {
            applyGrayFilter(ivAchievement);
            progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#4E6470")));
            tvStatus.setTextColor(Color.parseColor("#9AA7AF"));
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
            int byName = getResources().getIdentifier(normalizedIconName, "drawable", getPackageName());
            if (byName != 0) {
                return byName;
            }
        }

        String normalizedConditionType = normalizeKey(conditionType);
        switch (normalizedConditionType) {
            case "LOGIN_STREAK":
                return R.drawable.ic_ach_streak;
            case "MAX_XP_DAY":
                return R.drawable.ic_ach_max_day_xp;
            case "PERFECT_STREAK":
                return R.drawable.ic_ach_perfect_streak;
            case "EARLY_BIRD":
                return R.drawable.ic_ach_early_bird;
            case "ERROR_FIXER":
                return R.drawable.ic_ach_technician;
            case "TASK_MASTER":
                return R.drawable.ic_ach_mission;
            case "NIGHT_OWL":
                return R.drawable.ic_ach_night;
            case "TOTAL_XP":
                return R.drawable.ic_ach_xp_peak;
            case "PERFECT_TOTAL":
                return R.drawable.ic_ach_bullseye;
            case "SPRINT_XP":
                return R.drawable.ic_ach_sprinter;
        }

        String normalizedTitle = normalizeKey(title);
        switch (normalizedTitle) {
            case "УДАРНЫЙ_РЕКОРД":
                return R.drawable.ic_ach_streak;
            case "МАКСИМУМ_ОПЫТА":
                return R.drawable.ic_ach_max_day_xp;
            case "УРОКИ_БЕЗ_ОШИБОК":
                return R.drawable.ic_ach_perfect_streak;
            case "ПРОСНИСЬ_И_ПОЙ":
                return R.drawable.ic_ach_early_bird;
            case "ТЕХНИК":
                return R.drawable.ic_ach_technician;
            case "МИССИЯ_ВЫПОЛНИМА":
                return R.drawable.ic_ach_mission;
            case "ПОД_ПОКРОВОМ_НОЧИ":
                return R.drawable.ic_ach_night;
            case "ВЕРШИНЫ_ОПЫТА":
                return R.drawable.ic_ach_xp_peak;
            case "В_ЯБЛОЧКО":
                return R.drawable.ic_ach_bullseye;
            case "СПРИНТЕР":
                return R.drawable.ic_ach_sprinter;
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
}