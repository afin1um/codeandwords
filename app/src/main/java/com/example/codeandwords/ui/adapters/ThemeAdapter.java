package com.example.codeandwords.ui.adapters;

import android.graphics.Color;
import android.graphics.PorterDuff;
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

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder> {

    private List<Theme> themes = new ArrayList<>();
    private final OnThemeClickListener listener;

    public interface OnThemeClickListener {
        void onThemeClick(Theme theme);
    }

    public ThemeAdapter(OnThemeClickListener listener) {
        this.listener = listener;
    }

    public void setThemes(List<Theme> themes) {
        this.themes = themes;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ThemeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_theme, parent, false);
        return new ThemeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThemeViewHolder holder, int position) {
        Theme theme = themes.get(position);
        holder.bind(theme, listener);
    }

    @Override
    public int getItemCount() {
        return themes.size();
    }

    static class ThemeViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvDesc;
        private final TextView tvDifficulty;

        public ThemeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvThemeTitle);
            tvDesc = itemView.findViewById(R.id.tvThemeDesc);
            tvDifficulty = itemView.findViewById(R.id.tvDifficulty);
        }

        public void bind(final Theme theme, final OnThemeClickListener listener) {
            tvTitle.setText(theme.getTitle());
            tvDesc.setText(theme.getDescription());

            // Устанавливаем текст сложности
            String difficulty = theme.getDifficultyLevel();
            tvDifficulty.setText(difficulty);

            // Логика изменения цвета метки сложности
            int color;
            if (difficulty == null) {
                color = Color.parseColor("#9E9E9E"); // Серый по умолчанию
            } else {
                switch (difficulty.toLowerCase()) {
                    case "easy":
                        color = Color.parseColor("#4CAF50"); // Зеленый
                        break;
                    case "medium":
                        color = Color.parseColor("#FFB300"); // Оранжевый/Желтый
                        break;
                    case "hard":
                        color = Color.parseColor("#F44336"); // Красный
                        break;
                    default:
                        color = Color.parseColor("#9E9E9E"); // Серый
                        break;
                }
            }

            // Применяем цвет к фону (background) нашего TextView
            // Используем PorterDuff.Mode.SRC_IN для корректного закрашивания скругленного фона
            if (tvDifficulty.getBackground() != null) {
                tvDifficulty.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }

            // Обработка клика
            itemView.setOnClickListener(v -> listener.onThemeClick(theme));
        }
    }
}