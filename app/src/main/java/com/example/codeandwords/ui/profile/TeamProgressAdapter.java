package com.example.codeandwords.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.TeamChallengeProgress;

import java.util.ArrayList;
import java.util.List;

public class TeamProgressAdapter extends RecyclerView.Adapter<TeamProgressAdapter.VH> {

    private final List<TeamChallengeProgress> items = new ArrayList<>();
    private int targetValue = 100;

    public TeamProgressAdapter() {
    }

    public void setItems(List<TeamChallengeProgress> data, int targetValue) {
        items.clear();

        if (data != null) {
            items.addAll(data);
        }

        this.targetValue = Math.max(targetValue, 1);
        notifyDataSetChanged();
    }

    public void setItems(List<TeamChallengeProgress> data) {
        setItems(data, targetValue);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team_progress, parent, false);

        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TeamChallengeProgress item = items.get(position);

        String name = item.username != null && !item.username.trim().isEmpty()
                ? item.username.trim()
                : "Участник #" + item.userId;

        holder.tvParticipantName.setText(name);

        int progress = Math.max(item.progress, 0);
        int safeTarget = Math.max(targetValue, 1);

        holder.tvParticipantProgress.setText(progress + " / " + safeTarget);
        holder.progressParticipant.setMax(safeTarget);
        holder.progressParticipant.setProgress(Math.min(progress, safeTarget));

        if (item.isCompleted) {
            String placeText = item.place != null && item.place > 0
                    ? "Завершил · место: " + item.place
                    : "Завершил";

            if (item.awardedXp > 0) {
                placeText += " · +" + item.awardedXp + " XP";
            }

            holder.tvParticipantStatus.setText(placeText);
        } else {
            holder.tvParticipantStatus.setText("В процессе");
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvParticipantName;
        TextView tvParticipantProgress;
        TextView tvParticipantStatus;
        ProgressBar progressParticipant;

        VH(@NonNull View itemView) {
            super(itemView);

            tvParticipantName = itemView.findViewById(R.id.tvParticipantName);
            tvParticipantProgress = itemView.findViewById(R.id.tvParticipantProgress);
            tvParticipantStatus = itemView.findViewById(R.id.tvParticipantStatus);
            progressParticipant = itemView.findViewById(R.id.progressParticipant);
        }
    }
}