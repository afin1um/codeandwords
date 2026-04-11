package com.example.codeandwords.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.List;

public class WordListAdapter extends RecyclerView.Adapter<WordListAdapter.WordViewHolder> {

    private List<Word> words = new ArrayList<>();
    private final OnWordClickListener listener;

    public interface OnWordClickListener {
        void onSpeakClick(String term, boolean isSlow);
        void onAddToDictionaryClick(Word word);
    }

    public WordListAdapter(OnWordClickListener listener) {
        this.listener = listener;
    }

    public void setWords(List<Word> words) {
        this.words = words;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_word_list, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        Word word = words.get(position);

        holder.tvTerm.setText(word.getTerm());
        holder.tvTranslation.setText(word.getTranslation());

        if (word.getTranscription() != null && !word.getTranscription().isEmpty()) {
            holder.tvTranscription.setText(word.getTranscription());
            holder.tvTranscription.setVisibility(View.VISIBLE);
        } else {
            holder.tvTranscription.setVisibility(View.GONE);
        }

        holder.btnSpeak.setOnClickListener(v -> listener.onSpeakClick(word.getTerm(), false));
        holder.btnSlow.setOnClickListener(v -> listener.onSpeakClick(word.getTerm(), true));
        holder.btnAddToDictionary.setOnClickListener(v -> listener.onAddToDictionaryClick(word));
    }

    @Override
    public int getItemCount() {
        return words.size();
    }

    static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView tvTerm, tvTranslation, tvTranscription;
        ImageButton btnSpeak, btnSlow, btnAddToDictionary;

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTerm = itemView.findViewById(R.id.tvTerm);
            tvTranslation = itemView.findViewById(R.id.tvTranslation);
            tvTranscription = itemView.findViewById(R.id.tvTranscription);
            btnSpeak = itemView.findViewById(R.id.btnSpeak);
            btnSlow = itemView.findViewById(R.id.btnSlow);
            btnAddToDictionary = itemView.findViewById(R.id.btnAddToDictionary);
        }
    }
}