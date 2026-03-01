package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.AchievementWithProgress;

import java.util.ArrayList;
import java.util.List;

public class AchievementsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private AchievementsAdapter adapter;
    private Repository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_achievements, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.rvAchievements);
        progressBar = view.findViewById(R.id.progressBar);
        repository = new Repository(requireContext());

        // Сетка 3 колонки
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        adapter = new AchievementsAdapter(requireContext());
        recyclerView.setAdapter(adapter);

        loadAchievements();
    }

    private void loadAchievements() {
        progressBar.setVisibility(View.VISIBLE);
        repository.getAchievements(new Repository.DataCallback<List<AchievementWithProgress>>() {
            @Override
            public void onSuccess(List<AchievementWithProgress> data) {
                progressBar.setVisibility(View.GONE);
                adapter.setAchievements(data);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private class AchievementsAdapter extends RecyclerView.Adapter<AchievementsAdapter.ViewHolder> {
        private List<AchievementWithProgress> achievements = new ArrayList<>();
        private Context context;

        public AchievementsAdapter(Context context) {
            this.context = context;
        }

        void setAchievements(List<AchievementWithProgress> achievements) {
            this.achievements = achievements;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_achievement, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AchievementWithProgress item = achievements.get(position);

            holder.tvTitle.setText(item.achievement.getTitle());

            // Ищем картинку по имени. Если нет картинки - ставим стандартного робота
            int iconId = context.getResources().getIdentifier(
                    item.achievement.getIconResName(), "drawable", context.getPackageName());

            if (iconId != 0) {
                holder.ivIcon.setImageResource(iconId);
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_launcher_foreground);
            }

            int currentProgress = (item.currentProgress == null) ? 0 : item.currentProgress;
            int maxProgress = item.achievement.getMaxProgress();

            holder.progressBar.setMax(maxProgress);
            holder.progressBar.setProgress(currentProgress);
            holder.tvProgress.setText(currentProgress + " из " + maxProgress);

            if (item.isNew != null && item.isNew) {
                holder.tvNewBadge.setVisibility(View.VISIBLE);
            } else {
                holder.tvNewBadge.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() { return achievements.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvTitle, tvProgress, tvNewBadge;
            ProgressBar progressBar;

            ViewHolder(View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvProgress = itemView.findViewById(R.id.tvProgress);
                tvNewBadge = itemView.findViewById(R.id.tvNewBadge);
                progressBar = itemView.findViewById(R.id.progressBar);
            }
        }
    }
}