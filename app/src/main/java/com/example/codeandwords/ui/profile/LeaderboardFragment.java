package com.example.codeandwords.ui.profile;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.User;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private LeaderboardAdapter adapter;
    private Repository repository;
    private long myUserId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.rvLeaderboard);
        progressBar = view.findViewById(R.id.pbLeaderboard);

        repository = new Repository(requireContext());

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LeaderboardAdapter();
        recyclerView.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // 1. Сначала узнаем ID текущего пользователя для подсветки
        repository.getCurrentUser(new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                myUserId = user.getId();
                fetchLeaderboard();
            }

            @Override
            public void onError(String error) {
                // Если не вошли, просто грузим список без подсветки
                fetchLeaderboard();
            }
        });
    }

    private void fetchLeaderboard() {
        // ТЕПЕРЬ ЭТОТ МЕТОД ЕСТЬ В РЕПОЗИТОРИИ
        repository.getLeaderboard(new Repository.DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                adapter.setData(users, myUserId);
            }

            @Override
            public void onError(String error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Ошибка: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- АДАПТЕР (Без изменений, проверен на корректность) ---
    private class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {
        private List<User> users = new ArrayList<>();
        private long currentUserId = -1;

        void setData(List<User> users, long currentUserId) {
            this.users = users;
            this.currentUserId = currentUserId;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = users.get(position);
            int rank = position + 1;

            holder.tvRank.setText(String.valueOf(rank));
            holder.tvUsername.setText(user.getUsername());
            holder.tvLevel.setText("Уровень " + user.getCurrentLevel());
            holder.tvXp.setText(user.getTotalXp() + " XP");

            // Цвета позиций
            int rankColor = Color.parseColor("#BDBDBD");
            if (rank == 1) rankColor = Color.parseColor("#FFD700");
            else if (rank == 2) rankColor = Color.parseColor("#C0C0C0");
            else if (rank == 3) rankColor = Color.parseColor("#CD7F32");

            Drawable background = holder.tvRank.getBackground();
            if (background != null) background.setTint(rankColor);

            // Подсветка "Меня" в списке
            if (user.getId() == currentUserId) {
                holder.cardRoot.setStrokeColor(Color.parseColor("#2196F3"));
                holder.cardRoot.setStrokeWidth(6);
                holder.cardRoot.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
            } else {
                holder.cardRoot.setStrokeColor(Color.parseColor("#E0E0E0"));
                holder.cardRoot.setStrokeWidth(2);
                holder.cardRoot.setCardBackgroundColor(Color.WHITE);
            }
        }

        @Override
        public int getItemCount() { return users.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvRank, tvUsername, tvLevel, tvXp;
            MaterialCardView cardRoot;

            ViewHolder(View itemView) {
                super(itemView);
                tvRank = itemView.findViewById(R.id.tvRank);
                tvUsername = itemView.findViewById(R.id.tvUsername);
                tvLevel = itemView.findViewById(R.id.tvLevel);
                tvXp = itemView.findViewById(R.id.tvXp);
                cardRoot = itemView.findViewById(R.id.cardRoot);
            }
        }
    }
}