package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
import com.example.codeandwords.databinding.FragmentProfileBinding;
import com.example.codeandwords.model.AchievementWithProgress;
import com.example.codeandwords.model.User;
import com.example.codeandwords.ui.PersonalDictionaryActivity;
import com.example.codeandwords.ui.auth.LoginActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProfileFragment extends Fragment {

    private static final String ACHIEVEMENT_PREFS = "achievement_popup_prefs";
    private static final String KEY_SHOWN_IDS = "shown_ids";

    private FragmentProfileBinding binding;
    private Repository repository;
    private AchievementsAdapter adapter;

    public ProfileFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new Repository(requireContext());

        binding.rvAchievements.setLayoutManager(new GridLayoutManager(getContext(), 3));
        adapter = new AchievementsAdapter(requireContext());
        binding.rvAchievements.setAdapter(adapter);

        binding.btnLogout.setOnClickListener(v -> performLogout());

        binding.btnOpenPersonalDictionary.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PersonalDictionaryActivity.class);
            startActivity(intent);
        });

        loadUserData();
    }

    private void loadUserData() {
        repository.getCurrentUser(new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (binding == null) return;

                binding.tvUsername.setText(user.getUsername());
                binding.tvEmail.setText(user.getEmail());

                int totalXp = user.getTotalXp() != null ? user.getTotalXp() : 0;
                int currentLevel = (totalXp / 100) + 1;
                int xpInCurrentLevel = totalXp % 100;

                binding.tvLevelValue.setText(String.valueOf(currentLevel));
                binding.tvTotalXP.setText(String.valueOf(totalXp));

                binding.progressBarXP.setMax(100);
                binding.progressBarXP.setProgress(xpInCurrentLevel);
                binding.tvXpCount.setText(xpInCurrentLevel + " / 100 XP");

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
                showUnlockedAchievementPopupIfNeeded(data);
            }

            @Override
            public void onError(String error) {
                Log.e("ProfileFragment", "Ошибка загрузки достижений: " + error);
            }
        });
    }

    private void showUnlockedAchievementPopupIfNeeded(List<AchievementWithProgress> achievements) {
        if (!isAdded() || achievements == null || achievements.isEmpty()) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(ACHIEVEMENT_PREFS, Context.MODE_PRIVATE);
        Set<String> shownIds = new HashSet<>(prefs.getStringSet(KEY_SHOWN_IDS, new HashSet<>()));

        AchievementWithProgress newest = null;

        for (AchievementWithProgress item : achievements) {
            if (item == null || item.id == null) continue;

            String key = String.valueOf(item.id);

            if (item.isUnlocked && item.isNew && !shownIds.contains(key)) {
                if (newest == null || item.dateReceived > newest.dateReceived) {
                    newest = item;
                }
            }
        }

        if (newest != null) {
            String key = String.valueOf(newest.id);
            shownIds.add(key);
            prefs.edit().putStringSet(KEY_SHOWN_IDS, shownIds).apply();

            Intent intent = new Intent(requireContext(), AchievementUnlockedActivity.class);
            intent.putExtra("achievement_id", newest.id);
            intent.putExtra("title", newest.title);
            intent.putExtra("description", newest.description);
            intent.putExtra("xp_reward", newest.xpReward != null ? newest.xpReward : 0);
            intent.putExtra("icon_res_name", newest.iconResName);
            intent.putExtra("current_progress", newest.currentProgress);
            intent.putExtra("max_progress", newest.maxProgress != null ? newest.maxProgress : 0);
            startActivity(intent);
        }
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
        binding = null;
    }

    private class AchievementsAdapter extends RecyclerView.Adapter<AchievementsAdapter.ViewHolder> {

        private List<AchievementWithProgress> achievements = new ArrayList<>();
        private final Context context;

        public AchievementsAdapter(Context context) {
            this.context = context;
        }

        void setAchievements(List<AchievementWithProgress> achievements) {
            this.achievements = achievements != null ? achievements : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_achievement, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AchievementWithProgress item = achievements.get(position);

            String title = item.title != null ? item.title : "";
            holder.tvTitle.setText(title);

            String iconName = item.iconResName != null ? item.iconResName : "";
            int iconId = context.getResources().getIdentifier(
                    iconName,
                    "drawable",
                    context.getPackageName()
            );
            holder.ivIcon.setImageResource(iconId != 0 ? iconId : R.drawable.ic_launcher_foreground);

            int currentProgress = item.currentProgress;
            int maxProgress = item.maxProgress != null ? item.maxProgress : 0;
            if (maxProgress <= 0) {
                maxProgress = 1;
            }

            holder.progressBar.setMax(maxProgress);
            holder.progressBar.setProgress(Math.min(currentProgress, maxProgress));
            holder.tvProgress.setText(currentProgress + " / " + maxProgress);

            boolean isCompleted = item.isUnlocked || currentProgress >= maxProgress;

            if (isCompleted) {
                holder.cardRoot.setStrokeColor(android.graphics.Color.parseColor("#FFD54F"));
                holder.cardRoot.setStrokeWidth(5);
                holder.cardRoot.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF8E1"));
                holder.progressBar.setProgressTintList(
                        ColorStateList.valueOf(android.graphics.Color.parseColor("#43A047"))
                );
            } else {
                holder.cardRoot.setStrokeColor(android.graphics.Color.parseColor("#E3E7EA"));
                holder.cardRoot.setStrokeWidth(2);
                holder.cardRoot.setCardBackgroundColor(android.graphics.Color.WHITE);
                holder.progressBar.setProgressTintList(
                        ColorStateList.valueOf(android.graphics.Color.parseColor("#29B6F6"))
                );
            }

            holder.cardRoot.setOnClickListener(v -> openAchievementDetails(item));
        }

        @Override
        public int getItemCount() {
            return achievements.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvTitle;
            TextView tvProgress;
            ProgressBar progressBar;
            MaterialCardView cardRoot;

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