package com.example.codeandwords.data.admin;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.AppDatabase;
import com.example.codeandwords.db.ThemeDao;
import com.example.codeandwords.db.UserWordDao;
import com.example.codeandwords.db.WordDao;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Репозиторий для административных операций: управление темами и словами.
// Стратегия: сначала локально (мгновенный UI), потом синхронизация с сервером в фоне.
public class AdminRepository {

    private static final String TAG = "AdminRepository";

    private final AppDatabase database;
    private final ThemeDao themeDao;
    private final WordDao wordDao;
    private final UserWordDao userWordDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private AdminListener listener;

    public AdminRepository(AppDatabase database,
                           ThemeDao themeDao,
                           WordDao wordDao,
                           UserWordDao userWordDao,
                           ApiService apiService,
                           ExecutorService executor,
                           Handler mainHandler) {
        this.database = database;
        this.themeDao = themeDao;
        this.wordDao = wordDao;
        this.userWordDao = userWordDao;
        this.apiService = apiService;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    public interface AdminListener {
        String normalizeText(String value);
        List<Long> extractWordIds(List<Word> words);
        String buildIdsFilter(List<Long> ids);
        void getCurrentUserId(OnUserIdRetrieved callback);
        User getCurrentUser();
        void restoreCurrentUserFromPrefs();

        interface OnUserIdRetrieved {
            void onRetrieved(Integer userId);
        }
    }

    public void setListener(AdminListener listener) {
        this.listener = listener;
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    private String normalizeText(String value) {
        return listener != null ? listener.normalizeText(value) : (value == null ? "" : value.trim());
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) return response.errorBody().string();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    public boolean isCurrentUserAdmin() {
        User currentUser = listener != null ? listener.getCurrentUser() : null;

        if (currentUser == null && listener != null) {
            listener.restoreCurrentUserFromPrefs();
            currentUser = listener.getCurrentUser();
        }

        return currentUser != null
                && currentUser.getRole() != null
                && "admin".equalsIgnoreCase(currentUser.getRole().trim());
    }

    // ============================================================
    // СОЗДАНИЕ ТЕМЫ
    // ============================================================
    public void adminCreateTheme(Theme theme, DataCallback<Theme> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (theme == null) {
            callback.onError("Тема не заполнена");
            return;
        }

        String title = theme.getTitle() == null ? "" : theme.getTitle().trim();
        String description = theme.getDescription() == null ? "" : theme.getDescription().trim();
        String difficulty = theme.getDifficultyLevel() == null ? "Easy" : theme.getDifficultyLevel().trim();
        String theoryText = theme.getTheoryText() == null ? "" : theme.getTheoryText().trim();

        if (title.isEmpty()) {
            callback.onError("Введите название темы");
            return;
        }

        theme.setTitle(title);
        theme.setDescription(description);
        theme.setDifficultyLevel(difficulty.isEmpty() ? "Easy" : difficulty);
        theme.setTheoryText(theoryText);

        // 1) Мгновенно сохраняем локально + отдаём UI
        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    if (theme.getId() == null || theme.getId() <= 0) {
                        Long maxId = themeDao.getMaxThemeId();
                        theme.setId(maxId == null ? 1L : maxId + 1L);
                    }
                    themeDao.insert(theme);
                });

                mainHandler.post(() -> callback.onSuccess(theme));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального создания темы: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось создать тему"));
                return;
            }

