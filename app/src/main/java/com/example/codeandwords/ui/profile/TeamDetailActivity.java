package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.TeamChallenge;
import com.example.codeandwords.model.TeamChallengeProgress;
import com.google.android.material.button.MaterialButton;

import java.util.List;

// Экран деталей команды: задание, прогресс участников с автообновлением каждые 5 секунд.
public class TeamDetailActivity extends AppCompatActivity {

    private static final String TAG = "TeamDetailActivity";

    private static final long AUTO_REFRESH_INTERVAL_MS = 5000L;

    private Repository repository;

    private TextView tvTeamName;
    private TextView tvChallengeTitle;
    private TextView tvChallengeTarget;
    private ProgressBar progressTeam;
    private RecyclerView recyclerProgress;

    private View loadingContainer;
    private View errorContainer;
    private MaterialButton btnRetry;
    private TextView tvErrorMessage;

    private TeamProgressAdapter adapter;

    private int teamId;
    private String teamName;

    private boolean isLoading = false;
    private TeamChallenge currentChallenge;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed() && currentChallenge != null) {
                requestProgress(currentChallenge, true);
                refreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_detail);

        repository = Repository.getInstance(getApplicationContext());

        teamId = getIntent().getIntExtra("team_id", -1);
        teamName = getIntent().getStringExtra("team_name");

        initViews();
        setupRecycler();
        loadChallenge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentChallenge != null) {
            requestProgress(currentChallenge, true);
            startAutoRefresh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    @Override
    protected void onDestroy() {
        stopAutoRefresh();
        super.onDestroy();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBackTeamDetail);
        btnBack.setOnClickListener(v -> goToTeamActivity());

        tvTeamName = findViewById(R.id.tvTeamDetailName);
        tvChallengeTitle = findViewById(R.id.tvTeamChallengeTitle);
        tvChallengeTarget = findViewById(R.id.tvTeamChallengeTarget);
        progressTeam = findViewById(R.id.progressTeamChallenge);
        recyclerProgress = findViewById(R.id.recyclerTeamProgress);

        loadingContainer = findViewById(R.id.loadingContainer);
        errorContainer = findViewById(R.id.errorContainer);
        btnRetry = findViewById(R.id.btnRetry);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        tvTeamName.setText(teamName != null && !teamName.trim().isEmpty() ? teamName : "Команда");

        btnRetry.setOnClickListener(v -> loadChallenge());
    }

    private void setupRecycler() {
        adapter = new TeamProgressAdapter();
        recyclerProgress.setLayoutManager(new LinearLayoutManager(this));
        recyclerProgress.setAdapter(adapter);
        recyclerProgress.setNestedScrollingEnabled(false);
    }

    private void showLoading() {
        loadingContainer.setVisibility(View.VISIBLE);
        errorContainer.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loadingContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);
        tvErrorMessage.setText(message != null ? "Ошибка: " + message : "Не удалось загрузить данные");
    }

    // Загружает задание команды; при успехе запрашивает прогресс и запускает автообновление
    private void loadChallenge() {
        if (teamId <= 0) {
            Toast.makeText(this, "Команда не найдена", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (isLoading) return;
        isLoading = true;
        showLoading();

        repository.getTeamChallenge(teamId, new Repository.DataCallback<TeamChallenge>() {
            @Override
            public void onSuccess(TeamChallenge challenge) {
                if (isFinishing() || isDestroyed()) return;

                if (challenge == null) {
                    isLoading = false;
                    showError("Задание команды ещё не создано");
                    return;
                }

                currentChallenge = challenge;
                tvChallengeTitle.setText(challenge.title != null ? challenge.title : "Задание команды");

                String unit = "LESSONS".equals(challenge.conditionType) ? " уроков" : " XP";
                tvChallengeTarget.setText("Цель: " + challenge.targetValue + unit);
                progressTeam.setMax(Math.max(challenge.targetValue, 1));

                requestProgress(challenge, false);
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                isLoading = false;
                showError(error);
            }
        });
    }

    // Запрашивает прогресс: isSilent = true не показывает лоадер и ошибки в UI
    private void requestProgress(TeamChallenge challenge, boolean isSilent) {
        repository.getTeamProgress(challenge.id,
                new Repository.DataCallback<List<TeamChallengeProgress>>() {
                    @Override
                    public void onSuccess(List<TeamChallengeProgress> data) {
                        if (isFinishing() || isDestroyed()) return;

                        isLoading = false;
                        showContent();

                        int maxProgress = 0;
                        if (data != null) {
                            for (TeamChallengeProgress p : data) {
                                if (p != null && p.progress > maxProgress) {
                                    maxProgress = p.progress;
                                }
                            }
                        }

                        progressTeam.setProgress(Math.min(maxProgress,
                                Math.max(challenge.targetValue, 1)));
                        adapter.setItems(data, challenge.targetValue);

                        if (!isSilent) {
                            startAutoRefresh();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (isFinishing() || isDestroyed()) return;

                        if (!isSilent) {
                            isLoading = false;
                            showError(error);
                        } else {
                            Log.w(TAG, "Ошибка тихого обновления: " + error);
                        }
                    }
                });
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        if (currentChallenge != null) {
            refreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS);
        }
    }

    private void stopAutoRefresh() {
        refreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    @Override
    public void onBackPressed() {
        goToTeamActivity();
    }

    // Возврат в TeamActivity с сохранением стека
    private void goToTeamActivity() {
        Intent intent = new Intent(this, TeamActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}