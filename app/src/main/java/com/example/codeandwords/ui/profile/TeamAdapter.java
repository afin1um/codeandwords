package com.example.codeandwords.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.Team;

import java.util.ArrayList;
import java.util.List;

public class TeamAdapter extends RecyclerView.Adapter<TeamAdapter.TeamViewHolder> {

    public interface OnTeamClickListener {
        void onTeamClick(Team team);
    }

    private final List<Team> items = new ArrayList<>();
    private final OnTeamClickListener listener;

    public TeamAdapter(OnTeamClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Team> teams) {
        items.clear();

        if (teams != null) {
            items.addAll(teams);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TeamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team, parent, false);

        return new TeamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TeamViewHolder holder, int position) {
        Team team = items.get(position);

        String name = team.teamName != null && !team.teamName.trim().isEmpty()
                ? team.teamName.trim()
                : "Команда";

        holder.tvTeamName.setText(name);
        holder.tvTeamSubtitle.setText("Открыть задание команды");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTeamClick(team);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TeamViewHolder extends RecyclerView.ViewHolder {

        TextView tvTeamName;
        TextView tvTeamSubtitle;

        TeamViewHolder(@NonNull View itemView) {
            super(itemView);

            tvTeamName = itemView.findViewById(R.id.tvTeamName);
            tvTeamSubtitle = itemView.findViewById(R.id.tvTeamSubtitle);
        }
    }
}