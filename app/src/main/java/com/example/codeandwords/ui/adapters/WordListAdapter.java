package com.example.codeandwords.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Адаптер списка терминов с произношением и добавлением в личный словарь.
// Звезда меняет вид после добавления слова, не требуя перезагрузки списка.
public class WordListAdapter extends RecyclerView.Adapter<WordListAdapter.WordViewHolder> {

    private List<Word> words = new ArrayList<>();
    private final Set<String> addedTerms = new HashSet<>();
    private final OnWordClickListener listener;

    public interface OnWordClickListener {
        void onSpeakClick(String term, boolean isSlow);
        void onAddToDictionaryClick(Word word);
    }

    public WordListAdapter(OnWordClickListener listener) {
        this.listener = listener;
    }

    public void setWords(List<Word> words) {
        this.words = words != null ? words : new ArrayList<>();
        notifyDataSetChanged();
    }

    // Обновляет набор добавленных терминов и перерисовывает список
    public void setAddedTerms(Set<String> terms) {
        addedTerms.clear();

        if (terms != null) {
            for (String term : terms) {
                addedTerms.add(normalizeTerm(term));
            }
        }

        notifyDataSetChanged();
    }

    // Помечает конкретное слово как добавленное без полной перерисовки
    public void markWordAsAdded(Word word) {
        if (word == null || word.getTerm() == null) return;

        addedTerms.add(normalizeTerm(word.getTerm()));
        notifyDataSetChanged();
    }

    private String normalizeTerm(String term) {
        return term == null ? "" : term.trim().toLowerCase();
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word_list, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        Word word = words.get(position);

        String term = word.getTerm() != null ? word.getTerm().trim() : "";
        String translation = word.getTranslation() != null ? word.getTranslation().trim() : "";
        String transcription = word.getTranscription() != null ? word.getTranscription().trim() : "";

        holder.tvTerm.setText(term.isEmpty() ? "Без термина" : term);
        holder.tvTranslation.setText(translation.isEmpty() ? "Перевод не указан" : translation);

        if (!transcription.isEmpty()) {
            holder.tvTranscription.setText(transcription);
            holder.tvTranscription.setVisibility(View.VISIBLE);
        } else {
            holder.tvTranscription.setVisibility(View.GONE);
        }

        boolean alreadyAdded = addedTerms.contains(normalizeTerm(term));
        bindDictionaryState(holder, alreadyAdded);

        holder.btnSpeak.setOnClickListener(v -> {
            if (listener != null) listener.onSpeakClick(term, false);
        });

        holder.btnSlow.setOnClickListener(v -> {
            if (listener != null) listener.onSpeakClick(term, true);
        });

        holder.btnAddToDictionary.setOnClickListener(v -> {
            if (alreadyAdded) return;
            if (listener != null) listener.onAddToDictionaryClick(word);
        });
    }

    // Обновляет внешний вид кнопки словаря в зависимости от состояния добавления
    private void bindDictionaryState(@NonNull WordViewHolder holder, boolean alreadyAdded) {
        if (alreadyAdded) {
            holder.btnAddToDictionary.setText("★");
            holder.btnAddToDictionary.setTextColor(0xFF58CC02);
            holder.btnAddToDictionary.setAlpha(1.0f);
            holder.btnAddToDictionary.setEnabled(false);
            holder.btnAddToDictionary.setContentDescription("Слово уже добавлено в словарь");
        } else {
            holder.btnAddToDictionary.setText("☆");
            holder.btnAddToDictionary.setTextColor(0xFFFFD43B);
            holder.btnAddToDictionary.setAlpha(1.0f);
            holder.btnAddToDictionary.setEnabled(true);
            holder.btnAddToDictionary.setContentDescription("Добавить слово в словарь");
        }
    }

    @Override
    public int getItemCount() {
        return words.size();
    }

    static class WordViewHolder extends RecyclerView.ViewHolder {

        TextView tvTerm;
        TextView tvTranslation;
        TextView tvTranscription;
        TextView btnSpeak;
        TextView btnSlow;
        TextView btnAddToDictionary;

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