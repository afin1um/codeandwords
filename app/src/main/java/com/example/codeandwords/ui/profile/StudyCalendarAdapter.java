package com.example.codeandwords.ui.profile;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;

import java.util.ArrayList;
import java.util.List;

public class StudyCalendarAdapter extends RecyclerView.Adapter<StudyCalendarAdapter.CalendarViewHolder> {

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

        holder.tvDayNumber.setText(item.dayText);

        if (item.isCurrentMonth) {
            holder.tvDayNumber.setTextColor(Color.WHITE);
            holder.itemView.setAlpha(1f);
        } else {
            holder.tvDayNumber.setTextColor(Color.parseColor("#4D6470"));
            holder.itemView.setAlpha(0.55f);
        }

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(holder.itemView, 14));

        if (item.isSelected) {
            background.setColor(Color.parseColor("#1CB0F6"));
            background.setStroke(dp(holder.itemView, 2), Color.parseColor("#7DD7FF"));
            holder.tvDayNumber.setTextColor(Color.WHITE);
        } else if (item.isToday) {
            background.setColor(Color.parseColor("#17303B"));
            background.setStroke(dp(holder.itemView, 2), Color.parseColor("#1CB0F6"));
        } else {
            background.setColor(Color.parseColor("#10232D"));
            background.setStroke(dp(holder.itemView, 1), Color.parseColor("#203A46"));
        }

        holder.root.setBackground(background);

        if (item.eventsCount > 0) {
            holder.tvEventCount.setVisibility(View.VISIBLE);
            holder.tvEventCount.setText(String.valueOf(item.eventsCount));
        } else {
            holder.tvEventCount.setVisibility(View.INVISIBLE);
            holder.tvEventCount.setText("");
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null && item.date != null) {
                listener.onDayClick(item.date);
            }
        });
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CalendarViewHolder extends RecyclerView.ViewHolder {

        LinearLayout root;
        TextView tvDayNumber;
        TextView tvEventCount;

        public CalendarViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.calendarDayRoot);
            tvDayNumber = itemView.findViewById(R.id.tvCalendarDayNumber);
            tvEventCount = itemView.findViewById(R.id.tvCalendarEventCount);
        }
    }
}