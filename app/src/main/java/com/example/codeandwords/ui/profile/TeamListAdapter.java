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

// Альтернативный адаптер списка команд (используется там, где нужен отдельный экземпляр).
public class TeamListAdapter extends RecyclerView.Adapter<TeamListAdapter.VH> {

    public interface OnTeamClickListener {
        void onTeamClick(Team team);
    }

    private final List<Team> items = new ArrayList<>();
    private final OnTeamClickListener listener;

    public TeamListAdapter(OnTeamClickListener listener) {
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
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team, parent, false);

        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Team team = items.get(position);

        holder.tvName.setText(team.teamName != null ? team.teamName : "Команда");
        holder.tvSubtitle.setText("Открыть задание команды");

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

    static class VH extends RecyclerView.ViewHolder {

        TextView tvName;
        TextView tvSubtitle;

        VH(@NonNull View itemView) {
            super(itemView);

            tvName = itemView.findViewById(R.id.tvTeamName);
            tvSubtitle = itemView.findViewById(R.id.tvTeamSubtitle);
        }
    }
}