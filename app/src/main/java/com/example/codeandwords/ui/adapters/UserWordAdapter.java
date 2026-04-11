package com.example.codeandwords.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.UserWord;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UserWordAdapter extends RecyclerView.Adapter<UserWordAdapter.WordViewHolder> {

    private List<UserWord> wordList = new ArrayList<>();
    private final Repository repository;
    private final OnWordDeletedListener deleteListener;
    private boolean sortAscending = true;

    public interface OnWordDeletedListener {
        void onDeleted();
    }

    public UserWordAdapter(Repository repository, OnWordDeletedListener deleteListener) {
        this.repository = repository;
        this.deleteListener = deleteListener;
    }

    public void setWords(List<UserWord> words) {
        this.wordList = words != null ? new ArrayList<>(words) : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSortAscending(boolean sortAscending) {
        this.sortAscending = sortAscending;
    }

    public void sortWords() {
        Collections.sort(wordList, (o1, o2) -> {
            String w1 = o1.getWord() == null ? "" : o1.getWord().toLowerCase();
            String w2 = o2.getWord() == null ? "" : o2.getWord().toLowerCase();
            return sortAscending ? w1.compareTo(w2) : w2.compareTo(w1);
        });
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        UserWord currentWord = wordList.get(position);

        holder.tvWord.setText(currentWord.getWord());
        holder.tvTranslation.setText(currentWord.getTranslation());

        String transcription = currentWord.getTranscription();
        if (transcription != null && !transcription.trim().isEmpty()) {
            holder.tvTranscription.setVisibility(View.VISIBLE);
            holder.tvTranscription.setText(transcription);
        } else {
            holder.tvTranscription.setVisibility(View.GONE);
        }

        String notes = currentWord.getNotes();
        if (notes != null && !notes.trim().isEmpty()) {
            holder.tvNotes.setVisibility(View.VISIBLE);
            holder.tvNotes.setText(notes);
        } else {
            holder.tvNotes.setVisibility(View.GONE);
        }

        holder.btnSpeak.setOnClickListener(v -> repository.speak(currentWord.getWord(), false));

        holder.btnSlowSpeak.setOnClickListener(v -> repository.speak(currentWord.getWord(), true));

        holder.btnDelete.setOnClickListener(v -> {
            repository.deleteUserWord(currentWord, deleteListener::onDeleted);
        });
    }

    @Override
    public int getItemCount() {
        return wordList.size();
    }

    static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView tvWord;
        TextView tvTranslation;
        TextView tvTranscription;
        TextView tvNotes;
        ImageButton btnSpeak;
        ImageButton btnDelete;
        MaterialButton btnSlowSpeak;

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvWord = itemView.findViewById(R.id.tvWord);
            tvTranslation = itemView.findViewById(R.id.tvTranslation);
            tvTranscription = itemView.findViewById(R.id.tvTranscription);
            tvNotes = itemView.findViewById(R.id.tvNotes);
            btnSpeak = itemView.findViewById(R.id.btnSpeak);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnSlowSpeak = itemView.findViewById(R.id.btnSlowSpeak);
        }
    }
}