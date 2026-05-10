package com.example.codeandwords.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectableFriendAdapter extends RecyclerView.Adapter<SelectableFriendAdapter.VH> {

    private final List<User> items = new ArrayList<>();
    private final Set<Integer> selectedUserIds = new HashSet<>();

    public void setItems(List<User> users) {
        items.clear();
        selectedUserIds.clear();

        if (users != null) {
            items.addAll(users);
        }

        notifyDataSetChanged();
    }

    public List<User> getSelectedFriends() {
        List<User> result = new ArrayList<>();

        for (User user : items) {
            if (user != null && user.getId() != null && selectedUserIds.contains(user.getId())) {
                result.add(user);
            }
        }

        return result;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selectable_friend, parent, false);

        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        User user = items.get(position);

        if (user == null) {
            return;
        }

        Integer userId = user.getId();

        String username = user.getUsername() != null && !user.getUsername().trim().isEmpty()
                ? user.getUsername().trim()
                : "Пользователь";

        int xp = user.getTotalXp() != null ? user.getTotalXp() : 0;

        holder.tvUsername.setText(username);
        holder.tvXp.setText(xp + " XP");

        boolean isSelected = userId != null && selectedUserIds.contains(userId);

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(isSelected);

        holder.itemView.setSelected(isSelected);

        View.OnClickListener clickListener = v -> {
            if (userId == null) {
                Toast.makeText(v.getContext(), "Пользователь недоступен", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean currentlySelected = selectedUserIds.contains(userId);

            if (currentlySelected) {
                selectedUserIds.remove(userId);
            } else {
                if (selectedUserIds.size() >= 3) {
                    Toast.makeText(v.getContext(), "Можно выбрать максимум 3 друзей", Toast.LENGTH_SHORT).show();
                    return;
                }

                selectedUserIds.add(userId);
            }

            notifyItemChanged(holder.getAdapterPosition());
        };

        holder.itemView.setOnClickListener(clickListener);
        holder.checkBox.setOnClickListener(clickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void clearSelection() {
        selectedUserIds.clear();
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvUsername;
        TextView tvXp;
        CheckBox checkBox;

        VH(@NonNull View itemView) {
            super(itemView);

            tvUsername = itemView.findViewById(R.id.tvSelectableFriendUsername);
            tvXp = itemView.findViewById(R.id.tvSelectableFriendXp);
            checkBox = itemView.findViewById(R.id.cbSelectableFriend);
        }
    }
}