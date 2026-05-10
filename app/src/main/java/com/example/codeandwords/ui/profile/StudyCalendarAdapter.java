package com.example.codeandwords.ui.profile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;

import java.util.ArrayList;
import java.util.List;

public class StudyCalendarAdapter extends RecyclerView.Adapter<StudyCalendarAdapter.CalendarViewHolder> {

    private static final float PRESSED_SCALE = 0.94f;
    private static final float NORMAL_SCALE = 1.0f;

    private static final long PRESS_DURATION = 70L;
    private static final long RELEASE_DURATION = 120L;

    public interface OnDayClickListener {
        void onDayClick(String date);
    }

    public static class CalendarDay {
        public String date;
        public String dayText;
        public boolean isCurrentMonth;
        public boolean isSelected;
        public boolean isToday;
        public int eventsCount;

        public CalendarDay(String date,
                           String dayText,
                           boolean isCurrentMonth,
                           boolean isSelected,
                           boolean isToday,
                           int eventsCount) {
            this.date = date;
            this.dayText = dayText;
            this.isCurrentMonth = isCurrentMonth;
            this.isSelected = isSelected;
            this.isToday = isToday;
            this.eventsCount = eventsCount;
        }
    }

    private final List<CalendarDay> items = new ArrayList<>();
    private final OnDayClickListener listener;

    public StudyCalendarAdapter(OnDayClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<CalendarDay> days) {
        items.clear();

        if (days != null) {
            items.addAll(days);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CalendarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_study_calendar_day, parent, false);

        return new CalendarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarViewHolder holder, int position) {
        CalendarDay item = items.get(position);
        int safeEventsCount = Math.max(0, item.eventsCount);

        holder.itemView.setScaleX(NORMAL_SCALE);
        holder.itemView.setScaleY(NORMAL_SCALE);

        holder.tvDayNumber.setText(item.dayText != null ? item.dayText : "");

        holder.root.setBackground(buildDayBackground(holder.itemView, item, safeEventsCount));

        bindDayText(holder, item, safeEventsCount);
        bindEventsBadge(holder, item, safeEventsCount);
        bindAlpha(holder, item);
        bindClick(holder, item);
        bindPressAnimation(holder);
    }

    private GradientDrawable buildDayBackground(View view, CalendarDay item, int eventsCount) {
        Context context = view.getContext();

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(view, 14));

        if (item.isSelected) {
            background.setColor(color(context, R.color.app_blue));
            background.setStroke(dp(view, 2), color(context, R.color.app_blue));
            return background;
        }

        if (item.isToday) {
            background.setColor(color(context, R.color.app_surface_blue));
            background.setStroke(dp(view, 2), color(context, R.color.app_blue));
            return background;
        }

        if (eventsCount > 0 && item.isCurrentMonth) {
            background.setColor(color(context, R.color.app_surface_green));
            background.setStroke(dp(view, 1), color(context, R.color.app_green));
            return background;
        }

        background.setColor(color(context, R.color.app_card_bg));
        background.setStroke(dp(view, 1), color(context, R.color.app_card_stroke));

        return background;
    }

    private void bindDayText(CalendarViewHolder holder, CalendarDay item, int eventsCount) {
        Context context = holder.itemView.getContext();

        if (item.isSelected) {
            holder.tvDayNumber.setTextColor(color(context, R.color.app_text_on_accent));
            holder.tvDayNumber.setTextSize(16);
            holder.tvDayNumber.setTypeface(null, Typeface.BOLD);
            return;
        }

        if (!item.isCurrentMonth) {
            holder.tvDayNumber.setTextColor(color(context, R.color.app_text_muted));
            holder.tvDayNumber.setTextSize(15);
            holder.tvDayNumber.setTypeface(null, Typeface.NORMAL);
            return;
        }

        if (item.isToday || eventsCount > 0) {
            holder.tvDayNumber.setTextColor(color(context, R.color.app_text_primary));
            holder.tvDayNumber.setTextSize(16);
            holder.tvDayNumber.setTypeface(null, Typeface.BOLD);
            return;
        }

        holder.tvDayNumber.setTextColor(color(context, R.color.app_text_primary));
        holder.tvDayNumber.setTextSize(15);
        holder.tvDayNumber.setTypeface(null, Typeface.NORMAL);
    }

    private void bindEventsBadge(CalendarViewHolder holder, CalendarDay item, int eventsCount) {
        Context context = holder.itemView.getContext();

        if (eventsCount <= 0) {
            holder.tvEventCount.setVisibility(View.INVISIBLE);
            holder.tvEventCount.setText("");
            holder.tvEventCount.setBackground(null);
            return;
        }

        holder.tvEventCount.setVisibility(View.VISIBLE);
        holder.tvEventCount.setText(formatEventsCount(eventsCount));

        if (item.isSelected) {
            holder.tvEventCount.setTextColor(color(context, R.color.app_blue));
            holder.tvEventCount.setBackground(buildBadgeBackground(
                    holder.itemView,
                    color(context, R.color.app_text_on_accent),
                    color(context, R.color.app_text_on_accent)
            ));
            return;
        }

        if (!item.isCurrentMonth) {
            holder.tvEventCount.setTextColor(color(context, R.color.app_text_secondary));
            holder.tvEventCount.setBackground(buildBadgeBackground(
                    holder.itemView,
                    color(context, R.color.app_surface_soft),
                    color(context, R.color.app_card_stroke)
            ));
            return;
        }

        holder.tvEventCount.setTextColor(color(context, R.color.app_green));
        holder.tvEventCount.setBackground(buildBadgeBackground(
                holder.itemView,
                color(context, R.color.app_surface_green),
                color(context, R.color.app_green)
        ));
    }

    private GradientDrawable buildBadgeBackground(View view, int fillColor, int strokeColor) {
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.RECTANGLE);
        badge.setColor(fillColor);
        badge.setCornerRadius(dp(view, 10));
        badge.setStroke(dp(view, 1), strokeColor);
        return badge;
    }

    private String formatEventsCount(int count) {
        if (count > 99) {
            return "99+";
        }

        return String.valueOf(count);
    }

    private void bindAlpha(CalendarViewHolder holder, CalendarDay item) {
        holder.itemView.setAlpha(item.isCurrentMonth ? 1f : 0.55f);
    }

    private void bindClick(CalendarViewHolder holder, CalendarDay item) {
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && item.date != null && !item.date.trim().isEmpty()) {
                listener.onDayClick(item.date);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindPressAnimation(CalendarViewHolder holder) {
        holder.itemView.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate()
                            .scaleX(PRESSED_SCALE)
                            .scaleY(PRESSED_SCALE)
                            .setDuration(PRESS_DURATION)
                            .start();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate()
                            .scaleX(NORMAL_SCALE)
                            .scaleY(NORMAL_SCALE)
                            .setDuration(RELEASE_DURATION)
                            .start();
                    break;

                default:
                    break;
            }

            return false;
        });
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

    static class CalendarViewHolder extends RecyclerView.ViewHolder {

        LinearLayout root;
        TextView tvDayNumber;
        TextView tvEventCount;

        CalendarViewHolder(@NonNull View itemView) {
            super(itemView);

            root = itemView.findViewById(R.id.calendarDayRoot);
            tvDayNumber = itemView.findViewById(R.id.tvCalendarDayNumber);
            tvEventCount = itemView.findViewById(R.id.tvCalendarEventCount);
        }
    }
}