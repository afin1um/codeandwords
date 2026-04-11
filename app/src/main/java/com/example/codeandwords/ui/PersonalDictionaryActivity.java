package com.example.codeandwords.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.UserWord;
import com.example.codeandwords.ui.adapters.UserWordAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class PersonalDictionaryActivity extends AppCompatActivity {

    private Repository repository;
    private UserWordAdapter adapter;

    private RecyclerView rvWords;
    private FloatingActionButton fabAdd;
    private TextView tvWordsCount;
    private TextView tvSort;
    private TextView tvEmpty;
    private MaterialButton btnStartWordsTraining;
    private ImageButton btnBack;

    private boolean sortAscending = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_dictionary);

        repository = new Repository(this);

        initViews();
        setupRecycler();
        setupClicks();

        loadUserWords();
    }

    private void initViews() {
        rvWords = findViewById(R.id.rvUserWords);
        fabAdd = findViewById(R.id.fabAddWord);
        tvWordsCount = findViewById(R.id.tvWordsCount);
        tvSort = findViewById(R.id.tvSort);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnStartWordsTraining = findViewById(R.id.btnStartWordsTraining);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupRecycler() {
        rvWords.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserWordAdapter(repository, this::loadUserWords);
        rvWords.setAdapter(adapter);
    }

    private void setupClicks() {
        fabAdd.setOnClickListener(v -> showAddWordDialog());

        btnBack.setOnClickListener(v -> finish());

        btnStartWordsTraining.setOnClickListener(v ->
                Toast.makeText(this, "Тренировка по словам будет добавлена следующим шагом", Toast.LENGTH_SHORT).show()
        );

        tvSort.setOnClickListener(v -> {
            sortAscending = !sortAscending;
            adapter.setSortAscending(sortAscending);
            adapter.sortWords();
            tvSort.setText(sortAscending ? "СОРТИРОВКА: А-Я" : "СОРТИРОВКА: Я-А");
        });
    }

    private void loadUserWords() {
        repository.getUserPersonalWords(new Repository.DataCallback<List<UserWord>>() {
            @Override
            public void onSuccess(List<UserWord> data) {
                int count = data != null ? data.size() : 0;
                tvWordsCount.setText(count + " слов");

                if (count == 0) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvWords.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rvWords.setVisibility(View.VISIBLE);
                }

                adapter.setWords(data);
                adapter.setSortAscending(sortAscending);
                adapter.sortWords();
            }

            @Override
            public void onError(String error) {
                tvWordsCount.setText("0 слов");
                tvEmpty.setVisibility(View.VISIBLE);
                rvWords.setVisibility(View.GONE);
                Toast.makeText(PersonalDictionaryActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddWordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_word, null);

        EditText etWord = view.findViewById(R.id.etNewWord);
        EditText etTranslation = view.findViewById(R.id.etNewTranslation);
        EditText etTranscription = view.findViewById(R.id.etNewTranscription);
        EditText etNotes = view.findViewById(R.id.etNewNotes);

        builder.setView(view)
                .setTitle("Добавить новое слово")
                .setPositiveButton("Добавить", (dialog, which) -> {
                    String word = etWord.getText().toString().trim();
                    String translation = etTranslation.getText().toString().trim();
                    String transcription = etTranscription.getText().toString().trim();
                    String notes = etNotes.getText().toString().trim();

                    if (!word.isEmpty() && !translation.isEmpty()) {
                        repository.addUserWord(word, translation, transcription, notes, new Repository.DataCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                loadUserWords();
                                Toast.makeText(PersonalDictionaryActivity.this, "Добавлено!", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(PersonalDictionaryActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        Toast.makeText(PersonalDictionaryActivity.this, "Заполните слово и перевод", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (repository != null) {
            repository.onDestroy();
        }
    }


}