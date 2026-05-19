package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.User;

import java.util.ArrayList;
import java.util.Collections;

public class UserSearchActivity extends AppCompatActivity {

    private EditText etSearch;
    private ImageButton btnBack;
    private ImageButton btnClearSearch;
    private RecyclerView rvUsers;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private TextView tvHint;

    private Repository repository;
    private UserSearchAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_search);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        setupRecycler();
        setupListeners();
    }

    private void initViews() {
        etSearch = findViewById(R.id.etUserSearch);
        btnBack = findViewById(R.id.btnBackUserSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        rvUsers = findViewById(R.id.rvUserSearchResults);
        progressBar = findViewById(R.id.progressUserSearch);
        tvEmptyState = findViewById(R.id.tvUserSearchEmpty);
        tvHint = findViewById(R.id.tvUserSearchHint);

        progressBar.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.GONE);
        tvHint.setVisibility(View.VISIBLE);
        btnClearSearch.setVisibility(View.GONE);
    }

    private void setupRecycler() {
        adapter = new UserSearchAdapter(new ArrayList<>(), this::openUserProfile);
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            adapter.setItems(new ArrayList<>());
            tvHint.setVisibility(View.VISIBLE);
            tvEmptyState.setVisibility(View.GONE);
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void performSearch() {
        String query = etSearch.getText().toString().trim();

        if (query.isEmpty()) {
            Toast.makeText(this, "Введите ник пользователя", Toast.LENGTH_SHORT).show();
            return;
        }

        // Скрываем клавиатуру
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }

        progressBar.setVisibility(View.VISIBLE);
        tvHint.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.GONE);
        adapter.setItems(new ArrayList<>());

        repository.findUserByUsername(query, new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                progressBar.setVisibility(View.GONE);

                if (user == null) {
                    showEmpty("Пользователь не найден");
                    return;
                }

                adapter.setItems(Collections.singletonList(user));
                tvEmptyState.setVisibility(View.GONE);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                showEmpty(error != null ? error : "Пользователь не найден");
            }
        });
    }

    private void showEmpty(String message) {
        adapter.setItems(new ArrayList<>());
        tvEmptyState.setText(message);
        tvEmptyState.setVisibility(View.VISIBLE);
        tvHint.setVisibility(View.GONE);
    }

    private void openUserProfile(User user) {
        if (user == null || user.getId() == null) {
            Toast.makeText(this, "Не удалось открыть профиль", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, FriendProfileActivity.class);
        intent.putExtra("user_id", user.getId());
        intent.putExtra("username", user.getUsername());
        intent.putExtra("email", user.getEmail());
        intent.putExtra("avatar_config", user.getAvatarConfig());
        intent.putExtra("total_xp", user.getTotalXp() != null ? user.getTotalXp() : 0);
        intent.putExtra("current_level", user.getCurrentLevel() != null ? user.getCurrentLevel() : 1);
        intent.putExtra("show_add_friend_button", true);
        startActivity(intent);
    }
}