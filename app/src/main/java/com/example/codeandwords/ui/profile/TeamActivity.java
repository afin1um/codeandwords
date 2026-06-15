package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Team;
import com.example.codeandwords.model.User;

import java.util.ArrayList;
import java.util.List;

// Экран управления командами: создание с выбором друзей и заданием, список существующих команд.
public class TeamActivity extends AppCompatActivity {

    private Repository repository;

    private EditText etTeamName;
    private Spinner spinnerChallengeType;
    private Spinner spinnerChallengeTarget;
    private Button btnCreateTeam;

    private RecyclerView recyclerSelectableFriends;
    private RecyclerView recyclerMyTeams;

    private TextView tvMyTeamsEmpty;

    private SelectableFriendAdapter selectableFriendAdapter;
    private TeamAdapter teamAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        setupSpinners();
        setupRecycler();
        setupClicks();
    }

    // При каждом возврате на экран обновляем команды и друзей,
    // чтобы свежедобавленные друзья сразу появились в списке выбора
    @Override
    protected void onResume() {
        super.onResume();
        loadMyTeams();
        loadFriends();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBackTeam);
        btnBack.setOnClickListener(v -> finish());

        etTeamName = findViewById(R.id.etTeamName);
        spinnerChallengeType = findViewById(R.id.spinnerChallengeType);
        spinnerChallengeTarget = findViewById(R.id.spinnerChallengeTarget);
        btnCreateTeam = findViewById(R.id.btnCreateTeam);

        recyclerSelectableFriends = findViewById(R.id.recyclerSelectableFriends);
        recyclerMyTeams = findViewById(R.id.recyclerMyTeams);

        tvMyTeamsEmpty = findViewById(R.id.tvMyTeamsEmpty);
    }

    private void setupSpinners() {
        List<String> types = new ArrayList<>();
        types.add("Заработать XP");
        types.add("Пройти уроки");

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                types
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerChallengeType.setAdapter(typeAdapter);

        List<String> targets = new ArrayList<>();
        targets.add("50");
        targets.add("100");
        targets.add("150");
        targets.add("200");
        targets.add("300");

        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                targets
        );
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerChallengeTarget.setAdapter(targetAdapter);
    }

    private void setupRecycler() {
        teamAdapter = new TeamAdapter(this::openTeamDetail);
        recyclerMyTeams.setLayoutManager(new LinearLayoutManager(this));
        recyclerMyTeams.setAdapter(teamAdapter);
        recyclerMyTeams.setNestedScrollingEnabled(false);

        selectableFriendAdapter = new SelectableFriendAdapter();
        recyclerSelectableFriends.setLayoutManager(new LinearLayoutManager(this));
        recyclerSelectableFriends.setAdapter(selectableFriendAdapter);
        recyclerSelectableFriends.setNestedScrollingEnabled(false);
    }

    private void setupClicks() {
        btnCreateTeam.setOnClickListener(v -> createTeam());
    }

    private void loadMyTeams() {
        repository.getMyTeams(new Repository.DataCallback<List<Team>>() {
            @Override
            public void onSuccess(List<Team> data) {
                teamAdapter.setItems(data);

                if (data == null || data.isEmpty()) {
                    tvMyTeamsEmpty.setVisibility(TextView.VISIBLE);
                    recyclerMyTeams.setVisibility(RecyclerView.GONE);
                } else {
                    tvMyTeamsEmpty.setVisibility(TextView.GONE);
                    recyclerMyTeams.setVisibility(RecyclerView.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                tvMyTeamsEmpty.setVisibility(TextView.VISIBLE);
                recyclerMyTeams.setVisibility(RecyclerView.GONE);
            }
        });
    }

    private void loadFriends() {
        repository.getFriends(new Repository.DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> data) {
                selectableFriendAdapter.setItems(data);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TeamActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Собирает данные формы и создаёт команду с заданием
    private void createTeam() {
        String teamName = etTeamName.getText().toString().trim();

        int typePosition = spinnerChallengeType.getSelectedItemPosition();
        String challengeType = typePosition == 1 ? "LESSONS" : "XP";

        int targetValue = Integer.parseInt(spinnerChallengeTarget.getSelectedItem().toString());

        List<User> selectedFriends = selectableFriendAdapter.getSelectedFriends();

        btnCreateTeam.setEnabled(false);

        repository.createTeamWithFriends(
                teamName,
                selectedFriends,
                challengeType,
                targetValue,
                new Repository.DataCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer teamId) {
                        btnCreateTeam.setEnabled(true);

                        Toast.makeText(TeamActivity.this, "Команда создана",
                                Toast.LENGTH_SHORT).show();

                        etTeamName.setText("");
                        selectableFriendAdapter.clearSelection();

                        loadMyTeams();

                        Intent intent = new Intent(TeamActivity.this, TeamDetailActivity.class);
                        intent.putExtra("team_id", teamId);
                        intent.putExtra("team_name", teamName);
                        startActivity(intent);
                    }

                    @Override
                    public void onError(String error) {
                        btnCreateTeam.setEnabled(true);
                        Toast.makeText(TeamActivity.this, error, Toast.LENGTH_LONG).show();
                        loadMyTeams();
                    }
                }
        );
    }

    private void openTeamDetail(Team team) {
        if (team == null || team.id <= 0) {
            Toast.makeText(this, "Команда недоступна", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, TeamDetailActivity.class);
        intent.putExtra("team_id", team.id);
        intent.putExtra("team_name", team.teamName);
        startActivity(intent);
    }
}