            // 2) В фоне создаём на сервере и подменяем ID на серверный
            apiService.adminCreateTheme(theme).enqueue(new Callback<List<Theme>>() {
                @Override
                public void onResponse(Call<List<Theme>> call, Response<List<Theme>> response) {
                    if (response.isSuccessful()
                            && response.body() != null
                            && !response.body().isEmpty()) {

                        Theme savedTheme = response.body().get(0);

                        executor.execute(() -> {
                            try {
                                // Если сервер вернул другой id — заменяем локальную запись
                                if (savedTheme.getId() != null
                                        && !savedTheme.getId().equals(theme.getId())) {
                                    database.runInTransaction(() -> {
                                        try {
                                            themeDao.deleteThemeById(theme.getId());
                                        } catch (Exception ignored) {}
                                        themeDao.insert(savedTheme);
                                    });
                                } else {
                                    themeDao.insert(savedTheme);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Не удалось синхронизировать созданную тему: "
                                        + e.getMessage(), e);
                            }
                        });
                    } else {
                        Log.w(TAG, "Сервер не создал тему: " + response.code()
                                + " | " + getErrorBody(response));
                    }
                }

                @Override
                public void onFailure(Call<List<Theme>> call, Throwable t) {
                    Log.w(TAG, "Ошибка сети adminCreateTheme: " + t.getMessage(), t);
                }
            });
        });
    }

    // ============================================================
    // ОБНОВЛЕНИЕ ТЕМЫ
    // ============================================================
    public void adminUpdateTheme(Theme theme, DataCallback<Theme> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (theme == null || theme.getId() == null) {
            callback.onError("Тема не найдена");
            return;
        }

        // 1) Мгновенно сохраняем локально и отдаём UI
        executor.execute(() -> {
            try {
                themeDao.insert(theme);
                mainHandler.post(() -> callback.onSuccess(theme));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального обновления темы: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось обновить тему"));
                return;
            }

            // 2) В фоне обновляем на сервере
            apiService.adminUpdateTheme("eq." + theme.getId(), theme)
                    .enqueue(new Callback<List<Theme>>() {
                        @Override
                        public void onResponse(Call<List<Theme>> call, Response<List<Theme>> response) {
                            if (response.isSuccessful()
                                    && response.body() != null
                                    && !response.body().isEmpty()) {
                                Theme savedTheme = response.body().get(0);
                                executor.execute(() -> {
                                    try {
                                        themeDao.insert(savedTheme);
                                    } catch (Exception e) {
                                        Log.e(TAG,
                                                "Ошибка локальной перезаписи темы после ответа сервера: "
                                                        + e.getMessage(), e);
                                    }
                                });
                            } else {
                                Log.w(TAG, "Сервер не подтвердил обновление темы: "
                                        + response.code() + " | " + getErrorBody(response));
                            }
                        }

                        @Override
                        public void onFailure(Call<List<Theme>> call, Throwable t) {
                            Log.w(TAG, "Ошибка сети adminUpdateTheme: " + t.getMessage(), t);
                        }
                    });
        });
    }

    // ============================================================
    // УДАЛЕНИЕ ТЕМЫ (каскадно)
    // ============================================================
    public void adminDeleteTheme(Long themeId, DataCallback<Void> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (themeId == null) {
            callback.onError("Тема не найдена");
            return;
        }

        // 1) Мгновенно удаляем локально и отдаём UI
        executor.execute(() -> {
            final List<Word> localWords = new ArrayList<>();
            try {
                List<Word> wordsFromDb = wordDao.getWordsByTheme(themeId);
                if (wordsFromDb != null) localWords.addAll(wordsFromDb);
            } catch (Exception e) {
                Log.e(TAG, "Не удалось получить слова темы перед удалением: "
                        + e.getMessage(), e);
            }

            try {
                database.runInTransaction(() -> {
                    userWordDao.deleteUserWordsByThemeId(themeId);
                    wordDao.deleteWordsByThemeId(themeId);
                    themeDao.deleteThemeById(themeId);
                });
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального каскадного удаления темы: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось удалить тему"));
                return;
            }

            // 2) В фоне удаляем на сервере
            List<Long> wordIds = listener != null
                    ? listener.extractWordIds(localWords)
                    : new ArrayList<>();
            String idsFilter = listener != null
                    ? listener.buildIdsFilter(wordIds)
                    : "";

            deleteThemeCascadeRemote(themeId, idsFilter);
        });
    }

    // Серверное каскадное удаление: прогресс → слова → тема
    private void deleteThemeCascadeRemote(Long themeId, String wordIdsFilter) {
        apiService.adminDeleteProgressByWordIds(wordIdsFilter).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                deleteWordsByThemeRemote(themeId);
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "Ошибка удаления прогресса слов на сервере: " + t.getMessage());
                deleteWordsByThemeRemote(themeId);
            }
        });
    }

    private void deleteWordsByThemeRemote(Long themeId) {
        apiService.adminDeleteWordsByThemeId("eq." + themeId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                deleteThemeRemote(themeId);
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "Ошибка удаления слов темы на сервере: " + t.getMessage());
                deleteThemeRemote(themeId);
            }
        });
    }

    private void deleteThemeRemote(Long themeId) {
        apiService.adminDeleteTheme("eq." + themeId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "Сервер не подтвердил удаление темы: "
                            + response.code() + " | " + getErrorBody(response));
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "Ошибка удаления темы на сервере: " + t.getMessage());
            }
        });
    }

    // ============================================================
    // СОЗДАНИЕ СЛОВА
    // ============================================================
    public void adminCreateWord(Word word, DataCallback<Word> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        Word preparedWord = prepareAdminWord(word);

        if (preparedWord == null) {
            callback.onError("Заполните термин, перевод и тему");
            return;
        }

        // 1) Мгновенно сохраняем локально
        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    if (preparedWord.getId() == null || preparedWord.getId() <= 0) {
                        Long maxId = wordDao.getMaxWordId();
                        preparedWord.setId(maxId == null ? 1L : maxId + 1L);
                    }
                    wordDao.insert(preparedWord);
                });

                mainHandler.post(() -> callback.onSuccess(preparedWord));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального создания слова: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось создать термин"));
                return;
            }

            // 2) В фоне создаём на сервере
            apiService.adminCreateWord(preparedWord).enqueue(new Callback<List<Word>>() {
                @Override
                public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                    if (response.isSuccessful()
                            && response.body() != null
                            && !response.body().isEmpty()) {

                        Word savedWord = response.body().get(0);

                        executor.execute(() -> {
                            try {
                                if (savedWord.getId() != null
                                        && !savedWord.getId().equals(preparedWord.getId())) {
                                    database.runInTransaction(() -> {
                                        try {
                                            wordDao.deleteWordById(preparedWord.getId());
                                        } catch (Exception ignored) {}
                                        wordDao.insert(savedWord);
                                    });
                                } else {
                                    wordDao.insert(savedWord);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Не удалось синхронизировать созданное слово: "
                                        + e.getMessage(), e);
                            }
                        });
                    } else {
                        Log.w(TAG, "Сервер не создал слово: " + response.code()
                                + " | " + getErrorBody(response));
                    }
                }

                @Override
                public void onFailure(Call<List<Word>> call, Throwable t) {
                    Log.w(TAG, "Ошибка сети adminCreateWord: " + t.getMessage(), t);
                }
            });
        });
    }

    private Word prepareAdminWord(Word word) {
        if (word == null) return null;

        Long themeId = word.getThemeId();
        String term = normalizeText(word.getTerm());
        String translation = normalizeText(word.getTranslation());

        if (themeId == null || themeId <= 0 || term.isEmpty() || translation.isEmpty()) {
            return null;
        }

        word.setTerm(term);
        word.setTranslation(translation);
        word.setDefinition(normalizeText(word.getDefinition()));
        word.setTranscription(normalizeText(word.getTranscription()));
        word.setExampleSentence(normalizeText(word.getExampleSentence()));

        return word;
    }

    // ============================================================
    // ОБНОВЛЕНИЕ СЛОВА
    // ============================================================
    public void adminUpdateWord(Word word, DataCallback<Word> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (word == null || word.getId() == null) {
            callback.onError("Термин не найден");
            return;
        }

        // 1) Мгновенно сохраняем локально
        executor.execute(() -> {
            try {
                wordDao.insert(word);
                mainHandler.post(() -> callback.onSuccess(word));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального обновления слова: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось обновить термин"));
                return;
            }

            // 2) В фоне обновляем на сервере
            apiService.adminUpdateWord("eq." + word.getId(), word)
                    .enqueue(new Callback<List<Word>>() {
                        @Override
                        public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                            if (response.isSuccessful()
                                    && response.body() != null
                                    && !response.body().isEmpty()) {
                                Word savedWord = response.body().get(0);
                                executor.execute(() -> {
                                    try {
                                        wordDao.insert(savedWord);
                                    } catch (Exception e) {
                                        Log.e(TAG,
                                                "Ошибка локальной перезаписи слова после ответа сервера: "
                                                        + e.getMessage(), e);
                                    }
                                });
                            } else {
                                Log.w(TAG, "Сервер не подтвердил обновление слова: "
                                        + response.code() + " | " + getErrorBody(response));
                            }
                        }

                        @Override
                        public void onFailure(Call<List<Word>> call, Throwable t) {
                            Log.w(TAG, "Ошибка сети adminUpdateWord: " + t.getMessage(), t);
                        }
                    });
        });
    }

    // ============================================================
    // УДАЛЕНИЕ СЛОВА
    // ============================================================
    public void adminDeleteWord(Long wordId, DataCallback<Void> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (wordId == null) {
            callback.onError("Термин не найден");
            return;
        }

        // 1) Мгновенно удаляем локально
        executor.execute(() -> {
            Word localWord = null;
            try {
                localWord = wordDao.getWordById(wordId);
            } catch (Exception e) {
                Log.e(TAG, "Не удалось получить слово перед удалением: " + e.getMessage(), e);
            }

            final Word finalLocalWord = localWord;

            // Локально удаляем сразу
            try {
                if (listener != null) {
                    listener.getCurrentUserId(userId -> executor.execute(() -> {
                        try {
                            database.runInTransaction(() -> {
                                if (finalLocalWord != null
                                        && finalLocalWord.getTerm() != null
                                        && userId != null
                                        && userId != -1) {
                                    userWordDao.deleteUserWordsByTerm(userId, finalLocalWord.getTerm());
                                }
                                wordDao.deleteWordById(wordId);
                            });
                            mainHandler.post(() -> callback.onSuccess(null));
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка локального удаления слова: " + e.getMessage(), e);
                            mainHandler.post(() -> callback.onError("Не удалось удалить термин"));
                            return;
                        }

                        // 2) В фоне удаляем на сервере
                        deleteWordRemoteCascade(wordId);
                    }));
                } else {
                    database.runInTransaction(() -> wordDao.deleteWordById(wordId));
                    mainHandler.post(() -> callback.onSuccess(null));
                    deleteWordRemoteCascade(wordId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального удаления слова: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось удалить термин"));
            }
        });
    }

    private void deleteWordRemoteCascade(Long wordId) {
        apiService.adminDeleteProgressByWordId("eq." + wordId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                deleteWordRemote(wordId);
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "Ошибка удаления прогресса слова на сервере: " + t.getMessage());
                deleteWordRemote(wordId);
            }
        });
    }

    private void deleteWordRemote(Long wordId) {
        apiService.adminDeleteWord("eq." + wordId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "Сервер не подтвердил удаление слова: "
                            + response.code() + " | " + getErrorBody(response));
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "Ошибка удаления слова на сервере: " + t.getMessage());
            }
        });
    }

    // ============================================================
    // МАССОВОЕ СОЗДАНИЕ СЛОВ
    // ============================================================
    public void adminCreateWordsBulk(Long themeId, List<Word> words,
                                     DataCallback<List<Word>> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (themeId == null || themeId <= 0) {
            callback.onError("Выберите тему");
            return;
        }

        List<Word> preparedWords = prepareBulkWords(themeId, words);

        if (preparedWords.isEmpty()) {
            callback.onError("Добавьте хотя бы один корректный термин");
            return;
        }

        // 1) Мгновенно сохраняем все слова локально
        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    Long maxId = wordDao.getMaxWordId();
                    long nextId = maxId == null ? 1L : maxId + 1L;
                    for (Word word : preparedWords) {
                        if (word.getId() == null || word.getId() <= 0) {
                            word.setId(nextId++);
                        }
                    }
                    wordDao.insertAll(preparedWords);
                });

                mainHandler.post(() -> callback.onSuccess(preparedWords));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального массового создания слов: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось создать термины"));
                return;
            }

            // 2) В фоне отправляем на сервер
            apiService.adminCreateWords(preparedWords).enqueue(new Callback<List<Word>>() {
                @Override
                public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                    if (response.isSuccessful()
                            && response.body() != null
                            && !response.body().isEmpty()) {
                        List<Word> serverWords = response.body();
                        executor.execute(() -> {
                            try {
                                database.runInTransaction(() -> wordDao.insertAll(serverWords));
                            } catch (Exception e) {
                                Log.e(TAG,
                                        "Ошибка локальной перезаписи bulk-слов после ответа сервера: "
                                                + e.getMessage(), e);
                            }
                        });
                    } else {
                        Log.w(TAG, "Сервер не создал слова bulk: " + response.code()
                                + " | " + getErrorBody(response));
                    }
                }

                @Override
                public void onFailure(Call<List<Word>> call, Throwable t) {
                    Log.w(TAG, "Ошибка сети adminCreateWordsBulk: " + t.getMessage(), t);
                }
            });
        });
    }

    private List<Word> prepareBulkWords(Long themeId, List<Word> words) {
        List<Word> result = new ArrayList<>();
        if (words == null) return result;

        for (Word word : words) {
            if (word == null) continue;

            String term = normalizeText(word.getTerm());
            String translation = normalizeText(word.getTranslation());

            if (term.isEmpty() || translation.isEmpty()) continue;

            Word prepared = new Word();
            prepared.setId(word.getId());
            prepared.setThemeId(themeId);
            prepared.setTerm(term);
            prepared.setTranslation(translation);
            prepared.setDefinition(normalizeText(word.getDefinition()));
            prepared.setTranscription(normalizeText(word.getTranscription()));
            prepared.setExampleSentence(normalizeText(word.getExampleSentence()));

            result.add(prepared);
        }

        return result;
    }
}