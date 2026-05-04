package com.example.codeandwords.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.StudySchedule;

import java.util.ArrayList;
import java.util.List;

public class StudyScheduleAdapter extends RecyclerView.Adapter<StudyScheduleAdapter.ScheduleViewHolder> {

    public interface OnScheduleClickListener {
        void onScheduleClick(StudySchedule schedule);
    }

    private final List<StudySchedule> items = new ArrayList<>();
    private final OnScheduleClickListener listener;

    public StudyScheduleAdapter(OnScheduleClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<StudySchedule> schedules) {
        items.clear();
        if (schedules != null) {
            items.addAll(schedules);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_study_schedule, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        StudySchedule schedule = items.get(position);

        holder.tvScheduleShortTitle.setText(makeShortTitle(schedule.themeTitle));
        holder.tvScheduleTheme.setText(schedule.themeTitle);
        holder.tvScheduleTime.setText(schedule.startTime + " — " + schedule.endTime);

        if (schedule.note == null || schedule.note.trim().isEmpty()) {
            holder.tvScheduleNote.setVisibility(View.GONE);
        } else {
            holder.tvScheduleNote.setVisibility(View.VISIBLE);
            holder.tvScheduleNote.setText(schedule.note);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onScheduleClick(schedule);
            }
        });
    }

    private String makeShortTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "ЗН";
        }

        String clean = title.trim();
        String[] parts = clean.split("\\s+");

        if (parts.length >= 2) {
            String first = parts[0].substring(0, 1);
            String second = parts[1].substring(0, 1);
            return (first + second).toUpperCase();
        }

        if (clean.length() >= 2) {
            return clean.substring(0, 2).toUpperCase();
        }

        return clean.toUpperCase();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ScheduleViewHolder extends RecyclerView.ViewHolder {

        TextView tvScheduleShortTitle;
        TextView tvScheduleTheme;
        TextView tvScheduleTime;
        TextView tvScheduleNote;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvScheduleShortTitle = itemView.findViewById(R.id.tvScheduleShortTitle);
            tvScheduleTheme = itemView.findViewById(R.id.tvScheduleTheme);
            tvScheduleTime = itemView.findViewById(R.id.tvScheduleTime);
            tvScheduleNote = itemView.findViewById(R.id.tvScheduleNote);
        }
    }
}