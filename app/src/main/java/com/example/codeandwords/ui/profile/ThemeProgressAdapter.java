package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.ThemeProgressStats;

import java.util.ArrayList;
import java.util.List;

public class ThemeProgressAdapter extends RecyclerView.Adapter<ThemeProgressAdapter.ThemeProgressViewHolder> {

    private final List<ThemeProgressStats> items = new ArrayList<>();

    public void setItems(List<ThemeProgressStats> data) {
        items.clear();

        if (data != null) {
            items.addAll(data);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ThemeProgressViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_theme_progress, parent, false);
        return new ThemeProgressViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThemeProgressViewHolder holder, int position) {
        ThemeProgressStats stats = items.get(position);
        Theme theme = stats.getTheme();

        String title = theme != null && theme.getTitle() != null && !theme.getTitle().trim().isEmpty()
                ? theme.getTitle().trim()
                : "Без названия";

        String difficulty = theme != null && theme.getDifficultyLevel() != null && !theme.getDifficultyLevel().trim().isEmpty()
                ? theme.getDifficultyLevel().trim()
                : "Easy";

        int learned = Math.max(0, stats.getLearnedWords());
        int total = Math.max(0, stats.getTotalWords());
        int percent = Math.max(0, Math.min(100, stats.getProgressPercent()));

        holder.tvThemeTitle.setText(title);
        holder.tvThemeDifficulty.setText(localizeDifficulty(difficulty));
        holder.tvThemeProgressText.setText("Изучено " + learned + " из " + total);
        holder.tvThemePercent.setText(percent + "%");

        holder.progressTheme.setMax(100);
        holder.progressTheme.setProgress(percent);

        Context context = holder.itemView.getContext();

        holder.tvThemeTitle.setTextColor(color(context, R.color.app_text_primary));
        holder.tvThemeProgressText.setTextColor(color(context, R.color.app_text_secondary));
        holder.tvThemeDifficulty.setTextColor(getDifficultyColor(context, difficulty));
        holder.progressTheme.setProgressBackgroundTintList(ColorStateList.valueOf(color(context, R.color.app_divider)));

        if (total == 0) {
            bindStatus(
                    holder,
                    "Нет терминов",
                    R.color.app_text_secondary,
                    R.color.app_card_stroke
            );
            return;
        }

        if (stats.isMastered()) {
            bindStatus(
                    holder,
                    "Освоена",
                    R.color.app_green,
                    R.color.app_green
            );
        } else if (learned > 0) {
            bindStatus(
                    holder,
                    "В процессе",
                    R.color.app_blue,
                    R.color.app_blue
            );
        } else {
            bindStatus(
                    holder,
                    "Не начата",
                    R.color.app_text_secondary,
                    R.color.app_card_stroke
            );
        }
    }

    private void bindStatus(ThemeProgressViewHolder holder,
                            String status,
                            @ColorRes int textColorRes,
                            @ColorRes int progressColorRes) {
        Context context = holder.itemView.getContext();

        int textColor = color(context, textColorRes);
        int progressColor = color(context, progressColorRes);

        holder.tvThemeStatus.setText(status);
        holder.tvThemeStatus.setTextColor(textColor);
        holder.tvThemePercent.setTextColor(textColor);
        holder.progressTheme.setProgressTintList(ColorStateList.valueOf(progressColor));
    }

    private String localizeDifficulty(String difficulty) {
        if (difficulty == null) {
            return "Лёгкая";
        }

        String normalized = difficulty.trim().toLowerCase();

        switch (normalized) {
            case "easy":
                return "Лёгкая";

            case "medium":
                return "Средняя";

            case "hard":
                return "Сложная";

            default:
                return difficulty;
        }
    }

    private int getDifficultyColor(Context context, String difficulty) {
        if (difficulty == null) {
            return color(context, R.color.app_text_secondary);
        }

        String normalized = difficulty.trim().toLowerCase();

        switch (normalized) {
            case "easy":
                return color(context, R.color.app_green);

            case "medium":
                return color(context, R.color.app_orange);

            case "hard":
                return color(context, R.color.app_red);

            default:
                return color(context, R.color.app_blue);
        }
    }

    private int color(Context context, @ColorRes int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ThemeProgressViewHolder extends RecyclerView.ViewHolder {

        TextView tvThemeTitle;
        TextView tvThemeDifficulty;
        TextView tvThemeProgressText;
        TextView tvThemePercent;
        TextView tvThemeStatus;
        ProgressBar progressTheme;

        ThemeProgressViewHolder(@NonNull View itemView) {
            super(itemView);

            tvThemeTitle = itemView.findViewById(R.id.tvThemeProgressTitle);
            tvThemeDifficulty = itemView.findViewById(R.id.tvThemeProgressDifficulty);
            tvThemeProgressText = itemView.findViewById(R.id.tvThemeProgressText);
            tvThemePercent = itemView.findViewById(R.id.tvThemeProgressPercent);
            tvThemeStatus = itemView.findViewById(R.id.tvThemeProgressStatus);
            progressTheme = itemView.findViewById(R.id.progressTheme);
        }
    }
}