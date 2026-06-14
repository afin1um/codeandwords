package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.databinding.FragmentProfileBinding;
import com.example.codeandwords.model.AchievementWithProgress;
import com.example.codeandwords.model.User;
import com.example.codeandwords.ui.PersonalDictionaryActivity;
import com.example.codeandwords.ui.auth.LoginActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private Repository repository;
    private ProfileMedalsAdapter achievementsAdapter;

    private boolean avatarEditorAutoOpened = false;

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
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = Repository.getInstance(requireContext());

        setupUi();
        loadUserData();
    }

    private void setupUi() {
        applyAvatarToHeader();

        binding.rvMedals.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        binding.rvMedals.setHasFixedSize(true);

        achievementsAdapter = new ProfileMedalsAdapter(requireContext(), new ArrayList<>());
        binding.rvMedals.setAdapter(achievementsAdapter);

        binding.btnEditAvatar.setOnClickListener(v -> openAvatarEditor());
        binding.btnEditProfile.setOnClickListener(v -> openEditProfile());

        updateThemeIcon();
        binding.btnToggleTheme.setOnClickListener(v -> toggleTheme());

        binding.btnOpenPersonalDictionary.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PersonalDictionaryActivity.class);
            startActivity(intent);
        });

        binding.btnOpenStudySchedule.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), StudyScheduleActivity.class);
            startActivity(intent);
        });

        binding.btnOpenTeam.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), TeamActivity.class);
            startActivity(intent);
        });

        // ✅ НОВАЯ КНОПКА — ПОИСК ДРУЗЕЙ
        binding.btnFindFriends.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), UserSearchActivity.class);
            startActivity(intent);
        });

        binding.btnOpenAdminPanel.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AdminPanelActivity.class);
            startActivity(intent);
        });

        binding.layoutAchievementsHeader.setOnClickListener(v -> openAchievementsScreen());

        binding.btnLogout.setOnClickListener(v -> performLogout());

        binding.btnOpenStatistics.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), UserStatisticsActivity.class);
            startActivity(intent);
        });
    }

    private void updateThemeIcon() {
        if (binding == null) return;

        if (isCurrentlyDark()) {
            binding.btnToggleTheme.setImageResource(R.drawable.ic_theme_sun);
            binding.btnToggleTheme.setContentDescription("Включить светлую тему");
        } else {
            binding.btnToggleTheme.setImageResource(R.drawable.ic_theme_moon);
            binding.btnToggleTheme.setContentDescription("Включить тёмную тему");
        }
    }

    private boolean isCurrentlyDark() {
        int currentNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void toggleTheme() {
        int newMode = isCurrentlyDark()
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES;

        ThemePrefs.saveThemeMode(requireContext(), newMode);

        if (isAdded()) {
            requireActivity().recreate();
        }
    }

    private void applyAvatarToHeader() {
        if (binding == null || !isAdded()) return;

        AvatarConfig avatarConfig = AvatarPrefs.load(requireContext());

        if (avatarConfig == null) {
            avatarConfig = new AvatarConfig();
        }

        int bgColor = avatarConfig.backgroundColor;

        binding.avatarPreview.setAvatarConfig(avatarConfig);

        binding.profileHeaderContainer.setBackgroundColor(bgColor);
        binding.profileHeaderContent.setBackgroundColor(bgColor);
        binding.avatarPreviewHolder.setBackgroundColor(bgColor);

        requireActivity().getWindow().setStatusBarColor(bgColor);
    }

    private void openAvatarEditor() {
        try {
            Intent intent = new Intent(requireContext(), AvatarEditorActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("ProfileFragment", "Ошибка открытия AvatarEditorActivity: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Не удалось открыть редактор аватара", Toast.LENGTH_SHORT).show();
        }
    }

    private void openEditProfile() {
        try {
            Intent intent = new Intent(requireContext(), EditProfileActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("ProfileFragment", "Ошибка открытия EditProfileActivity: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Не удалось открыть редактирование профиля", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAchievementsScreen() {
        try {
            Intent intent = new Intent(requireContext(), AchievementsActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("ProfileFragment", "Ошибка открытия AchievementsActivity: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Не удалось открыть достижения", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAvatarEditorIfNeeded() {
        if (!isAdded() || binding == null) return;
        if (avatarEditorAutoOpened) return;

        if (AvatarPrefs.needsAvatarSetup(requireContext())) {
            avatarEditorAutoOpened = true;
            Toast.makeText(requireContext(), "Создайте своего аватара", Toast.LENGTH_SHORT).show();
            openAvatarEditor();
        }
    }

    private void loadUserData() {
        repository.getCurrentUser(new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (binding == null || user == null) return;

                String username = user.getUsername() != null && !user.getUsername().trim().isEmpty()
                        ? user.getUsername().trim()
                        : "Пользователь";

                String email = user.getEmail() != null ? user.getEmail() : "";

                binding.tvUsername.setText(username);
                binding.tvEmail.setText(email);
                binding.tvHandle.setText("@" + username.toLowerCase(Locale.ROOT));

                int totalXp = user.getTotalXp() != null ? user.getTotalXp() : 0;
                int currentLevel = (totalXp / 100) + 1;
                int xpInCurrentLevel = totalXp % 100;

                binding.tvTotalXP.setText(String.valueOf(totalXp));
                binding.tvLevelValue.setText(String.valueOf(currentLevel));
                binding.tvXpCount.setText(xpInCurrentLevel + " / 100 XP");
                binding.progressBarXP.setMax(100);
                binding.progressBarXP.setProgress(xpInCurrentLevel);
                binding.progressBarXP.setProgressTintList(
                        ColorStateList.valueOf(Color.parseColor("#FFD43B"))
                );

                LeagueInfo leagueInfo = getLeagueInfo(totalXp);
                binding.tvLeagueValue.setText(leagueInfo.icon + " " + leagueInfo.title);
                binding.tvLeagueValue.setTextColor(leagueInfo.color);
                binding.tvLeagueDescription.setText(leagueInfo.subtitle);

                loadLearnedWordsCount(user.getId());
                loadMasteredThemesCount();
                loadAchievements();

                if ("admin".equalsIgnoreCase(user.getRole())) {
                    binding.btnOpenAdminPanel.setVisibility(View.VISIBLE);
                } else {
                    binding.btnOpenAdminPanel.setVisibility(View.GONE);
                }

                openAvatarEditorIfNeeded();
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
                if (binding == null) return;
                int safeCount = count != null ? count : 0;
                binding.tvWordsLearnedCount.setText(String.valueOf(safeCount));
            }

            @Override
            public void onError(String error) {
                Log.e("ProfileFragment", "Ошибка загрузки выученных слов: " + error);
                if (binding != null) {
                    binding.tvWordsLearnedCount.setText("0");
                }
            }
        });
    }

    private void loadMasteredThemesCount() {
        repository.getMasteredThemesCount(new Repository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                if (binding == null) return;
                int safeCount = count != null ? count : 0;
                binding.tvCoursesCount.setText(String.valueOf(safeCount));
                binding.tvMasteredThemesValue.setText(String.valueOf(safeCount));
            }

            @Override
            public void onError(String error) {
                Log.e("ProfileFragment", "Ошибка загрузки освоенных тем: " + error);
                if (binding != null) {
                    binding.tvCoursesCount.setText("0");
                    binding.tvMasteredThemesValue.setText("0");
                }
            }
        });
    }

    private void loadAchievements() {
        repository.getAchievements(new Repository.DataCallback<List<AchievementWithProgress>>() {
            @Override
            public void onSuccess(List<AchievementWithProgress> data) {
                if (binding == null) return;

                List<AchievementWithProgress> achievements = new ArrayList<>();
                int unlockedCount = 0;

                if (data != null) {
                    achievements.addAll(data);

                    for (AchievementWithProgress item : achievements) {
                        if (item != null && item.isUnlocked) {
                            unlockedCount++;
                        }
                    }

                    Collections.sort(achievements, new Comparator<AchievementWithProgress>() {
                        @Override
                        public int compare(AchievementWithProgress a1, AchievementWithProgress a2) {
                            if (a1 == null && a2 == null) return 0;
                            if (a1 == null) return 1;
                            if (a2 == null) return -1;

                            if (a1.isUnlocked && !a2.isUnlocked) return -1;
                            if (!a1.isUnlocked && a2.isUnlocked) return 1;

                            if (a1.id == null && a2.id == null) return 0;
                            if (a1.id == null) return 1;
                            if (a2.id == null) return -1;

                            return Long.compare(a1.id, a2.id);
                        }
                    });
                }

                binding.tvMedalsCount.setText(String.valueOf(unlockedCount));
                achievementsAdapter.setItems(achievements);

                if (achievements.isEmpty()) {
                    binding.tvMedalsEmpty.setVisibility(View.VISIBLE);
                    binding.rvMedals.setVisibility(View.GONE);
                } else {
                    binding.tvMedalsEmpty.setVisibility(View.GONE);
                    binding.rvMedals.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                Log.e("ProfileFragment", "Ошибка загрузки достижений: " + error);
                if (binding != null) {
                    binding.tvMedalsCount.setText("0");
                    binding.tvMedalsEmpty.setVisibility(View.VISIBLE);
                    binding.rvMedals.setVisibility(View.GONE);
                }
            }
        });
    }

    private LeagueInfo getLeagueInfo(int xp) {
        if (xp >= 2500) {
            return new LeagueInfo("Алмазная лига", "Элита Code & Words", "💎",
                    Color.parseColor("#61D9FF"));
        }
        if (xp >= 1200) {
            return new LeagueInfo("Изумрудная лига", "Сильные и стабильные", "💚",
                    Color.parseColor("#58CC02"));
        }
        if (xp >= 600) {
            return new LeagueInfo("Золотая лига", "Путь к сильным игрокам", "🥇",
                    Color.parseColor("#FFC107"));
        }
        if (xp >= 250) {
            return new LeagueInfo("Серебряная лига", "Стабильный прогресс", "🥈",
                    Color.parseColor("#B0BEC5"));
        }
        return new LeagueInfo("Бронзовая лига", "Начало пути", "🥉",
                Color.parseColor("#CD7F32"));
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

        applyAvatarToHeader();
        updateThemeIcon();
        loadUserData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (isAdded()) {
            requireActivity().getWindow().setStatusBarColor(
                    getResources().getColor(R.color.avatar_editor_screen_bg, null)
            );
        }

        binding = null;
    }

    private static class LeagueInfo {
        private final String title;
        private final String subtitle;
        private final String icon;
        private final int color;

        private LeagueInfo(String title, String subtitle, String icon, int color) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.color = color;
        }
    }
}