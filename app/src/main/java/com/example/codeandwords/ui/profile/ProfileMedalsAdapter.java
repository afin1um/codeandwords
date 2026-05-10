package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.AchievementWithProgress;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProfileMedalsAdapter extends RecyclerView.Adapter<ProfileMedalsAdapter.MedalViewHolder> {

    private final Context context;
    private List<AchievementWithProgress> items = new ArrayList<>();

    public ProfileMedalsAdapter(Context context, List<AchievementWithProgress> items) {
        this.context = context;
        this.items = items != null ? items : new ArrayList<>();
    }

    public void setItems(List<AchievementWithProgress> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MedalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile_medal, parent, false);
        return new MedalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedalViewHolder holder, int position) {
        AchievementWithProgress item = items.get(position);

        @DrawableRes int iconRes = resolveIcon(item);
        Drawable drawable = AppCompatResources.getDrawable(context, iconRes);

        if (drawable != null) {
            holder.ivMedal.setImageDrawable(drawable.mutate());
        } else {
            holder.ivMedal.setImageResource(R.drawable.ic_achievement_default);
        }

        // ✅ Адаптивные цвета (день/ночь)
        int unlockedBg = ContextCompat.getColor(context, R.color.medal_unlocked_bg);
        int unlockedStroke = ContextCompat.getColor(context, R.color.medal_unlocked_stroke);
        int lockedBg = ContextCompat.getColor(context, R.color.medal_locked_bg);
        int lockedStroke = ContextCompat.getColor(context, R.color.medal_locked_stroke);

        if (item != null && item.isUnlocked) {
            holder.ivMedal.clearColorFilter();
            holder.ivMedal.setAlpha(1f);

            holder.cardRoot.setCardBackgroundColor(unlockedBg);
            holder.cardRoot.setStrokeColor(unlockedStroke);
            holder.cardRoot.setStrokeWidth(dp(2));

            holder.ivLockOverlay.setVisibility(View.GONE);
        } else {
            applyGrayFilter(holder.ivMedal);
            holder.ivMedal.setAlpha(0.78f);

            holder.cardRoot.setCardBackgroundColor(lockedBg);
            holder.cardRoot.setStrokeColor(lockedStroke);
            holder.cardRoot.setStrokeWidth(dp(2));

            holder.ivLockOverlay.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> openAchievementDetails(item));
    }

    private void openAchievementDetails(AchievementWithProgress item) {
        if (item == null) return;

        try {
            Intent intent = new Intent(context, AchievementDetailsActivity.class);
            intent.putExtra("achievement_id", item.id != null ? item.id : -1L);
            intent.putExtra("title", item.title != null ? item.title : "");
            intent.putExtra("description", item.description != null ? item.description : "");
            intent.putExtra("xpReward", item.xpReward != null ? item.xpReward : 0);
            intent.putExtra("conditionType", item.conditionType != null ? item.conditionType : "");
            intent.putExtra("conditionValue", item.conditionValue != null ? item.conditionValue : 0);
            intent.putExtra("progress", item.currentProgress);
            intent.putExtra("maxProgress", item.maxProgress != null ? item.maxProgress : 0);
            intent.putExtra("iconResName", item.iconResName != null ? item.iconResName : "");
            intent.putExtra("isUnlocked", item.isUnlocked);
            intent.putExtra("isNew", item.isNew);
            intent.putExtra("dateReceived", item.dateReceived);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Не удалось открыть достижение", Toast.LENGTH_SHORT).show();
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
    private int resolveIcon(AchievementWithProgress item) {
        if (item == null) {
            return R.drawable.ic_achievement_default;
        }

        String normalizedIconName = normalizeResourceName(item.iconResName);

        if (!normalizedIconName.isEmpty()) {
            int byName = context.getResources().getIdentifier(
                    normalizedIconName,
                    "drawable",
                    context.getPackageName()
            );

            if (byName != 0) {
                return byName;
            }
        }

        String normalizedConditionType = normalizeKey(item.conditionType);

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

        String normalizedTitle = normalizeKey(item.title);

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
        if (result.endsWith(".xml")) result = result.substring(0, result.length() - 4);
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

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MedalViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        ImageView ivMedal;
        ImageView ivLockOverlay;

        public MedalViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardMedal);
            ivMedal = itemView.findViewById(R.id.ivMedal);
            ivLockOverlay = itemView.findViewById(R.id.ivLockOverlay);
        }
    }
}