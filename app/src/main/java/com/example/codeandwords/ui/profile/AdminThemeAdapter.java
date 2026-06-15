package com.example.codeandwords.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.Theme;

import java.util.ArrayList;
import java.util.List;

// Адаптер списка тем в административном разделе.
public class AdminThemeAdapter extends RecyclerView.Adapter<AdminThemeAdapter.ThemeViewHolder> {

    public interface Listener {
        void onPreviewThemeWords(Theme theme);
        void onEditTheme(Theme theme);
        void onEditThemeTheory(Theme theme);
        void onDeleteTheme(Theme theme);
    }

    private final List<Theme> items = new ArrayList<>();
    private final Listener listener;

    public AdminThemeAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Theme> themes) {
        items.clear();

        if (themes != null) {
            items.addAll(themes);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ThemeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_theme, parent, false);
        return new ThemeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThemeViewHolder holder, int position) {
        Theme theme = items.get(position);

        String title = theme.getTitle() != null && !theme.getTitle().trim().isEmpty()
                ? theme.getTitle().trim()
                : "Без названия";

        String difficulty = theme.getDifficultyLevel() != null && !theme.getDifficultyLevel().trim().isEmpty()
                ? theme.getDifficultyLevel().trim()
                : "Easy";

        String description = theme.getDescription() != null && !theme.getDescription().trim().isEmpty()
                ? theme.getDescription().trim()
                : "Описание не указано";

        holder.tvTitle.setText(title);
        holder.tvDifficulty.setText(difficulty);
        holder.tvDescription.setText(description);

        holder.btnPreviewWords.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPreviewThemeWords(theme);
            }
        });

        holder.btnEditTheory.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditThemeTheory(theme);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditTheme(theme);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteTheme(theme);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ThemeViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDifficulty;
        TextView tvDescription;
        TextView btnPreviewWords;
        TextView btnEditTheory;
        TextView btnEdit;
        TextView btnDelete;

        ThemeViewHolder(@NonNull View itemView) {
            super(itemView);

            tvTitle = itemView.findViewById(R.id.tvAdminThemeTitle);
            tvDifficulty = itemView.findViewById(R.id.tvAdminThemeDifficulty);
            tvDescription = itemView.findViewById(R.id.tvAdminThemeDescription);
            btnPreviewWords = itemView.findViewById(R.id.btnPreviewThemeWords);
            btnEditTheory = itemView.findViewById(R.id.btnEditThemeTheory);
            btnEdit = itemView.findViewById(R.id.btnEditTheme);
            btnDelete = itemView.findViewById(R.id.btnDeleteTheme);
        }
    }
}