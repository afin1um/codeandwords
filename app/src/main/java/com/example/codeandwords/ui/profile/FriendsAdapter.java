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

// Адаптер списка друзей с отображением инициала, имени и XP.
public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {

    public interface OnFriendClickListener {
        void onFriendClick(User user);
    }

    private final List<User> items = new ArrayList<>();
    private final OnFriendClickListener listener;

    public FriendsAdapter(List<User> users, OnFriendClickListener listener) {
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
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);

        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        User user = items.get(position);

        String username = user.getUsername() != null && !user.getUsername().trim().isEmpty()
                ? user.getUsername().trim()
                : "Пользователь";

        int xp = user.getTotalXp() != null ? user.getTotalXp() : 0;

        holder.tvAvatarLetter.setText(username.substring(0, 1).toUpperCase(Locale.getDefault()));
        holder.tvUsername.setText(username);
        holder.tvXp.setText(xp + " XP");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFriendClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FriendViewHolder extends RecyclerView.ViewHolder {

        TextView tvAvatarLetter;
        TextView tvUsername;
        TextView tvXp;

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);

            tvAvatarLetter = itemView.findViewById(R.id.tvAvatarLetter);
            tvUsername = itemView.findViewById(R.id.tvFriendUsername);
            tvXp = itemView.findViewById(R.id.tvFriendXp);
        }
    }
}