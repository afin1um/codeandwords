package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
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
import com.example.codeandwords.model.LessonHistory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecentLessonsAdapter extends RecyclerView.Adapter<RecentLessonsAdapter.RecentLessonViewHolder> {

    private final List<LessonHistory> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat;

    {
        dateFormat = new SimpleDateFormat("dd.MM • HH:mm", Locale.getDefault());
        // ✅ Явно указываем локальный часовой пояс устройства (для России — GMT+3)
        dateFormat.setTimeZone(java.util.TimeZone.getDefault());
    }

    public void setItems(List<LessonHistory> data) {
        items.clear();

        if (data != null) {
            items.addAll(data);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecentLessonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_lesson, parent, false);
        return new RecentLessonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecentLessonViewHolder holder, int position) {
        LessonHistory item = items.get(position);
        Context context = holder.itemView.getContext();

        LessonTypeFormatter.LessonTypeUi typeUi = LessonTypeFormatter.format(item.lessonType);

        int totalWords = Math.max(0, item.totalWords);
        int mistakes = Math.max(0, item.mistakesCount);
        int correct = Math.max(0, totalWords - mistakes);
        int accuracy = totalWords > 0
                ? Math.max(0, Math.min(100, (correct * 100) / totalWords))
                : 0;

        int accentColor = getLessonAccentColor(context, item.lessonType);
        int iconBackgroundColor = getLessonIconBackgroundColor(context, item.lessonType);
        int accuracyColor = getAccuracyColor(context, accuracy);

        holder.tvLessonIcon.setText(typeUi.getIcon());
        holder.tvLessonIcon.setTextColor(accentColor);
        holder.tvLessonIcon.setBackground(makeOvalBackground(iconBackgroundColor, accentColor));

        holder.tvLessonType.setText(typeUi.getTitle());
        holder.tvLessonType.setTextColor(color(context, R.color.app_text_primary));

        holder.tvLessonSubtitle.setText(typeUi.getSubtitle());
        holder.tvLessonSubtitle.setTextColor(color(context, R.color.app_text_secondary));

        holder.tvLessonDate.setText(formatFinishedAt(item.finishedAt));
        holder.tvLessonDate.setTextColor(color(context, R.color.app_text_muted));

        holder.tvLessonStats.setText(buildSummary(totalWords, mistakes));
        holder.tvLessonStats.setTextColor(accentColor);

        holder.tvLessonXp.setText(formatXp(item.earnedXp));
        holder.tvLessonXp.setTextColor(item.earnedXp > 0
                ? color(context, R.color.app_orange)
                : color(context, R.color.app_text_secondary));

        holder.progressLessonAccuracy.setMax(100);
        holder.progressLessonAccuracy.setProgress(accuracy);
        holder.progressLessonAccuracy.setProgressBackgroundTintList(
                ColorStateList.valueOf(color(context, R.color.app_divider))
        );
        holder.progressLessonAccuracy.setProgressTintList(ColorStateList.valueOf(accuracyColor));

        holder.tvLessonAccuracy.setText(accuracy + "%");
        holder.tvLessonAccuracy.setTextColor(accuracyColor);

        if (item.fixedErrorsCount > 0) {
            holder.tvLessonFixedErrors.setVisibility(View.VISIBLE);
            holder.tvLessonFixedErrors.setText("Исправлено ошибок: " + item.fixedErrorsCount);
            holder.tvLessonFixedErrors.setTextColor(color(context, R.color.app_green));
        } else {
            holder.tvLessonFixedErrors.setVisibility(View.GONE);
        }

        bindBadge(holder, item, mistakes);
    }

    private void bindBadge(RecentLessonViewHolder holder, LessonHistory item, int mistakes) {
        Context context = holder.itemView.getContext();

        if (item.wasPerfect) {
            holder.tvLessonBadge.setVisibility(View.VISIBLE);
            holder.tvLessonBadge.setText("Без ошибок");
            holder.tvLessonBadge.setTextColor(color(context, R.color.app_green));
            holder.tvLessonBadge.setBackground(makeRoundedBackground(
                    color(context, R.color.app_surface_green),
                    color(context, R.color.app_green),
                    dp(holder.itemView, 14)
            ));
            return;
        }

        if (mistakes > 0) {
            holder.tvLessonBadge.setVisibility(View.VISIBLE);
            holder.tvLessonBadge.setText("Есть ошибки");
            holder.tvLessonBadge.setTextColor(color(context, R.color.app_red));
            holder.tvLessonBadge.setBackground(makeRoundedBackground(
                    color(context, R.color.app_surface_red),
                    color(context, R.color.app_red),
                    dp(holder.itemView, 14)
            ));
            return;
        }

        holder.tvLessonBadge.setVisibility(View.GONE);
    }

    private int getLessonAccentColor(Context context, String lessonType) {
        String normalized = normalizeLessonType(lessonType);

        switch (normalized) {
            case "TRAINING_MISTAKES":
            case "LEARNED_WORDS":
            case "DICTIONARY":
                return color(context, R.color.app_green);

            case "SPRINT":
                return color(context, R.color.app_orange);

            case "LISTENING":
            case "TRAINING_LISTENING":
                return color(context, R.color.app_blue);

            case "WRITE_WORD":
            case "WRITING":
            case "TRAINING_WORDS":
            case "MATCHING":
            case "THEORY":
            default:
                return color(context, R.color.app_blue);
        }
    }

    private int getLessonIconBackgroundColor(Context context, String lessonType) {
        String normalized = normalizeLessonType(lessonType);

        switch (normalized) {
            case "TRAINING_MISTAKES":
            case "LEARNED_WORDS":
            case "DICTIONARY":
                return color(context, R.color.app_surface_green);

            case "SPRINT":
                return color(context, R.color.app_surface_orange);

            case "LISTENING":
            case "TRAINING_LISTENING":
            case "WRITE_WORD":
            case "WRITING":
            case "TRAINING_WORDS":
            case "MATCHING":
            case "THEORY":
            default:
                return color(context, R.color.app_surface_blue);
        }
    }

    private String normalizeLessonType(String lessonType) {
        if (lessonType == null || lessonType.trim().isEmpty()) {
            return "";
        }

        return lessonType.trim().toUpperCase(Locale.ROOT);
    }

    private int getAccuracyColor(Context context, int accuracy) {
        if (accuracy >= 90) {
            return color(context, R.color.app_green);
        }

        if (accuracy >= 70) {
            return color(context, R.color.app_orange);
        }

        return color(context, R.color.app_red);
    }

    private String formatFinishedAt(long finishedAt) {
        if (finishedAt <= 0) {
            return "";
        }

        return dateFormat.format(new Date(finishedAt));
    }

    private String buildSummary(int totalWords, int mistakes) {
        return totalWords + " слов • " + mistakes + " ошибок";
    }

    private String formatXp(int xp) {
        if (xp <= 0) {
            return "0 XP";
        }

        return "+" + xp + " XP";
    }

    private GradientDrawable makeOvalBackground(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        drawable.setStroke(2, strokeColor);
        return drawable;
    }

    private GradientDrawable makeRoundedBackground(int fillColor, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, strokeColor);
        return drawable;
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private int color(Context context, @ColorRes int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class RecentLessonViewHolder extends RecyclerView.ViewHolder {

        TextView tvLessonIcon;
        TextView tvLessonType;
        TextView tvLessonSubtitle;
        TextView tvLessonDate;
        TextView tvLessonStats;
        TextView tvLessonXp;
        TextView tvLessonFixedErrors;
        TextView tvLessonBadge;
        TextView tvLessonAccuracy;
        ProgressBar progressLessonAccuracy;

        RecentLessonViewHolder(@NonNull View itemView) {
            super(itemView);

            tvLessonIcon = itemView.findViewById(R.id.tvLessonIcon);
            tvLessonType = itemView.findViewById(R.id.tvLessonType);
            tvLessonSubtitle = itemView.findViewById(R.id.tvLessonSubtitle);
            tvLessonDate = itemView.findViewById(R.id.tvLessonDate);
            tvLessonStats = itemView.findViewById(R.id.tvLessonStats);
            tvLessonXp = itemView.findViewById(R.id.tvLessonXp);
            tvLessonFixedErrors = itemView.findViewById(R.id.tvLessonFixedErrors);
            tvLessonBadge = itemView.findViewById(R.id.tvLessonBadge);
            tvLessonAccuracy = itemView.findViewById(R.id.tvLessonAccuracy);
            progressLessonAccuracy = itemView.findViewById(R.id.progressLessonAccuracy);
        }
    }
}