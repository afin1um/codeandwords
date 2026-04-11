package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.AchievementWithProgress;

import java.util.ArrayList;
import java.util.List;

public class AchievementsFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private Repository repository;
    private AchievementsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_achievements, container, false);

        recyclerView = view.findViewById(R.id.recyclerAchievements);
        tvEmpty = view.findViewById(R.id.tvEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new AchievementsAdapter();
        recyclerView.setAdapter(adapter);

        repository = new Repository(requireContext());

        loadAchievements();

        return view;
    }

    private void loadAchievements() {
        repository.getAchievements(new Repository.DataCallback<List<AchievementWithProgress>>() {
            @Override
            public void onSuccess(List<AchievementWithProgress> data) {
                if (data == null || data.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setData(data);
                }
            }

            @Override
            public void onError(String error) {
                tvEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
    }

    private void openAchievementDetails(AchievementWithProgress item) {
        if (item == null || !isAdded()) return;

        Intent intent = new Intent(requireContext(), AchievementDetailActivity.class);
        intent.putExtra("achievement_id", item.id != null ? item.id : -1L);
        intent.putExtra("title", item.title);
        intent.putExtra("description", item.description);
        intent.putExtra("xp_reward", item.xpReward != null ? item.xpReward : 0);
        intent.putExtra("condition_type", item.conditionType);
        intent.putExtra("condition_value", item.conditionValue != null ? item.conditionValue : 0);
        intent.putExtra("max_progress", item.maxProgress != null ? item.maxProgress : 0);
        intent.putExtra("current_progress", item.currentProgress);
        intent.putExtra("icon_res_name", item.iconResName);
        intent.putExtra("is_unlocked", item.isUnlocked);
        intent.putExtra("is_new", item.isNew);
        intent.putExtra("date_received", item.dateReceived);
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

            holder.tvTitle.setText(item.title != null ? item.title : "");
            holder.tvDescription.setText(item.description != null ? item.description : "");

            int max = item.maxProgress != null ? item.maxProgress : 1;
            int current = item.currentProgress;

            holder.progressBar.setMax(max);
            holder.progressBar.setProgress(Math.min(current, max));
            holder.tvProgress.setText(current + " / " + max);

            if (item.isUnlocked) {
                holder.tvStatus.setText("Получено");
            } else {
                holder.tvStatus.setText("В процессе");
            }

            holder.tvNew.setVisibility(item.isNew ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> openAchievementDetails(item));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDescription, tvProgress, tvStatus, tvNew;
            ProgressBar progressBar;

            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvDescription = itemView.findViewById(R.id.tvDescription);
                tvProgress = itemView.findViewById(R.id.tvProgress);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvNew = itemView.findViewById(R.id.tvNew);
                progressBar = itemView.findViewById(R.id.progressBarAchievement);
            }
        }
    }
}