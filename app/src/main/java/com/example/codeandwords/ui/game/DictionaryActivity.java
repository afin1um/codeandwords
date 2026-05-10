package com.example.codeandwords.ui.game;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.UserWord;
import com.example.codeandwords.model.Word;
import com.example.codeandwords.ui.adapters.WordListAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DictionaryActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvTitle;
    private View btnBack;

    private Repository repository;
    private WordListAdapter adapter;
    private TextToSpeech tts;
    private boolean isTtsReady = false;

    private Long themeId;
    private String themeTitle;

    private boolean wordsLoadedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);

        tts = new TextToSpeech(this, this);

        initViews();

        repository = new Repository(this);

        themeId = getIntent().getLongExtra("THEME_ID", -1);
        themeTitle = getIntent().getStringExtra("THEME_TITLE");

        tvTitle.setText(themeTitle != null ? themeTitle : "Словарь");

        setupRecyclerView();
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
        btnBack = findViewById(R.id.btnBackDict);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        } else {
            Log.e("DictionaryActivity", "btnBackDict is null!");
        }
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
                                "Слово добавлено в личный словарь",
                                Toast.LENGTH_SHORT).show();
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
                            "В этой теме пока нет терминов",
                            Toast.LENGTH_LONG).show();
                    adapter.setWords(validWords);
                    return;
                }

                java.util.Collections.shuffle(validWords);
                adapter.setWords(validWords);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(DictionaryActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
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
            Method m = userWord.getClass().getMethod("getTerm");
            Object val = m.invoke(userWord);
            if (val != null) return val.toString();
        } catch (Exception ignored) {}

        try {
            Field f = userWord.getClass().getDeclaredField("term");
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
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    private void speakWord(String text, boolean isSlow) {
        if (!isTtsReady || text == null) return;

        tts.setSpeechRate(isSlow ? 0.4f : 1.0f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}