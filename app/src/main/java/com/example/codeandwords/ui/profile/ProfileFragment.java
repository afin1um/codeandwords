package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private Repository repository;
    private ProfileMedalsAdapter medalsAdapter;

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new Repository(requireContext());

        setupUi();
        loadUserData();
    }

    private void setupUi() {
        applyAvatarToHeader();

        binding.rvMedals.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        binding.rvMedals.setHasFixedSize(true);

        medalsAdapter = new ProfileMedalsAdapter(requireContext(), new ArrayList<>());
        binding.rvMedals.setAdapter(medalsAdapter);

        binding.btnEditAvatar.setOnClickListener(v -> openAvatarEditor());

        binding.btnOpenPersonalDictionary.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PersonalDictionaryActivity.class);
            startActivity(intent);
        });

        binding.btnOpenStudySchedule.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), StudyScheduleActivity.class);
            startActivity(intent);
        });

        binding.btnLogout.setOnClickListener(v -> performLogout());

        binding.btnOpenTeam.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), TeamActivity.class);
            startActivity(intent);
        });
    }

    private void applyAvatarToHeader() {
        if (binding == null || !isAdded()) return;

        AvatarConfig avatarConfig = AvatarPrefs.load(requireContext());
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
                if (binding == null) return;

                binding.tvUsername.setText(user.getUsername() != null ? user.getUsername() : "Пользователь");
                binding.tvHandle.setText("@" + (user.getUsername() != null ? user.getUsername().toLowerCase() : "user"));
                binding.tvEmail.setText(user.getEmail() != null ? user.getEmail() : "");

                int totalXp = user.getTotalXp() != null ? user.getTotalXp() : 0;
                int currentLevel = (totalXp / 100) + 1;
                int xpInCurrentLevel = totalXp % 100;

                binding.tvLevelValue.setText(String.valueOf(currentLevel));
                binding.tvTotalXP.setText(String.valueOf(totalXp));
                binding.tvXpCount.setText(xpInCurrentLevel + " / 100 XP");

                binding.progressBarXP.setMax(100);
                binding.progressBarXP.setProgress(xpInCurrentLevel);
                binding.progressBarXP.setProgressTintList(
                        ColorStateList.valueOf(getResources().getColor(R.color.profile_gold, null))
                );

                loadLearnedWordsCount(user.getId());
                loadAchievements();
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
                if (binding != null) {
                    binding.tvWordsLearnedCount.setText(String.valueOf(count));
                    binding.tvCoursesCount.setText(String.valueOf(count));
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
                medalsAdapter.setItems(achievements);

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
                    binding.tvMedalsEmpty.setVisibility(View.VISIBLE);
                    binding.rvMedals.setVisibility(View.GONE);
                }
            }
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

        applyAvatarToHeader();
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
}