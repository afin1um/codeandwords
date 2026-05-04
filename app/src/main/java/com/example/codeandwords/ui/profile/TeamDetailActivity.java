package com.example.codeandwords.ui.profile;

import android.os.Bundle;
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

import java.util.List;

public class TeamDetailActivity extends AppCompatActivity {

    private Repository repository;

    private TextView tvTeamName;
    private TextView tvChallengeTitle;
    private TextView tvChallengeTarget;
    private ProgressBar progressTeam;
    private RecyclerView recyclerProgress;

    private TeamProgressAdapter adapter;

    private int teamId;
    private String teamName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_detail);

        repository = new Repository(this);

        teamId = getIntent().getIntExtra("team_id", -1);
        teamName = getIntent().getStringExtra("team_name");

        initViews();
        setupRecycler();
        loadChallenge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadChallenge();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBackTeamDetail);
        btnBack.setOnClickListener(v -> finish());

        tvTeamName = findViewById(R.id.tvTeamDetailName);
        tvChallengeTitle = findViewById(R.id.tvTeamChallengeTitle);
        tvChallengeTarget = findViewById(R.id.tvTeamChallengeTarget);
        progressTeam = findViewById(R.id.progressTeamChallenge);
        recyclerProgress = findViewById(R.id.recyclerTeamProgress);

        tvTeamName.setText(teamName != null && !teamName.trim().isEmpty() ? teamName : "Команда");
    }

    private void setupRecycler() {
        adapter = new TeamProgressAdapter();

        recyclerProgress.setLayoutManager(new LinearLayoutManager(this));
        recyclerProgress.setAdapter(adapter);
        recyclerProgress.setNestedScrollingEnabled(false);
    }

    private void loadChallenge() {
        if (teamId <= 0) {
            Toast.makeText(this, "Команда не найдена", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        repository.getTeamChallenge(teamId, new Repository.DataCallback<TeamChallenge>() {
            @Override
            public void onSuccess(TeamChallenge challenge) {
                if (challenge == null) {
                    Toast.makeText(TeamDetailActivity.this, "Задание команды не найдено", Toast.LENGTH_SHORT).show();
                    return;
                }

                tvChallengeTitle.setText(challenge.title != null ? challenge.title : "Задание команды");

                String unit = "LESSONS".equals(challenge.conditionType) ? " уроков" : " XP";
                tvChallengeTarget.setText("Цель: " + challenge.targetValue + unit);

                progressTeam.setMax(challenge.targetValue);
                loadProgress(challenge);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TeamDetailActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadProgress(TeamChallenge challenge) {
        repository.getTeamProgress(challenge.id, new Repository.DataCallback<List<TeamChallengeProgress>>() {
            @Override
            public void onSuccess(List<TeamChallengeProgress> data) {
                int maxProgress = 0;

                if (data != null) {
                    for (TeamChallengeProgress p : data) {
                        if (p != null && p.progress > maxProgress) {
                            maxProgress = p.progress;
                        }
                    }
                }

                progressTeam.setProgress(Math.min(maxProgress, challenge.targetValue));

                // ВАЖНО: передаем targetValue, чтобы было 50/50, а не 50/100
                adapter.setItems(data, challenge.targetValue);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TeamDetailActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}