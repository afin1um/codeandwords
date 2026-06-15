package com.example.codeandwords.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

// Экран создания новой теории или редактирования существующей.
public class AdminTheoryEditorActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String MODE_CREATE_NEW = "create_new";
    public static final String MODE_EDIT_EXISTING = "edit_existing";

    public static final String EXTRA_THEME_ID = "extra_theme_id";
    public static final String EXTRA_THEME_TITLE = "extra_theme_title";
    public static final String EXTRA_THEME_DESCRIPTION = "extra_theme_description";
    public static final String EXTRA_THEME_DIFFICULTY = "extra_theme_difficulty";

    private ImageButton btnCloseTheoryEditor;
    private TextView tvTheoryEditorTitle;
    private TextView tvTheoryThemeName;
    private TextView tvTheoryHint;

    private Spinner spTheoryThemes;
    private EditText etTheoryText;
    private MaterialButton btnSaveTheory;

    private Repository repository;

    private final List<Theme> themes = new ArrayList<>();
    private final List<String> themeTitles = new ArrayList<>();

    private Theme selectedTheme;

    private String mode;
    private Long editThemeId;

    private String newThemeTitle;
    private String newThemeDescription;
    private String newThemeDifficulty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_theory_editor);

        repository = Repository.getInstance(getApplicationContext());

        readIntent();
        initViews();
        setupUiByMode();
        setupListeners();

        if (MODE_EDIT_EXISTING.equals(mode)) {
            loadThemesForEditing();
        }
    }

    // Читает режим работы и исходные параметры из Intent.
    private void readIntent() {
        mode = getIntent().getStringExtra(EXTRA_MODE);

        if (mode == null || mode.trim().isEmpty()) {
            mode = MODE_EDIT_EXISTING;
        }

        editThemeId = getIntent().getLongExtra(EXTRA_THEME_ID, -1);
        if (editThemeId == -1) {
            editThemeId = null;
        }

        newThemeTitle = getIntent().getStringExtra(EXTRA_THEME_TITLE);
        newThemeDescription = getIntent().getStringExtra(EXTRA_THEME_DESCRIPTION);
        newThemeDifficulty = getIntent().getStringExtra(EXTRA_THEME_DIFFICULTY);

        if (newThemeTitle == null) newThemeTitle = "";
        if (newThemeDescription == null) newThemeDescription = "";
        if (newThemeDifficulty == null) newThemeDifficulty = "Easy";
    }

    private void initViews() {
        btnCloseTheoryEditor = findViewById(R.id.btnCloseTheoryEditor);
        tvTheoryEditorTitle = findViewById(R.id.tvTheoryEditorTitle);
        tvTheoryThemeName = findViewById(R.id.tvTheoryThemeName);
        tvTheoryHint = findViewById(R.id.tvTheoryHint);

        spTheoryThemes = findViewById(R.id.spTheoryThemes);
        etTheoryText = findViewById(R.id.etTheoryText);
        btnSaveTheory = findViewById(R.id.btnSaveTheory);
    }

    // Настраивает экран в зависимости от режима: создание или редактирование.
    private void setupUiByMode() {
        if (MODE_CREATE_NEW.equals(mode)) {
            spTheoryThemes.setVisibility(View.GONE);

            tvTheoryEditorTitle.setText("Создание теории");
            tvTheoryThemeName.setText(newThemeTitle);
            tvTheoryHint.setText("Добавьте теорию для новой темы. Если закрыть окно крестиком, тема не будет создана.");
            btnSaveTheory.setText("СОХРАНИТЬ ТЕМУ");

        } else {
            spTheoryThemes.setVisibility(View.GONE);

            tvTheoryEditorTitle.setText("Редактор теории");
            tvTheoryThemeName.setText("Загрузка темы...");
            tvTheoryHint.setText("Обновите теоретический материал выбранной темы.");
            btnSaveTheory.setText("СОХРАНИТЬ ТЕОРИЮ");
        }
    }

    private void setupListeners() {
        btnCloseTheoryEditor.setOnClickListener(v -> finish());

        spTheoryThemes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (MODE_EDIT_EXISTING.equals(mode) && position >= 0 && position < themes.size()) {
                    selectedTheme = themes.get(position);
                    showSelectedTheme();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                selectedTheme = null;
            }
        });

        btnSaveTheory.setOnClickListener(v -> {
            if (MODE_CREATE_NEW.equals(mode)) {
                createThemeWithTheory();
            } else {
                updateExistingTheory();
            }
        });
    }

    // Загружает список тем и выбирает нужную тему для редактирования.
    private void loadThemesForEditing() {
        repository.getThemes(new Repository.DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> data) {
                themes.clear();
                themeTitles.clear();

                if (data != null) {
                    themes.addAll(data);
                }

                for (Theme theme : themes) {
                    themeTitles.add(theme.getTitle() != null ? theme.getTitle() : "Без названия");
                }

                if (themes.isEmpty()) {
                    toast("Тем пока нет");
                    finish();
                    return;
                }

                if (editThemeId != null) {
                    for (Theme theme : themes) {
                        if (theme.getId() != null && theme.getId().equals(editThemeId)) {
                            selectedTheme = theme;
                            showSelectedTheme();
                            return;
                        }
                    }
                }

                spTheoryThemes.setVisibility(View.VISIBLE);

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        AdminTheoryEditorActivity.this,
                        android.R.layout.simple_spinner_item,
                        themeTitles
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spTheoryThemes.setAdapter(adapter);
            }

            @Override
            public void onError(String error) {
                toast(error);
                finish();
            }
        });
    }

    private void showSelectedTheme() {
        if (selectedTheme == null) return;

        tvTheoryThemeName.setText(selectedTheme.getTitle() != null ? selectedTheme.getTitle() : "Без названия");
        etTheoryText.setText(selectedTheme.getTheoryText() != null ? selectedTheme.getTheoryText() : "");
    }

    // Создаёт новую тему сразу вместе с текстом теории.
    private void createThemeWithTheory() {
        if (newThemeTitle.trim().isEmpty()) {
            toast("Название темы не передано");
            return;
        }

        Theme theme = new Theme();
        theme.setTitle(newThemeTitle.trim());
        theme.setDescription(newThemeDescription.trim());
        theme.setDifficultyLevel(newThemeDifficulty.trim().isEmpty() ? "Easy" : newThemeDifficulty.trim());
        theme.setTheoryText(getTheoryText());

        btnSaveTheory.setEnabled(false);

        repository.adminCreateTheme(theme, new Repository.DataCallback<Theme>() {
            @Override
            public void onSuccess(Theme data) {
                btnSaveTheory.setEnabled(true);
                toast("Тема создана");
                finish();
            }

            @Override
            public void onError(String error) {
                btnSaveTheory.setEnabled(true);
                toast(error);
            }
        });
    }

    // Обновляет теорию существующей темы.
    private void updateExistingTheory() {
        if (selectedTheme == null || selectedTheme.getId() == null) {
            toast("Тема не выбрана");
            return;
        }

        selectedTheme.setTheoryText(getTheoryText());

        btnSaveTheory.setEnabled(false);

        repository.adminUpdateTheme(selectedTheme, new Repository.DataCallback<Theme>() {
            @Override
            public void onSuccess(Theme data) {
                btnSaveTheory.setEnabled(true);
                toast("Теория сохранена");
                finish();
            }

            @Override
            public void onError(String error) {
                btnSaveTheory.setEnabled(true);
                toast(error);
            }
        });
    }

    private String getTheoryText() {
        return etTheoryText.getText() == null
                ? ""
                : etTheoryText.getText().toString().trim();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}