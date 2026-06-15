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

// Адаптер предпросмотра терминов темы в административном BottomSheet.
public class ThemeWordsPreviewAdapter extends RecyclerView.Adapter<ThemeWordsPreviewAdapter.WordViewHolder> {

    private final List<Word> items = new ArrayList<>();

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
                .inflate(R.layout.item_theme_preview_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        Word word = items.get(position);

        String term = word.getTerm() != null ? word.getTerm().trim() : "Без термина";
        String translation = word.getTranslation() != null ? word.getTranslation().trim() : "Без перевода";
        String transcription = word.getTranscription() != null ? word.getTranscription().trim() : "";

        holder.tvIndex.setText(String.valueOf(position + 1));
        holder.tvTerm.setText(term);
        holder.tvTranslation.setText(translation);

        if (!transcription.isEmpty()) {
            holder.tvTranscription.setText(transcription);
            holder.tvTranscription.setVisibility(View.VISIBLE);
        } else {
            holder.tvTranscription.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView tvIndex;
        TextView tvTerm;
        TextView tvTranslation;
        TextView tvTranscription;

        WordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tvPreviewWordIndex);
            tvTerm = itemView.findViewById(R.id.tvPreviewWordTerm);
            tvTranslation = itemView.findViewById(R.id.tvPreviewWordTranslation);
            tvTranscription = itemView.findViewById(R.id.tvPreviewWordTranscription);
        }
    }
}