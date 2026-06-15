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

// Фрагмент списка тем: cache-first отображение без мигания при обновлении данных
public class ThemesFragment extends Fragment implements ThemeAdapter.OnThemeClickListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvError;
    private ThemeAdapter adapter;
    private Repository repository;

    // Флаг предотвращает показ ошибки, если данные уже отображены
    private boolean hasShownData = false;

    public ThemesFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_themes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewThemes);
        progressBar = view.findViewById(R.id.progressBarThemes);
        tvError = view.findViewById(R.id.tvError);

        repository = Repository.getInstance(requireContext());

        initRecyclerView();
        loadThemes();
    }

    private void initRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ThemeAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    private void loadThemes() {
        showInitialLoading();

        repository.getThemes(new Repository.DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> data) {
                if (!isAdded()) return;

                if (data != null && !data.isEmpty()) {
                    showData(data);
                } else if (!hasShownData) {
                    showError("Темы не найдены");
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;

                if (!hasShownData) {
                    showError(error);
                }
            }
        });
    }

    // Показывает загрузчик только если данные ещё не отображались
    private void showInitialLoading() {
        if (hasShownData) return;
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showData(List<Theme> themes) {
        hasShownData = true;
        progressBar.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        adapter.setThemes(themes);
    }

    private void showError(String error) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(error);
    }

    @Override
    public void onThemeClick(Theme theme) {
        Intent intent = new Intent(getContext(), GameSelectionActivity.class);
        intent.putExtra("THEME_ID", theme.getId());
        intent.putExtra("THEME_TITLE", theme.getTitle());
        startActivity(intent);
    }
}