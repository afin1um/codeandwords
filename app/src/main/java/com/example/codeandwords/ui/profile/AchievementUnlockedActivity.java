package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.google.android.material.button.MaterialButton;

// Экран всплывающего уведомления о разблокировке достижения.
public class AchievementUnlockedActivity extends AppCompatActivity {

    private ImageView ivGlow;
    private ImageView ivAchievement;
    private ImageView ivStarLeft;
    private ImageView ivStarRight;
    private TextView tvTitle;
    private TextView tvDescription;
    private TextView tvXpReward;
    private MaterialButton btnDetails;
    private MaterialButton btnContinue;

    private long achievementId;
    private String title;
    private String description;
    private int xpReward;
    private String iconResName;
    private int currentProgress;
    private int maxProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievement_unlocked);

        initViews();
        readExtras();
        bindData();
        setupAnimations();
        setupClicks();
    }

    private void initViews() {
        ivGlow = findViewById(R.id.ivGlow);
        ivAchievement = findViewById(R.id.ivAchievement);
        ivStarLeft = findViewById(R.id.ivStarLeft);
        ivStarRight = findViewById(R.id.ivStarRight);
        tvTitle = findViewById(R.id.tvTitle);
        tvDescription = findViewById(R.id.tvDescription);
        tvXpReward = findViewById(R.id.tvXpReward);
        btnDetails = findViewById(R.id.btnDetails);
        btnContinue = findViewById(R.id.btnContinue);
    }

    // Читает данные достижения из Intent.
    private void readExtras() {
        Intent intent = getIntent();
        achievementId = intent.getLongExtra("achievement_id", -1L);
        title = intent.getStringExtra("title");
        description = intent.getStringExtra("description");
        xpReward = intent.getIntExtra("xp_reward", 0);
        iconResName = intent.getStringExtra("icon_res_name");
        currentProgress = intent.getIntExtra("current_progress", 0);
        maxProgress = intent.getIntExtra("max_progress", 0);
    }

    private void bindData() {
        tvTitle.setText(title != null ? title : "Новое достижение");
        tvDescription.setText(description != null ? description : "Вы получили новое достижение!");
        tvXpReward.setText("+" + xpReward + " XP");

        int iconId = getResources().getIdentifier(
                iconResName != null ? iconResName : "",
                "drawable",
                getPackageName()
        );
        ivAchievement.setImageResource(iconId != 0 ? iconId : R.drawable.ic_launcher_foreground);
    }

    // Запускает анимации появления карточки и декоративных элементов.
    private void setupAnimations() {
        Animation popupIn = AnimationUtils.loadAnimation(this, R.anim.achievement_popup_in);
        Animation slowPulse = AnimationUtils.loadAnimation(this, R.anim.achievement_pulse);
        Animation starFloat = AnimationUtils.loadAnimation(this, R.anim.achievement_star_float);

        findViewById(R.id.cardPopup).startAnimation(popupIn);

        if (ivGlow != null) {
            ivGlow.startAnimation(slowPulse);
        }

        if (ivStarLeft != null) {
            ivStarLeft.startAnimation(starFloat);
        }

        if (ivStarRight != null) {
            ivStarRight.startAnimation(starFloat);
        }
    }

    private void setupClicks() {
        // Возврат в общий список достижений.
        btnContinue.setOnClickListener(v -> goToAchievements());

        // Переход к подробной карточке достижения.
        btnDetails.setOnClickListener(v -> {
            Intent intent = new Intent(this, AchievementDetailActivity.class);
            intent.putExtra("achievement_id", achievementId);
            intent.putExtra("title", title);
            intent.putExtra("description", description);
            intent.putExtra("xp_reward", xpReward);
            intent.putExtra("icon_res_name", iconResName);
            intent.putExtra("current_progress", currentProgress);
            intent.putExtra("max_progress", maxProgress);
            intent.putExtra("is_unlocked", true);
            startActivity(intent);
            finish();
        });
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