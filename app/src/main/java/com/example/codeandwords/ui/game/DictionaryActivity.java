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
import com.example.codeandwords.model.Word;
import com.example.codeandwords.ui.adapters.WordListAdapter;

import java.util.List;
import java.util.Locale;

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
    }

    private void initViews() {
        recyclerView = findViewById(R.id.rvDictionary);
        progressBar = findViewById(R.id.pbDictionary);
        tvTitle = findViewById(R.id.tvDictTitle);
        btnBack = findViewById(R.id.btnBackDict);

        btnBack.setOnClickListener(v -> finish());
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
                        Toast.makeText(DictionaryActivity.this, "Слово добавлено в личный словарь", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
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
                adapter.setWords(data);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(DictionaryActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Английский язык не поддерживается", Toast.LENGTH_SHORT).show();
            } else {
                isTtsReady = true;
            }
        } else {
            Log.e("TTS", "Ошибка инициализации TTS");
        }
    }

    private void speakWord(String text, boolean isSlow) {
        if (isTtsReady && text != null && !text.trim().isEmpty()) {
            tts.setSpeechRate(isSlow ? 0.4f : 1.0f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            Toast.makeText(this, "Голосовой движок еще не готов", Toast.LENGTH_SHORT).show();
        }
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