package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.AchievementWithProgress;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AchievementsFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private TextView tvUnlockedCount;
    private TextView tvTotalCount;
    private TextView tvSummaryText;
    private ProgressBar progressAchievementsSummary;

    private Repository repository;
    private AchievementsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_achievements, container, false);

        initViews(view);
        setupRecycler();

        repository = new Repository(requireContext());
        loadAchievements();

        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerAchievements);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvUnlockedCount = view.findViewById(R.id.tvUnlockedCount);
        tvTotalCount = view.findViewById(R.id.tvTotalCount);
        tvSummaryText = view.findViewById(R.id.tvSummaryText);
        progressAchievementsSummary = view.findViewById(R.id.progressAchievementsSummary);
    }

    private void setupRecycler() {
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerView.setClipToPadding(false);
        recyclerView.setNestedScrollingEnabled(false);

        adapter = new AchievementsAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void loadAchievements() {
        repository.getAchievements(new Repository.DataCallback<List<AchievementWithProgress>>() {
            @Override
            public void onSuccess(List<AchievementWithProgress> data) {
                if (!isAdded()) return;

                List<AchievementWithProgress> achievements = prepareAchievements(data);
                updateSummary(achievements);

                if (achievements.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setData(achievements);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;

                tvUnlockedCount.setText("0");
                tvTotalCount.setText("0");
                tvSummaryText.setText("Достижения пока не загружены");
                progressAchievementsSummary.setProgress(0);

                tvEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
    }

    private List<AchievementWithProgress> prepareAchievements(List<AchievementWithProgress> data) {
        List<AchievementWithProgress> result = new ArrayList<>();

        if (data != null) {
            for (AchievementWithProgress item : data) {
                if (item != null) {
                    result.add(item);
                }
            }
        }

        Collections.sort(result, new Comparator<AchievementWithProgress>() {
            @Override
            public int compare(AchievementWithProgress a1, AchievementWithProgress a2) {
                if (a1 == null && a2 == null) return 0;
                if (a1 == null) return 1;
                if (a2 == null) return -1;

                if (a1.isUnlocked && !a2.isUnlocked) return -1;
                if (!a1.isUnlocked && a2.isUnlocked) return 1;

                if (a1.isNew && !a2.isNew) return -1;
                if (!a1.isNew && a2.isNew) return 1;

                long id1 = a1.id != null ? a1.id : Long.MAX_VALUE;
                long id2 = a2.id != null ? a2.id : Long.MAX_VALUE;

                return Long.compare(id1, id2);
            }
        });

        return result;
    }

    private void updateSummary(List<AchievementWithProgress> achievements) {
        int total = achievements != null ? achievements.size() : 0;
        int unlocked = 0;

        if (achievements != null) {
            for (AchievementWithProgress item : achievements) {
                if (item != null && item.isUnlocked) {
                    unlocked++;
                }
            }
        }

        tvUnlockedCount.setText(String.valueOf(unlocked));
        tvTotalCount.setText(String.valueOf(total));

        progressAchievementsSummary.setProgressBackgroundTintList(
                ColorStateList.valueOf(color(R.color.app_divider))
        );
        progressAchievementsSummary.setProgressTintList(
                ColorStateList.valueOf(color(R.color.app_green))
        );

        if (total <= 0) {
            progressAchievementsSummary.setMax(100);
            progressAchievementsSummary.setProgress(0);
            tvSummaryText.setText("Пока нет доступных достижений");
            return;
        }

        progressAchievementsSummary.setMax(total);
        progressAchievementsSummary.setProgress(unlocked);
        tvSummaryText.setText("Получено " + unlocked + " из " + total + " достижений");
    }

    private void openAchievementDetails(AchievementWithProgress item) {
        if (item == null || !isAdded()) return;

        Intent intent = new Intent(requireContext(), AchievementDetailsActivity.class);
        intent.putExtra("achievement_id", item.id != null ? item.id : -1L);
        intent.putExtra("title", item.title != null ? item.title : "");
        intent.putExtra("description", item.description != null ? item.description : "");
        intent.putExtra("xpReward", item.xpReward != null ? item.xpReward : 0);
        intent.putExtra("conditionType", item.conditionType != null ? item.conditionType : "");
        intent.putExtra("conditionValue", item.conditionValue != null ? item.conditionValue : 0);
        intent.putExtra("maxProgress", item.maxProgress != null ? item.maxProgress : 0);
        intent.putExtra("progress", item.currentProgress);
        intent.putExtra("iconResName", item.iconResName != null ? item.iconResName : "");
        intent.putExtra("isUnlocked", item.isUnlocked);
        intent.putExtra("isNew", item.isNew);
        intent.putExtra("dateReceived", item.dateReceived);
        startActivity(intent);
    }

    private class AchievementsAdapter extends RecyclerView.Adapter<AchievementsAdapter.ViewHolder> {

        private List<AchievementWithProgress> list = new ArrayList<>();

        public void setData(List<AchievementWithProgress> data) {
            list = data != null ? data : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_achievement_full, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AchievementWithProgress item = list.get(position);

            String title = item.title != null ? item.title : "Достижение";
            String description = item.description != null ? item.description : "";

            int max = item.maxProgress != null && item.maxProgress > 0 ? item.maxProgress : 1;
            int current = Math.max(0, item.currentProgress);
            int safeProgress = Math.min(current, max);

            holder.tvTitle.setText(title);
            holder.tvDescription.setText(description);
            holder.progressBar.setMax(max);
            holder.progressBar.setProgress(safeProgress);
            holder.tvProgress.setText(safeProgress + " / " + max);

            int iconRes = resolveIcon(item);
            Drawable drawable = AppCompatResources.getDrawable(requireContext(), iconRes);

            if (drawable != null) {
                holder.ivAchievementIcon.setImageDrawable(drawable.mutate());
            } else {
                holder.ivAchievementIcon.setImageResource(R.drawable.ic_achievement_default);
            }

            if (item.xpReward != null && item.xpReward > 0) {
                holder.tvXpReward.setText("+" + item.xpReward + " XP");
                holder.tvXpReward.setVisibility(View.VISIBLE);
            } else {
                holder.tvXpReward.setVisibility(View.GONE);
            }

            holder.tvNew.setVisibility(item.isNew ? View.VISIBLE : View.GONE);

            holder.cardRoot.setCardBackgroundColor(color(R.color.app_card_bg));

            holder.tvTitle.setTextColor(color(R.color.app_text_primary));
            holder.tvDescription.setTextColor(color(R.color.app_text_secondary));
            holder.tvProgress.setTextColor(color(R.color.app_text_secondary));

            holder.progressBar.setProgressBackgroundTintList(
                    ColorStateList.valueOf(color(R.color.app_divider))
            );

            if (item.isUnlocked) {
                holder.tvStatus.setText("Получено");
                holder.tvStatus.setTextColor(color(R.color.app_green));

                holder.cardRoot.setStrokeColor(color(R.color.app_green));
                holder.cardRoot.setStrokeWidth(dp(1));

                holder.cardIconCircle.setCardBackgroundColor(color(R.color.app_surface_green));
                holder.cardIconCircle.setStrokeColor(color(R.color.app_green));
                holder.cardIconCircle.setStrokeWidth(dp(2));

                holder.ivAchievementIcon.clearColorFilter();
                holder.ivAchievementIcon.setAlpha(1f);
                holder.ivLock.setVisibility(View.GONE);

                holder.progressBar.setProgressTintList(
                        ColorStateList.valueOf(color(R.color.app_green))
                );
            } else {
                holder.tvStatus.setText("В процессе");
                holder.tvStatus.setTextColor(color(R.color.app_text_secondary));

                holder.cardRoot.setStrokeColor(color(R.color.app_card_stroke));
                holder.cardRoot.setStrokeWidth(dp(1));

                holder.cardIconCircle.setCardBackgroundColor(color(R.color.app_surface_soft));
                holder.cardIconCircle.setStrokeColor(color(R.color.app_card_stroke));
                holder.cardIconCircle.setStrokeWidth(dp(2));

                applyGrayFilter(holder.ivAchievementIcon);
                holder.ivAchievementIcon.setAlpha(0.75f);
                holder.ivLock.setVisibility(View.VISIBLE);

                holder.progressBar.setProgressTintList(
                        ColorStateList.valueOf(color(R.color.app_blue))
                );
            }

            holder.itemView.setOnClickListener(v -> openAchievementDetails(item));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardRoot;
            MaterialCardView cardIconCircle;
            ImageView ivAchievementIcon;
            ImageView ivLock;
            TextView tvTitle;
            TextView tvDescription;
            TextView tvProgress;
            TextView tvStatus;
            TextView tvNew;
            TextView tvXpReward;
            ProgressBar progressBar;

            ViewHolder(View itemView) {
                super(itemView);

                cardRoot = itemView.findViewById(R.id.cardAchievementRoot);
                cardIconCircle = itemView.findViewById(R.id.cardIconCircle);
                ivAchievementIcon = itemView.findViewById(R.id.ivAchievementIcon);
                ivLock = itemView.findViewById(R.id.ivLock);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvDescription = itemView.findViewById(R.id.tvDescription);
                tvProgress = itemView.findViewById(R.id.tvProgress);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvNew = itemView.findViewById(R.id.tvNew);
                tvXpReward = itemView.findViewById(R.id.tvXpReward);
                progressBar = itemView.findViewById(R.id.progressBarAchievement);
            }
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
            int byName = getResources().getIdentifier(
                    normalizedIconName,
                    "drawable",
                    requireContext().getPackageName()
            );

            if (byName != 0) {
                return byName;
            }
        }

        String normalizedConditionType = normalizeKey(item.conditionType);

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

        String normalizedTitle = normalizeKey(item.title);

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

    private int color(@ColorRes int colorRes) {
        Context context = requireContext();
        return ContextCompat.getColor(context, colorRes);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}