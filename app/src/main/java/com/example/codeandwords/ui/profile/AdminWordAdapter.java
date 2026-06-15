package com.example.codeandwords.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.List;

// Адаптер списка терминов в административной панели.
public class AdminWordAdapter extends RecyclerView.Adapter<AdminWordAdapter.WordViewHolder> {

    public interface Listener {
        void onEditWord(Word word);
        void onDeleteWord(Word word);
    }

    private final List<Word> items = new ArrayList<>();
    private final Listener listener;

    public AdminWordAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Word> words) {
        items.clear();

        if (words != null) {
            items.addAll(words);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        Word word = items.get(position);

        holder.tvTerm.setText(word.getTerm() != null ? word.getTerm() : "");
        holder.tvTranscription.setText(word.getTranscription() != null ? word.getTranscription() : "");
        holder.tvTranslation.setText(word.getTranslation() != null ? word.getTranslation() : "");
        holder.tvDefinition.setText(word.getDefinition() != null ? word.getDefinition() : "");

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEditWord(word);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteWord(word);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView tvTerm;
        TextView tvTranscription;
        TextView tvTranslation;
        TextView tvDefinition;
        TextView btnEdit;
        TextView btnDelete;

        WordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTerm = itemView.findViewById(R.id.tvAdminTerm);
            tvTranscription = itemView.findViewById(R.id.tvAdminTranscription);
            tvTranslation = itemView.findViewById(R.id.tvAdminTranslation);
            tvDefinition = itemView.findViewById(R.id.tvAdminDefinition);
            btnEdit = itemView.findViewById(R.id.btnEditWord);
            btnDelete = itemView.findViewById(R.id.btnDeleteWord);
        }
    }
}