package com.example.codeandwords.ui.profile;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.Word;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

// Экран массового импорта терминов администратором.
public class AdminBulkWordsActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private Spinner spinnerTheme;
    private EditText etBulkWords;
    private MaterialButton btnSave;

    private Repository repository;

    private final List<Theme> themes = new ArrayList<>();
    private final List<String> themeTitles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_bulk_words);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        setupClicks();
        loadThemes();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBackBulkWords);
        spinnerTheme = findViewById(R.id.spinnerBulkTheme);
        etBulkWords = findViewById(R.id.etBulkWords);
        btnSave = findViewById(R.id.btnSaveBulkWords);
    }

    private void setupClicks() {
        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveBulkWords());
    }

    // Загружает список тем для выбора целевой темы.
    private void loadThemes() {
        repository.getThemes(new Repository.DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> data) {
                themes.clear();
                themeTitles.clear();

                if (data != null) {
                    for (Theme theme : data) {
                        if (theme == null || theme.getId() == null) continue;

                        themes.add(theme);
                        themeTitles.add(theme.getTitle());
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        AdminBulkWordsActivity.this,
                        android.R.layout.simple_spinner_item,
                        themeTitles
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerTheme.setAdapter(adapter);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AdminBulkWordsActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Сохраняет распарсенный список слов в выбранную тему.
    private void saveBulkWords() {
        int position = spinnerTheme.getSelectedItemPosition();

        if (position < 0 || position >= themes.size()) {
            Toast.makeText(this, "Выберите тему", Toast.LENGTH_SHORT).show();
            return;
        }

        Theme selectedTheme = themes.get(position);
        String rawText = etBulkWords.getText() == null ? "" : etBulkWords.getText().toString().trim();

        if (rawText.isEmpty()) {
            Toast.makeText(this, "Введите термины", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Word> words = parseWords(rawText);

        repository.adminCreateWordsBulk(selectedTheme.getId(), words, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> data) {
                Toast.makeText(
                        AdminBulkWordsActivity.this,
                        "Добавлено терминов: " + (data == null ? 0 : data.size()),
                        Toast.LENGTH_LONG
                ).show();
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AdminBulkWordsActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Разбирает многострочный ввод формата:
    // term;translation;transcription;definition;exampleSentence
    private List<Word> parseWords(String rawText) {
        List<Word> result = new ArrayList<>();

        String[] lines = rawText.split("\\r?\\n");

        for (String line : lines) {
            if (line == null) continue;

            String cleanLine = line.trim();

            if (cleanLine.isEmpty()) continue;

            String[] parts = cleanLine.split(";", -1);

            if (parts.length < 2) continue;

            Word word = new Word();
            word.setTerm(parts[0].trim());
            word.setTranslation(parts[1].trim());

            if (parts.length > 2) {
                word.setTranscription(parts[2].trim());
            }

            if (parts.length > 3) {
                word.setDefinition(parts[3].trim());
            }

            if (parts.length > 4) {
                word.setExampleSentence(parts[4].trim());
            }

            result.add(word);
        }

        return result;
    }
}