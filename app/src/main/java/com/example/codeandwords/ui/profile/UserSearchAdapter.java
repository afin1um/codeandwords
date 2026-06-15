package com.example.codeandwords.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Адаптер результатов поиска пользователей.
public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.UserViewHolder> {

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    private final List<User> items = new ArrayList<>();
    private final OnUserClickListener listener;

    public UserSearchAdapter(List<User> users, OnUserClickListener listener) {
        this.listener = listener;

        if (users != null) {
            items.addAll(users);
        }
    }

    public void setItems(List<User> users) {
        items.clear();

        if (users != null) {
            items.addAll(users);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_search, parent, false);

        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = items.get(position);

        String username = user.getUsername() != null && !user.getUsername().trim().isEmpty()
                ? user.getUsername().trim()
                : "Пользователь";

        String email = user.getEmail() != null ? user.getEmail() : "";
        int xp = user.getTotalXp() != null ? user.getTotalXp() : 0;
        int level = user.getCurrentLevel() != null ? user.getCurrentLevel() : 1;

        holder.tvAvatarLetter.setText(username.substring(0, 1).toUpperCase(Locale.getDefault()));
        holder.tvUsername.setText(username);
        holder.tvEmail.setText(email);
        holder.tvInfo.setText("Уровень " + level + " • " + xp + " XP");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {

        TextView tvAvatarLetter;
        TextView tvUsername;
        TextView tvEmail;
        TextView tvInfo;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);

            tvAvatarLetter = itemView.findViewById(R.id.tvSearchAvatarLetter);
            tvUsername = itemView.findViewById(R.id.tvSearchUsername);
            tvEmail = itemView.findViewById(R.id.tvSearchEmail);
            tvInfo = itemView.findViewById(R.id.tvSearchInfo);
        }
    }
}