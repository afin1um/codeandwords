package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.databinding.FragmentProfileBinding; // Убедитесь, что ViewBinding включен в build.gradle
import com.example.codeandwords.model.AchievementWithProgress;
import com.example.codeandwords.model.User;
import com.example.codeandwords.ui.auth.LoginActivity;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding; // Основной способ работы с View
    private Repository repository;
    private AchievementsAdapter adapter;

    public ProfileFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Инициализируем binding правильно
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new Repository(requireContext());

        // Настройка RecyclerView
        binding.rvAchievements.setLayoutManager(new GridLayoutManager(getContext(), 3));
        adapter = new AchievementsAdapter(requireContext());
        binding.rvAchievements.setAdapter(adapter);

        // Кнопка выхода
        binding.btnLogout.setOnClickListener(v -> performLogout());

        loadUserData();
    }

    private void loadUserData() {
        repository.getCurrentUser(new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (binding == null) return;

                // Используем binding вместо findViewById
                binding.tvUsername.setText(user.getUsername());
                binding.tvEmail.setText(user.getEmail());

                int totalXp = user.getTotalXp();
                int currentLevel = (totalXp / 100) + 1;
                int xpInCurrentLevel = totalXp % 100;

                binding.tvLevelValue.setText(String.valueOf(currentLevel));
                binding.tvTotalXP.setText(String.valueOf(totalXp));

                binding.progressBarXP.setMax(100);
                binding.progressBarXP.setProgress(xpInCurrentLevel);
                binding.tvXpCount.setText(xpInCurrentLevel + " / 100 XP");

                // Загружаем статистику слов по Integer ID
                loadLearnedWordsCount(user.getId());
                loadAchievements();
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Ошибка загрузки профиля", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadLearnedWordsCount(Integer userId) {
        repository.getLearnedWordsCount(userId, new Repository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                // ПРОВЕРКА: Используем binding напрямую через внешний класс
                if (isAdded() && binding != null) {
                    binding.tvWordsLearnedCount.setText(String.valueOf(count));
                }
            }

            @Override
            public void onError(String error) {
                Log.e("ProfileFragment", "Ошибка загрузки слов: " + error);
            }
        });
    }

    private void loadAchievements() {
        repository.getAchievements(new Repository.DataCallback<List<AchievementWithProgress>>() {
            @Override
            public void onSuccess(List<AchievementWithProgress> data) {
                if (adapter != null) {
                    adapter.setAchievements(data);
                }
            }
            @Override
            public void onError(String error) {}
        });
    }

    private void performLogout() {
        repository.logout(() -> {
            if (isAdded()) {
                Intent intent = new Intent(requireActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Обязательно для фрагментов, чтобы избежать утечек памяти
    }

    // --- Адаптер достижений ---
    private class AchievementsAdapter extends RecyclerView.Adapter<AchievementsAdapter.ViewHolder> {
        private List<AchievementWithProgress> achievements = new ArrayList<>();
        private final Context context;

        public AchievementsAdapter(Context context) { this.context = context; }

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

            int iconId = context.getResources().getIdentifier(item.achievement.getIconResName(), "drawable", context.getPackageName());
            holder.ivIcon.setImageResource(iconId != 0 ? iconId : R.drawable.ic_launcher_foreground);

            int currentProgress = (item.currentProgress == null) ? 0 : item.currentProgress;
            int maxProgress = item.achievement.getMaxProgress();
            holder.progressBar.setMax(maxProgress);
            holder.progressBar.setProgress(currentProgress);
            holder.tvProgress.setText(currentProgress + " / " + maxProgress);

            if (currentProgress >= maxProgress) {
                holder.cardRoot.setStrokeColor(android.graphics.Color.parseColor("#FFD700"));
                holder.cardRoot.setStrokeWidth(6);
                holder.cardRoot.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFDE7"));
                holder.progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
            } else {
                holder.cardRoot.setStrokeColor(android.graphics.Color.TRANSPARENT);
                holder.cardRoot.setStrokeWidth(0);
                holder.cardRoot.setCardBackgroundColor(android.graphics.Color.WHITE);
                holder.progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFC107")));
            }
        }

        @Override
        public int getItemCount() { return achievements.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvTitle, tvProgress;
            ProgressBar progressBar;
            com.google.android.material.card.MaterialCardView cardRoot;

            ViewHolder(View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvProgress = itemView.findViewById(R.id.tvProgress);
                progressBar = itemView.findViewById(R.id.progressBar);
                cardRoot = itemView.findViewById(R.id.cardRoot);
            }
        }
    }
}