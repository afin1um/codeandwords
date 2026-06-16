package com.example.codeandwords.ui.adapters;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
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
import java.util.List;
import java.util.Locale;

// Адаптер личного словаря пользователя с поддержкой произношения, сортировки и удаления.
// Использует собственный TextToSpeech, чтобы озвучка работала независимо от состояния TtsManager в Repository.
public class UserWordAdapter extends RecyclerView.Adapter<UserWordAdapter.WordViewHolder> {

    private static final String TAG = "UserWordAdapter";

    private List<UserWord> wordList = new ArrayList<>();
    private final Repository repository;
    private final OnWordDeletedListener deleteListener;
    private boolean sortAscending = true;

    // Собственный TextToSpeech
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // Если кнопку нажали до того, как TTS был готов — запоминаем последнее слово и проиграем после готовности
    private String pendingSpeechText;
    private boolean pendingSpeechIsSlow = false;

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

    // Ленивая инициализация TTS — при первом нажатии на «озвучить»
    private void ensureTtsInitialized(Context context) {
        if (tts != null) return;

        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS init failed: " + status);
                return;
            }

            int result = tts.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Английский язык не поддерживается: " + result);
                return;
            }

            ttsReady = true;

            // Если был отложенный запрос — проигрываем
            if (pendingSpeechText != null) {
                playTts(pendingSpeechText, pendingSpeechIsSlow);
                pendingSpeechText = null;
            }
        });
    }

    private void speak(Context context, String text, boolean isSlow) {
        if (text == null || text.trim().isEmpty()) return;

        ensureTtsInitialized(context);

        if (ttsReady && tts != null) {
            playTts(text, isSlow);
        } else {
            // TTS ещё не готов — запоминаем и проиграем после инициализации
            pendingSpeechText = text;
            pendingSpeechIsSlow = isSlow;
        }
    }

    private void playTts(String text, boolean isSlow) {
        if (tts == null) return;

        try {
            tts.setSpeechRate(isSlow ? 0.55f : 1.0f);
        } catch (Exception ignored) {}

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "USER_WORD_TTS");
    }

    /**
     * Освобождает ресурсы TTS. Вызывать из onDestroy() активити,
     * в которой используется этот адаптер.
     */
    public void releaseTts() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
            ttsReady = false;
            pendingSpeechText = null;
        }
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_word, parent, false);
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

        holder.btnSpeak.setOnClickListener(v ->
                speak(v.getContext(), currentWord.getWord(), false));

        holder.btnSlowSpeak.setOnClickListener(v ->
                speak(v.getContext(), currentWord.getWord(), true));

        holder.btnDelete.setOnClickListener(v ->
                repository.deleteUserWord(currentWord, deleteListener::onDeleted));
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