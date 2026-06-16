package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.Word;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Экран управления темами: создание, редактирование, просмотр слов и удаление.
public class AdminThemesActivity extends AppCompatActivity {

    private ImageButton btnBackAdminThemes;
    private TextInputEditText etThemeTitle;
    private TextInputEditText etThemeDescription;
    private Spinner spDifficulty;
    private MaterialButton btnSaveTheme;
    private MaterialButton btnClearTheme;
    private ProgressBar progressAdminThemes;
    private RecyclerView rvAdminThemes;

    private Repository repository;
    private AdminThemeAdapter themeAdapter;

    private final List<Theme> loadedThemes = new ArrayList<>();
    private Theme editingTheme;

    private final String[] difficulties = new String[]{"Easy", "Medium", "Hard"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_themes);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        setupSpinner();
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
        btnBackAdminThemes = findViewById(R.id.btnBackAdminThemes);
        etThemeTitle = findViewById(R.id.etThemeTitle);
        etThemeDescription = findViewById(R.id.etThemeDescription);
        spDifficulty = findViewById(R.id.spDifficulty);
        btnSaveTheme = findViewById(R.id.btnSaveTheme);
        btnClearTheme = findViewById(R.id.btnClearTheme);
        progressAdminThemes = findViewById(R.id.progressAdminThemes);
        rvAdminThemes = findViewById(R.id.rvAdminThemes);
    }

    private void setupSpinner() {
        ArrayAdapter<String> difficultyAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                difficulties
        );
        difficultyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDifficulty.setAdapter(difficultyAdapter);
    }

    private void setupRecycler() {
        themeAdapter = new AdminThemeAdapter(new AdminThemeAdapter.Listener() {
            @Override
            public void onPreviewThemeWords(Theme theme) {
                previewThemeWords(theme);
            }

            @Override
            public void onEditTheme(Theme theme) {
                fillThemeForm(theme);
            }

            @Override
            public void onEditThemeTheory(Theme theme) {
                openTheoryEditor(theme);
            }

            @Override
            public void onDeleteTheme(Theme theme) {
                confirmDeleteTheme(theme);
            }
        });

        rvAdminThemes.setLayoutManager(new LinearLayoutManager(this));
        rvAdminThemes.setAdapter(themeAdapter);
    }

    private void setupClicks() {
        btnBackAdminThemes.setOnClickListener(v -> finish());
        btnSaveTheme.setOnClickListener(v -> saveTheme());
        btnClearTheme.setOnClickListener(v -> clearThemeForm());
    }

    private void loadThemes() {
        setLoading(true);

        repository.getThemes(new Repository.DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> data) {
                setLoading(false);

                loadedThemes.clear();

                if (data != null) {
                    loadedThemes.addAll(data);
                }

                themeAdapter.setItems(loadedThemes);
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                loadedThemes.clear();
                themeAdapter.setItems(new ArrayList<>());
                toast(error != null ? error : "Не удалось загрузить темы");
            }
        });
    }

    // Создаёт новую тему или мгновенно обновляет существующую (репозиторий делает локально + фоном на сервер)
    private void saveTheme() {
        String title = text(etThemeTitle);
        String description = text(etThemeDescription);
        String difficulty = spDifficulty.getSelectedItem() != null
                ? spDifficulty.getSelectedItem().toString()
                : "Easy";

        if (title.isEmpty()) {
            toast("Введите название темы");
            return;
        }

        if (isThemeDuplicate(title, editingTheme)) {
            toast("Тема с таким названием уже существует");
            return;
        }

        if (editingTheme == null) {
            Intent intent = new Intent(AdminThemesActivity.this, AdminTheoryEditorActivity.class);
            intent.putExtra(AdminTheoryEditorActivity.EXTRA_MODE, AdminTheoryEditorActivity.MODE_CREATE_NEW);
            intent.putExtra(AdminTheoryEditorActivity.EXTRA_THEME_TITLE, title);
            intent.putExtra(AdminTheoryEditorActivity.EXTRA_THEME_DESCRIPTION, description);
            intent.putExtra(AdminTheoryEditorActivity.EXTRA_THEME_DIFFICULTY, difficulty);
            startActivity(intent);
            return;
        }

        editingTheme.setTitle(title);
        editingTheme.setDescription(description);
        editingTheme.setDifficultyLevel(difficulty);

        if (editingTheme.getTheoryText() == null) {
            editingTheme.setTheoryText("");
        }

        // 1) Сразу обновляем список локально, чтобы пользователь видел изменения мгновенно
        replaceInLoadedThemes(editingTheme);
        themeAdapter.setItems(loadedThemes);

        Theme themeSnapshot = editingTheme;
        clearThemeForm();
        toast("Тема обновлена");

        // 2) Реальное сохранение — мгновенно локально, в фоне на сервер
        repository.adminUpdateTheme(themeSnapshot, new Repository.DataCallback<Theme>() {
            @Override
            public void onSuccess(Theme data) {
                // Без spinner-а — обновление уже отображено
            }

            @Override
            public void onError(String error) {
                toast(error != null ? error : "Не удалось обновить тему");
            }
        });
    }

    private void replaceInLoadedThemes(Theme updated) {
        if (updated == null || updated.getId() == null) return;

        for (int i = 0; i < loadedThemes.size(); i++) {
            Theme t = loadedThemes.get(i);
            if (t != null && updated.getId().equals(t.getId())) {
                loadedThemes.set(i, updated);
                return;
            }
        }
    }

    private boolean isThemeDuplicate(String title, Theme currentEditingTheme) {
        String safeTitle = normalize(title);

        if (safeTitle.isEmpty()) {
            return false;
        }

        Long editingId = currentEditingTheme != null ? currentEditingTheme.getId() : null;

        for (Theme theme : loadedThemes) {
            if (theme == null) continue;

            Long themeId = theme.getId();
            String existingTitle = normalize(theme.getTitle());

            if (existingTitle.equals(safeTitle)) {
                if (editingId == null) {
                    return true;
                }

                if (themeId == null || !themeId.equals(editingId)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void fillThemeForm(Theme theme) {
        if (theme == null) return;

        editingTheme = theme;

        etThemeTitle.setText(theme.getTitle() != null ? theme.getTitle() : "");
        etThemeDescription.setText(theme.getDescription() != null ? theme.getDescription() : "");

        String difficulty = theme.getDifficultyLevel() == null ? "Easy" : theme.getDifficultyLevel();

        for (int i = 0; i < difficulties.length; i++) {
            if (difficulties[i].equalsIgnoreCase(difficulty)) {
                spDifficulty.setSelection(i);
                break;
            }
        }

        btnSaveTheme.setText("ОБНОВИТЬ ТЕМУ");
    }

    private void openTheoryEditor(Theme theme) {
        if (theme == null || theme.getId() == null) {
            toast("Тема не найдена");
            return;
        }

        Intent intent = new Intent(AdminThemesActivity.this, AdminTheoryEditorActivity.class);
        intent.putExtra(AdminTheoryEditorActivity.EXTRA_MODE, AdminTheoryEditorActivity.MODE_EDIT_EXISTING);
        intent.putExtra(AdminTheoryEditorActivity.EXTRA_THEME_ID, theme.getId());
        startActivity(intent);
    }

    private void previewThemeWords(Theme theme) {
        if (theme == null || theme.getId() == null) {
            toast("Тема не найдена");
            return;
        }

        // Без spinner-а — getWordsByTheme отдаёт локальные данные быстро
        repository.getWordsByTheme(theme.getId(), new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                showWordsPreviewBottomSheet(theme, words != null ? words : new ArrayList<>());
            }

            @Override
            public void onError(String error) {
                showWordsPreviewBottomSheet(theme, new ArrayList<>());
            }
        });
    }

    private void showWordsPreviewBottomSheet(Theme theme, List<Word> words) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_theme_words_preview, null, false);

        TextView tvTitle = sheetView.findViewById(R.id.tvDialogThemeWordsTitle);
        TextView tvSubtitle = sheetView.findViewById(R.id.tvDialogThemeWordsSubtitle);
        TextView tvEmpty = sheetView.findViewById(R.id.tvDialogThemeWordsEmpty);
        TextView btnClose = sheetView.findViewById(R.id.btnCloseThemeWordsDialog);
        RecyclerView rvWords = sheetView.findViewById(R.id.rvDialogThemeWords);

        String themeTitle = theme.getTitle() != null && !theme.getTitle().trim().isEmpty()
                ? theme.getTitle().trim()
                : "Тема";

        tvTitle.setText(themeTitle);

        int count = words != null ? words.size() : 0;
        tvSubtitle.setText(count + " " + getTermEnding(count));

        ThemeWordsPreviewAdapter adapter = new ThemeWordsPreviewAdapter();
        rvWords.setLayoutManager(new LinearLayoutManager(this));
        rvWords.setAdapter(adapter);

        if (words == null || words.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvWords.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvWords.setVisibility(View.VISIBLE);
            adapter.setItems(words);
        }

        btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.setContentView(sheetView);
        bottomSheetDialog.show();
    }

    private String getTermEnding(int count) {
        int lastTwo = count % 100;
        int last = count % 10;

        if (lastTwo >= 11 && lastTwo <= 14) {
            return "терминов";
        }

        if (last == 1) {
            return "термин";
        }

        if (last >= 2 && last <= 4) {
            return "термина";
        }

        return "терминов";
    }

    private void confirmDeleteTheme(Theme theme) {
        if (theme == null || theme.getId() == null) {
            toast("Тема не найдена");
            return;
        }

        repository.getWordsByTheme(theme.getId(), new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                showDeleteThemeDialog(theme, words != null ? words.size() : 0);
            }

            @Override
            public void onError(String error) {
                showDeleteThemeDialog(theme, 0);
            }
        });
    }

    private void showDeleteThemeDialog(Theme theme, int wordsCount) {
        String title = theme.getTitle() != null && !theme.getTitle().trim().isEmpty()
                ? theme.getTitle().trim()
                : "Без названия";

        String message =
                "Вы действительно хотите удалить тему \"" + title + "\"?\n\n" +
                        "Будет удалено:\n" +
                        "• тема;\n" +
                        "• термины внутри темы: " + wordsCount + ";\n" +
                        "• прогресс пользователей по этим терминам;\n" +
                        "• привязки словаря к этой теме.\n\n" +
                        "Это действие нельзя отменить.";

        new AlertDialog.Builder(this)
                .setTitle("Удалить тему?")
                .setMessage(message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> deleteTheme(theme))
                .show();
    }

    private void deleteTheme(Theme theme) {
        if (theme == null || theme.getId() == null) {
            toast("Тема не найдена");
            return;
        }

        // Сразу убираем тему из списка
        removeFromLoadedThemes(theme.getId());
        themeAdapter.setItems(loadedThemes);
        clearThemeForm();
        toast("Тема удалена");

        repository.adminDeleteTheme(theme.getId(), new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) { }

            @Override
            public void onError(String error) {
                toast(error != null ? error : "Не удалось удалить тему");
            }
        });
    }

    private void removeFromLoadedThemes(Long id) {
        if (id == null) return;
        for (int i = loadedThemes.size() - 1; i >= 0; i--) {
            Theme t = loadedThemes.get(i);
            if (t != null && id.equals(t.getId())) {
                loadedThemes.remove(i);
            }
        }
    }

    private void clearThemeForm() {
        editingTheme = null;
        etThemeTitle.setText("");
        etThemeDescription.setText("");
        spDifficulty.setSelection(0);
        btnSaveTheme.setText("СОЗДАТЬ ТЕМУ И ТЕОРИЮ");
    }

    private String text(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void setLoading(boolean loading) {
        progressAdminThemes.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveTheme.setEnabled(!loading);
        btnClearTheme.setEnabled(!loading);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}