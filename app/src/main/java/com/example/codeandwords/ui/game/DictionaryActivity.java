package com.example.codeandwords.ui.game;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.UserWord;
import com.example.codeandwords.model.Word;
import com.example.codeandwords.ui.adapters.WordListAdapter;
import com.example.codeandwords.ui.base.BaseBackActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Экран словаря темы: список терминов с произношением и добавлением в личный словарь
public class DictionaryActivity extends BaseBackActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvTitle;
    private Repository repository;
    private WordListAdapter adapter;

    private Long themeId;
    private String themeTitle;

    private boolean wordsLoadedOnce = false;

    // Собственный TTS — чтобы озвучка не зависела от готовности TtsManager в Repository
    private TextToSpeech tts;
    private boolean ttsReady = false;
    // Очередь отложенного запроса: проигрывается сразу после готовности TTS
    private String pendingSpeechText;
    private boolean pendingSpeechIsSlow = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);

        repository = Repository.getInstance(getApplicationContext());

        themeId = getIntent().getLongExtra("THEME_ID", -1);
        themeTitle = getIntent().getStringExtra("THEME_TITLE");

        initViews();

        tvTitle.setText(themeTitle != null ? themeTitle : "Словарь");

        setupRecyclerView();
        initTextToSpeech();
        loadWords();
        loadAddedPersonalWords();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (wordsLoadedOnce) {
            loadAddedPersonalWords();
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.rvDictionary);
        progressBar = findViewById(R.id.pbDictionary);
        tvTitle = findViewById(R.id.tvDictTitle);

        View btnBack = findViewById(R.id.btnBackDict);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> goBackToGameSelection());
        }
    }

    @Override
    public void onBackPressed() {
        goBackToGameSelection();
    }

    private void goBackToGameSelection() {
        Intent intent = new Intent(this, GameSelectionActivity.class);
        intent.putExtra("THEME_ID", themeId != null ? themeId : -1L);
        intent.putExtra("THEME_TITLE", themeTitle);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new WordListAdapter(new WordListAdapter.OnWordClickListener() {
            @Override
            public void onSpeakClick(String term, boolean isSlow) {
                speakWord(term, isSlow);
            }

            @Override
            public void onAddToDictionaryClick(Word word) {
                repository.addWordToPersonalDictionary(word, new Repository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        adapter.markWordAsAdded(word);
                        Toast.makeText(DictionaryActivity.this,
                                "Слово добавлено в личный словарь", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        if (error != null && error.toLowerCase().contains("уже есть")) {
                            adapter.markWordAsAdded(word);
                        }
                        Toast.makeText(DictionaryActivity.this, error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        recyclerView.setAdapter(adapter);
    }

    private void initTextToSpeech() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) {
                android.util.Log.e("DictionaryTTS", "TTS init failed: " + status);
                return;
            }

            int result = tts.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                android.util.Log.e("DictionaryTTS",
                        "Английский язык не поддерживается: " + result);
                return;
            }

            ttsReady = true;

            // Если был запрос на озвучку до инициализации — проигрываем сейчас
            if (pendingSpeechText != null) {
                playTts(pendingSpeechText, pendingSpeechIsSlow);
                pendingSpeechText = null;
            }
        });
    }

    private void speakWord(String text, boolean isSlow) {
        if (text == null || text.trim().isEmpty()) return;

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

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DICTIONARY_TTS");
    }

    private void loadWords() {
        progressBar.setVisibility(View.VISIBLE);

        repository.getWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> data) {
                progressBar.setVisibility(View.GONE);
                wordsLoadedOnce = true;

                List<Word> validWords = preparePlayableWords(data);

                if (validWords.isEmpty()) {
                    Toast.makeText(DictionaryActivity.this,
                            "В этой теме пока нет терминов", Toast.LENGTH_LONG).show();
                    adapter.setWords(validWords);
                    return;
                }

                java.util.Collections.shuffle(validWords);
                adapter.setWords(validWords);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(DictionaryActivity.this, "Ошибка: " + error,
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadAddedPersonalWords() {
        repository.getUserPersonalWords(new Repository.DataCallback<List<UserWord>>() {
            @Override
            public void onSuccess(List<UserWord> data) {
                Set<String> addedTerms = new HashSet<>();

                if (data != null) {
                    for (UserWord userWord : data) {
                        String term = extractUserWordTerm(userWord);
                        if (term != null && !term.trim().isEmpty()) {
                            addedTerms.add(term.trim().toLowerCase());
                        }
                    }
                }

                adapter.setAddedTerms(addedTerms);
            }

            @Override
            public void onError(String error) { }
        });
    }

    private String extractUserWordTerm(UserWord userWord) {
        if (userWord == null) return "";

        try {
            java.lang.reflect.Method m = userWord.getClass().getMethod("getWord");
            Object val = m.invoke(userWord);
            if (val != null) return val.toString();
        } catch (Exception ignored) {}

        try {
            java.lang.reflect.Field f = userWord.getClass().getDeclaredField("word");
            f.setAccessible(true);
            Object val = f.get(userWord);
            if (val != null) return val.toString();
        } catch (Exception ignored) {}

        try {
            java.lang.reflect.Method m = userWord.getClass().getMethod("getTerm");
            Object val = m.invoke(userWord);
            if (val != null) return val.toString();
        } catch (Exception ignored) {}

        try {
            java.lang.reflect.Field f = userWord.getClass().getDeclaredField("term");
            f.setAccessible(true);
            Object val = f.get(userWord);
            if (val != null) return val.toString();
        } catch (Exception ignored) {}

        return "";
    }

    private List<Word> preparePlayableWords(List<Word> words) {
        List<Word> result = new ArrayList<>();
        if (words == null) return result;

        for (Word word : words) {
            if (word == null) continue;
            String term = word.getTerm();
            String translation = word.getTranslation();
            if (term != null && translation != null
                    && !term.trim().isEmpty()
                    && !translation.trim().isEmpty()) {
                result.add(word);
            }
        }

        return result;
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }
}