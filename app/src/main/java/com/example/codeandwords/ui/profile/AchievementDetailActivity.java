package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Экран детальной информации о достижении: прогресс, условия, дата получения
public class AchievementDetailActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private ImageView ivAchievement;
    private TextView tvTitle;
    private TextView tvDescription;
    private TextView tvStatus;
    private TextView tvXpReward;
    private TextView tvCondition;
    private TextView tvProgress;
    private TextView tvReceivedAt;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievement_detail);

        initViews();
        bindData();
        btnBack.setOnClickListener(v -> goToAchievements());
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        ivAchievement = findViewById(R.id.ivAchievement);
        tvTitle = findViewById(R.id.tvTitle);
        tvDescription = findViewById(R.id.tvDescription);
        tvStatus = findViewById(R.id.tvStatus);
        tvXpReward = findViewById(R.id.tvXpReward);
        tvCondition = findViewById(R.id.tvCondition);
        tvProgress = findViewById(R.id.tvProgress);
        tvReceivedAt = findViewById(R.id.tvReceivedAt);
        progressBar = findViewById(R.id.progressBar);
    }

    // Привязывает данные из Intent к UI-элементам
    private void bindData() {
        String title = getIntent().getStringExtra("title");
        String description = getIntent().getStringExtra("description");
        int xpReward = getIntent().getIntExtra("xp_reward", 0);
        String conditionType = getIntent().getStringExtra("condition_type");
        int conditionValue = getIntent().getIntExtra("condition_value", 0);
        int maxProgress = getIntent().getIntExtra("max_progress", 0);
        int currentProgress = getIntent().getIntExtra("current_progress", 0);
        boolean isUnlocked = getIntent().getBooleanExtra("is_unlocked", false);
        long dateReceived = getIntent().getLongExtra("date_received", 0L);
        String iconResName = getIntent().getStringExtra("icon_res_name");

        tvTitle.setText(title != null ? title : "Достижение");
        tvDescription.setText(description != null ? description : "");
        tvXpReward.setText("Награда: +" + xpReward + " XP");
        tvStatus.setText(isUnlocked ? "Статус: получено" : "Статус: в процессе");

        if (maxProgress <= 0) {
            maxProgress = conditionValue > 0 ? conditionValue : 1;
        }

        progressBar.setMax(maxProgress);
        progressBar.setProgress(Math.min(currentProgress, maxProgress));
        tvProgress.setText("Прогресс: " + currentProgress + " / " + maxProgress);

        tvCondition.setText("Условие: " + getConditionText(conditionType, conditionValue));

        if (isUnlocked && dateReceived > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getDefault());
            tvReceivedAt.setText("Получено: " + sdf.format(new Date(dateReceived)));
        } else {
            tvReceivedAt.setText("Получено: ещё не открыто");
        }

        int iconId = getResources().getIdentifier(
                iconResName != null ? iconResName : "",
                "drawable",
                getPackageName()
        );
        ivAchievement.setImageResource(iconId != 0 ? iconId : R.drawable.ic_launcher_foreground);
    }

    // Формирует человекочитаемое описание условия достижения
    private String getConditionText(String type, int value) {
        if (type == null) return "Неизвестно";

        switch (type) {
            case "LOGIN_STREAK": return "Заходить " + value + " дней подряд";
            case "MAX_XP_DAY": return "Набрать " + value + " XP за день";
            case "PERFECT_STREAK": return "Пройти " + value + " уроков подряд без ошибок";
            case "EARLY_BIRD": return "Пройти " + value + " уроков до 9 утра";
            case "ERROR_FIXER": return "Исправить " + value + " ошибок";
            case "TASK_MASTER": return "Выполнить " + value + " заданий";
            case "NIGHT_OWL": return "Пройти " + value + " уроков после 22:00";
            case "TOTAL_XP": return "Набрать " + value + " общего XP";
            case "PERFECT_TOTAL": return "Пройти " + value + " уроков без ошибок";
            case "SPRINT_XP": return "Набрать " + value + " XP в режиме Спринт";
            default: return type + ": " + value;
        }
    }

    @Override
    public void onBackPressed() {
        goToAchievements();
    }

    private void goToAchievements() {
        Intent intent = new Intent(this, AchievementsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}