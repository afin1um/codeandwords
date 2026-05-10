package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.Word;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminWordsActivity extends AppCompatActivity {

    private ImageButton btnBackAdminWords;

    private Spinner spThemesForWord;

    private TextInputEditText etTerm;
    private TextInputEditText etTranslation;
    private TextInputEditText etTranscription;
    private TextInputEditText etDefinition;
    private TextInputEditText etExample;

    private MaterialButton btnSaveWord;
    private MaterialButton btnClearWord;
    private MaterialButton btnAdminBulkWords;

    private ProgressBar progressAdminWords;
    private RecyclerView rvAdminWords;

    private Repository repository;
    private AdminWordAdapter wordAdapter;

    private final List<Theme> themes = new ArrayList<>();
    private final List<String> themeTitles = new ArrayList<>();
    private final List<Word> currentThemeWords = new ArrayList<>();

    private Word editingWord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_words);

        repository = new Repository(this);

        initViews();
        setupRecycler();
        setupClicks();
        loadThemes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadThemes();
    }

    private void initViews() {
        btnBackAdminWords = findViewById(R.id.btnBackAdminWords);

        spThemesForWord = findViewById(R.id.spThemesForWord);

        etTerm = findViewById(R.id.etTerm);
        etTranslation = findViewById(R.id.etTranslation);
        etTranscription = findViewById(R.id.etTranscription);
        etDefinition = findViewById(R.id.etDefinition);
        etExample = findViewById(R.id.etExample);

        btnSaveWord = findViewById(R.id.btnSaveWord);
        btnClearWord = findViewById(R.id.btnClearWord);
        btnAdminBulkWords = findViewById(R.id.btnAdminBulkWords);

        progressAdminWords = findViewById(R.id.progressAdminWords);
        rvAdminWords = findViewById(R.id.rvAdminWords);
    }

    private void setupRecycler() {
        wordAdapter = new AdminWordAdapter(new AdminWordAdapter.Listener() {
            @Override
            public void onEditWord(Word word) {
                fillWordForm(word);
            }

            @Override
            public void onDeleteWord(Word word) {
                deleteWord(word);
            }
        });

        rvAdminWords.setLayoutManager(new LinearLayoutManager(this));
        rvAdminWords.setAdapter(wordAdapter);
    }

    private void setupClicks() {
        btnBackAdminWords.setOnClickListener(v -> finish());
        btnSaveWord.setOnClickListener(v -> saveWord());
        btnClearWord.setOnClickListener(v -> clearWordForm());

        btnAdminBulkWords.setOnClickListener(v -> {
            Intent intent = new Intent(AdminWordsActivity.this, AdminBulkWordsActivity.class);
            startActivity(intent);
        });

        spThemesForWord.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < themes.size()) {
                    loadWords(themes.get(position).getId());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void loadThemes() {
        setLoading(true);

        repository.getThemes(new Repository.DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> data) {
                setLoading(false);

                themes.clear();
                themeTitles.clear();

                if (data != null) {
                    themes.addAll(data);
                }

                for (Theme theme : themes) {
                    themeTitles.add(theme.getTitle() != null ? theme.getTitle() : "Без названия");
                }

                if (themeTitles.isEmpty()) {
                    themeTitles.add("Тем пока нет");
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        AdminWordsActivity.this,
                        android.R.layout.simple_spinner_item,
                        themeTitles
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spThemesForWord.setAdapter(adapter);

                if (!themes.isEmpty()) {
                    loadWords(themes.get(0).getId());
                } else {
                    currentThemeWords.clear();
                    wordAdapter.setItems(new ArrayList<>());
                }
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                themes.clear();
                themeTitles.clear();
                currentThemeWords.clear();
                wordAdapter.setItems(new ArrayList<>());
                toast(error != null ? error : "Не удалось загрузить темы");
            }
        });
    }

    private void loadWords(Long themeId) {
        if (themeId == null) return;

        repository.getWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> data) {
                currentThemeWords.clear();

                if (data != null) {
                    currentThemeWords.addAll(data);
                }

                wordAdapter.setItems(currentThemeWords);
            }

            @Override
            public void onError(String error) {
                currentThemeWords.clear();
                wordAdapter.setItems(new ArrayList<>());
            }
        });
    }

    private void saveWord() {
        if (themes.isEmpty()) {
            toast("Сначала создайте тему");
            return;
        }

        int position = spThemesForWord.getSelectedItemPosition();

        if (position < 0 || position >= themes.size()) {
            toast("Выберите тему");
            return;
        }

        Theme selectedTheme = themes.get(position);

        String term = text(etTerm);
        String translation = text(etTranslation);

        if (term.isEmpty()) {
            toast("Введите термин");
            return;
        }

        if (translation.isEmpty()) {
            toast("Введите перевод");
            return;
        }

        if (isWordDuplicate(selectedTheme.getId(), term, translation, editingWord)) {
            toast("Такой термин уже есть в выбранной теме");
            return;
        }

        Word word = editingWord != null ? editingWord : new Word();
        word.setThemeId(selectedTheme.getId());
        word.setTerm(term);
        word.setTranslation(translation);
        word.setTranscription(text(etTranscription));
        word.setDefinition(text(etDefinition));
        word.setExampleSentence(text(etExample));

        setLoading(true);

        if (editingWord == null) {
            repository.adminCreateWord(word, new Repository.DataCallback<Word>() {
                @Override
                public void onSuccess(Word data) {
                    setLoading(false);
                    toast("Термин добавлен");
                    clearWordForm();
                    loadWords(selectedTheme.getId());
                }

                @Override
                public void onError(String error) {
                    setLoading(false);
                    toast(error != null ? error : "Не удалось добавить термин");
                }
            });
        } else {
            repository.adminUpdateWord(word, new Repository.DataCallback<Word>() {
                @Override
                public void onSuccess(Word data) {
                    setLoading(false);
                    toast("Термин обновлён");
                    clearWordForm();
                    loadWords(selectedTheme.getId());
                }

                @Override
                public void onError(String error) {
                    setLoading(false);
                    toast(error != null ? error : "Не удалось обновить термин");
                }
            });
        }
    }

    private boolean isWordDuplicate(Long themeId, String term, String translation, Word currentEditingWord) {
        String safeTerm = normalize(term);
        String safeTranslation = normalize(translation);
        Long editingId = currentEditingWord != null ? currentEditingWord.getId() : null;

        for (Word word : currentThemeWords) {
            if (word == null) continue;

            Long wordId = word.getId();

            if (editingId != null && wordId != null && editingId.equals(wordId)) {
                continue;
            }

            if (themeId != null && word.getThemeId() != null && !themeId.equals(word.getThemeId())) {
                continue;
            }

            String existingTerm = normalize(word.getTerm());
            String existingTranslation = normalize(word.getTranslation());

            boolean sameTerm = existingTerm.equals(safeTerm);
            boolean sameTermAndTranslation =
                    existingTerm.equals(safeTerm)
                            && existingTranslation.equals(safeTranslation);

            if (sameTerm || sameTermAndTranslation) {
                return true;
            }
        }

        return false;
    }

    private void fillWordForm(Word word) {
        if (word == null) return;

        editingWord = word;

        etTerm.setText(word.getTerm() != null ? word.getTerm() : "");
        etTranslation.setText(word.getTranslation() != null ? word.getTranslation() : "");
        etTranscription.setText(word.getTranscription() != null ? word.getTranscription() : "");
        etDefinition.setText(word.getDefinition() != null ? word.getDefinition() : "");
        etExample.setText(word.getExampleSentence() != null ? word.getExampleSentence() : "");

        for (int i = 0; i < themes.size(); i++) {
            if (themes.get(i).getId() != null && themes.get(i).getId().equals(word.getThemeId())) {
                spThemesForWord.setSelection(i);
                break;
            }
        }

        btnSaveWord.setText("ОБНОВИТЬ ТЕРМИН");
    }

    private void deleteWord(Word word) {
        if (word == null || word.getId() == null) {
            toast("Термин не найден");
            return;
        }

        Long themeId = word.getThemeId();

        setLoading(true);

        repository.adminDeleteWord(word.getId(), new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                setLoading(false);
                toast("Термин удалён");
                clearWordForm();

                if (themeId != null) {
                    loadWords(themeId);
                }
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                toast(error != null ? error : "Не удалось удалить термин");
            }
        });
    }

    private void clearWordForm() {
        editingWord = null;
        etTerm.setText("");
        etTranslation.setText("");
        etTranscription.setText("");
        etDefinition.setText("");
        etExample.setText("");
        btnSaveWord.setText("СОХРАНИТЬ ТЕРМИН");
    }

    private String text(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void setLoading(boolean loading) {
        progressAdminWords.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveWord.setEnabled(!loading);
        btnClearWord.setEnabled(!loading);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}