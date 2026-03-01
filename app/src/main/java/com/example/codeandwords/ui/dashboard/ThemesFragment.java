package com.example.codeandwords.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.ui.adapters.ThemeAdapter;
import com.example.codeandwords.ui.game.GameSelectionActivity;

import java.util.List;

public class ThemesFragment extends Fragment implements ThemeAdapter.OnThemeClickListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvError;
    private ThemeAdapter adapter;
    private Repository repository;

    public ThemesFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_themes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewThemes);
        progressBar = view.findViewById(R.id.progressBarThemes);
        tvError = view.findViewById(R.id.tvError);

        repository = new Repository(requireContext());

        initRecyclerView();
        loadThemes();
    }

    private void initRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ThemeAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    private void loadThemes() {
        showLoading(true);
        repository.getThemes(new Repository.DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> data) {
                showLoading(false);
                adapter.setThemes(data);
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                tvError.setVisibility(View.VISIBLE);
                tvError.setText(error);
            }
        });
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            tvError.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onThemeClick(Theme theme) {
        Intent intent = new Intent(getContext(), GameSelectionActivity.class);
        intent.putExtra("THEME_ID", theme.getId());
        intent.putExtra("THEME_TITLE", theme.getTitle());
        startActivity(intent);
    }
}