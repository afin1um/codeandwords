package com.example.codeandwords.ui.profile;

import android.os.Bundle;
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

public class TeamDetailActivity extends AppCompatActivity {

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
        // Не перезагружаем при каждом onResume — это вызывало повторные таймауты
        // loadChallenge() будет вызываться только при первом открытии и по кнопке "Повторить"
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBackTeamDetail);
        btnBack.setOnClickListener(v -> finish());

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
        if (loadingContainer != null) loadingContainer.setVisibility(View.VISIBLE);
        if (errorContainer != null) errorContainer.setVisibility(View.GONE);
    }

    private void showContent() {
        if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
        if (errorContainer != null) errorContainer.setVisibility(View.GONE);
    }

    private void showError(String message) {
        if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
        if (errorContainer != null) errorContainer.setVisibility(View.VISIBLE);
        if (tvErrorMessage != null) {
            tvErrorMessage.setText(message != null
                    ? "Не удалось загрузить задание команды.\n" + message
                    : "Не удалось загрузить задание команды");
        }
    }

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
                if (challenge == null) {
                    isLoading = false;
                    showError("Задание команды ещё не создано");
                    return;
                }

                tvChallengeTitle.setText(challenge.title != null
                        ? challenge.title
                        : "Задание команды");

                String unit = "LESSONS".equals(challenge.conditionType)
                        ? " уроков" : " XP";
                tvChallengeTarget.setText("Цель: " + challenge.targetValue + unit);

                progressTeam.setMax(Math.max(challenge.targetValue, 1));

                loadProgress(challenge);
            }

            @Override
            public void onError(String error) {
                isLoading = false;
                showError(error);
            }
        });
    }

    private void loadProgress(TeamChallenge challenge) {
        repository.getTeamProgress(challenge.id,
                new Repository.DataCallback<List<TeamChallengeProgress>>() {
                    @Override
                    public void onSuccess(List<TeamChallengeProgress> data) {
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

                        progressTeam.setProgress(
                                Math.min(maxProgress, challenge.targetValue));

                        adapter.setItems(data, challenge.targetValue);
                    }

                    @Override
                    public void onError(String error) {
                        isLoading = false;
                        showError(error);
                    }
                });
    }
}