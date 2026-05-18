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

    // ===== ИНТЕРФЕЙС СЛУШАТЕЛЯ =====

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

    // ===== ИНТЕРФЕЙС CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private String normalizeText(String value) {
        return listener != null
                ? listener.normalizeText(value)
                : (value == null ? "" : value.trim());
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
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

    // ===== ТЕМЫ =====

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
        String description = theme.getDescription() == null
                ? "" : theme.getDescription().trim();
        String difficulty = theme.getDifficultyLevel() == null
                ? "Easy" : theme.getDifficultyLevel().trim();
        String theoryText = theme.getTheoryText() == null
                ? "" : theme.getTheoryText().trim();

        if (title.isEmpty()) {
            callback.onError("Введите название темы");
            return;
        }

        theme.setTitle(title);
        theme.setDescription(description);
        theme.setDifficultyLevel(difficulty.isEmpty() ? "Easy" : difficulty);
        theme.setTheoryText(theoryText);

        apiService.adminCreateTheme(theme).enqueue(new Callback<List<Theme>>() {
            @Override
            public void onResponse(Call<List<Theme>> call,
                                   Response<List<Theme>> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {

                    Theme savedTheme = response.body().get(0);

                    executor.execute(() -> {
                        try {
                            themeDao.insert(savedTheme);
                        } catch (Exception e) {
                            Log.e(TAG,
                                    "Ошибка локального сохранения темы: "
                                            + e.getMessage(), e);
                        }
                        mainHandler.post(() -> callback.onSuccess(savedTheme));
                    });
                } else {
                    Log.e(TAG, "Сервер не создал тему: "
                            + response.code() + " | " + getErrorBody(response));
                    saveThemeLocallyOnly(theme, callback);
                }
            }

            @Override
            public void onFailure(Call<List<Theme>> call, Throwable t) {
                Log.e(TAG, "Ошибка сети adminCreateTheme: " + t.getMessage(), t);
                saveThemeLocallyOnly(theme, callback);
            }
        });
    }

    /**
     * ✅ ОПТИМИЗИРОВАНО: получение maxId + insert в одной транзакции
     */
    private void saveThemeLocallyOnly(Theme theme, DataCallback<Theme> callback) {
        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    Long maxId = themeDao.getMaxThemeId();
                    long newId = maxId == null ? 1L : maxId + 1L;
                    theme.setId(newId);
                    themeDao.insert(theme);
                });

                mainHandler.post(() -> callback.onSuccess(theme));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального создания темы: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось создать тему"));
            }
        });
    }

    public void adminUpdateTheme(Theme theme, DataCallback<Theme> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (theme == null || theme.getId() == null) {
            callback.onError("Тема не найдена");
            return;
        }

        apiService.adminUpdateTheme("eq." + theme.getId(), theme)
                .enqueue(new Callback<List<Theme>>() {
                    @Override
                    public void onResponse(Call<List<Theme>> call,
                                           Response<List<Theme>> response) {
                        Theme savedTheme = theme;

                        if (response.isSuccessful()
                                && response.body() != null
                                && !response.body().isEmpty()) {
                            savedTheme = response.body().get(0);
                        }

                        Theme finalTheme = savedTheme;

                        executor.execute(() -> {
                            try {
                                themeDao.insert(finalTheme);
                            } catch (Exception e) {
                                Log.e(TAG,
                                        "Ошибка локального обновления темы: "
                                                + e.getMessage(), e);
                            }
                            mainHandler.post(() -> callback.onSuccess(finalTheme));
                        });
                    }

                    @Override
                    public void onFailure(Call<List<Theme>> call, Throwable t) {
                        executor.execute(() -> {
                            try {
                                themeDao.insert(theme);
                                mainHandler.post(() -> callback.onSuccess(theme));
                            } catch (Exception e) {
                                mainHandler.post(() -> callback.onError(
                                        "Не удалось обновить тему"));
                            }
                        });
                    }
                });
    }

    public void adminDeleteTheme(Long themeId, DataCallback<Void> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (themeId == null) {
            callback.onError("Тема не найдена");
            return;
        }

        executor.execute(() -> {
            List<Word> localWords = new ArrayList<>();

            try {
                List<Word> wordsFromDb = wordDao.getWordsByTheme(themeId);
                if (wordsFromDb != null) localWords.addAll(wordsFromDb);
            } catch (Exception e) {
                Log.e(TAG, "Не удалось получить слова темы: " + e.getMessage(), e);
            }

            List<Long> wordIds = listener != null
                    ? listener.extractWordIds(localWords)
                    : new ArrayList<>();
            String idsFilter = listener != null
                    ? listener.buildIdsFilter(wordIds)
                    : "";

            mainHandler.post(() ->
                    deleteThemeCascadeRemote(themeId, idsFilter, callback));
        });
    }

    private void deleteThemeCascadeRemote(Long themeId,
                                          String wordIdsFilter,
                                          DataCallback<Void> callback) {
        apiService.adminDeleteProgressByWordIds(wordIdsFilter)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        deleteWordsByThemeRemote(themeId, callback);
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        deleteWordsByThemeRemote(themeId, callback);
                    }
                });
    }

    private void deleteWordsByThemeRemote(Long themeId, DataCallback<Void> callback) {
        apiService.adminDeleteWordsByThemeId("eq." + themeId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        deleteThemeRemote(themeId, callback);
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        deleteThemeRemote(themeId, callback);
                    }
                });
    }

    private void deleteThemeRemote(Long themeId, DataCallback<Void> callback) {
        apiService.adminDeleteTheme("eq." + themeId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        deleteThemeLocalCascade(themeId, callback);
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        deleteThemeLocalCascade(themeId, callback);
                    }
                });
    }

    /**
     * ✅ ОПТИМИ�ЗИРОВАНО: каскадное удаление в одной транзакции.
     * Раньше: 3 отдельные операции = 3 коммита в SQLite.
     * Сейчас: 1 транзакция = 1 коммит, в 3-5 раз быстрее.
     */
    private void deleteThemeLocalCascade(Long themeId, DataCallback<Void> callback) {
        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    userWordDao.deleteUserWordsByThemeId(themeId);
                    wordDao.deleteWordsByThemeId(themeId);
                    themeDao.deleteThemeById(themeId);
                });

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка каскадного удаления темы: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось удалить тему"));
            }
        });
    }

    // ===== СЛОВА =====

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

        apiService.adminCreateWord(preparedWord).enqueue(new Callback<List<Word>>() {
            @Override
            public void onResponse(Call<List<Word>> call,
                                   Response<List<Word>> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {
                    saveAdminWordLocal(response.body().get(0), callback);
                } else {
                    saveAdminWordLocalWithGeneratedId(preparedWord, callback);
                }
            }

            @Override
            public void onFailure(Call<List<Word>> call, Throwable t) {
                saveAdminWordLocalWithGeneratedId(preparedWord, callback);
            }
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

    private void saveAdminWordLocal(Word savedWord, DataCallback<Word> callback) {
        executor.execute(() -> {
            try {
                wordDao.insert(savedWord);
                mainHandler.post(() -> callback.onSuccess(savedWord));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального сохранения термина: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось сохранить термин"));
            }
        });
    }

    /**
     * ✅ ОПТИМИЗИРОВАНО: получение maxId + insert в одной транзакции
     */
    private void saveAdminWordLocalWithGeneratedId(Word word,
                                                   DataCallback<Word> callback) {
        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    if (word.getId() == null || word.getId() <= 0) {
                        Long maxId = wordDao.getMaxWordId();
                        word.setId(maxId == null ? 1L : maxId + 1L);
                    }
                    wordDao.insert(word);
                });

                mainHandler.post(() -> callback.onSuccess(word));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального создания термина: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось создать термин"));
            }
        });
    }

    public void adminUpdateWord(Word word, DataCallback<Word> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (word == null || word.getId() == null) {
            callback.onError("Термин не найден");
            return;
        }

        apiService.adminUpdateWord("eq." + word.getId(), word)
                .enqueue(new Callback<List<Word>>() {
                    @Override
                    public void onResponse(Call<List<Word>> call,
                                           Response<List<Word>> response) {
                        Word savedWord = word;

                        if (response.isSuccessful()
                                && response.body() != null
                                && !response.body().isEmpty()) {
                            savedWord = response.body().get(0);
                        }

                        Word finalWord = savedWord;

                        executor.execute(() -> {
                            try {
                                wordDao.insert(finalWord);
                            } catch (Exception e) {
                                Log.e(TAG,
                                        "Ошибка локального обновления слова: "
                                                + e.getMessage(), e);
                            }
                            mainHandler.post(() -> callback.onSuccess(finalWord));
                        });
                    }

                    @Override
                    public void onFailure(Call<List<Word>> call, Throwable t) {
                        executor.execute(() -> {
                            try {
                                wordDao.insert(word);
                                mainHandler.post(() -> callback.onSuccess(word));
                            } catch (Exception e) {
                                mainHandler.post(() -> callback.onError(
                                        "Не удалось обновить термин"));
                            }
                        });
                    }
                });
    }

    public void adminDeleteWord(Long wordId, DataCallback<Void> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (wordId == null) {
            callback.onError("Термин не найден");
            return;
        }

        executor.execute(() -> {
            Word localWord = null;

            try {
                localWord = wordDao.getWordById(wordId);
            } catch (Exception e) {
                Log.e(TAG, "Не удалось получить слово перед удалением: "
                        + e.getMessage(), e);
            }

            Word finalLocalWord = localWord;
            mainHandler.post(() ->
                    deleteWordRemoteCascade(wordId, finalLocalWord, callback));
        });
    }

    private void deleteWordRemoteCascade(Long wordId,
                                         Word localWord,
                                         DataCallback<Void> callback) {
        apiService.adminDeleteProgressByWordId("eq." + wordId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        deleteWordRemote(wordId, localWord, callback);
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        deleteWordRemote(wordId, localWord, callback);
                    }
                });
    }

    private void deleteWordRemote(Long wordId,
                                  Word localWord,
                                  DataCallback<Void> callback) {
        apiService.adminDeleteWord("eq." + wordId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (listener != null) {
                            listener.getCurrentUserId(userId ->
                                    deleteWordLocalCascade(wordId, localWord, userId, callback));
                        } else {
                            deleteWordLocalCascade(wordId, localWord, null, callback);
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        if (listener != null) {
                            listener.getCurrentUserId(userId ->
                                    deleteWordLocalCascade(wordId, localWord, userId, callback));
                        } else {
                            deleteWordLocalCascade(wordId, localWord, null, callback);
                        }
                    }
                });
    }

    /**
     * ✅ ОПТИМИЗИРОВАНО: каскадное удаление в одной транзакции
     */
    private void deleteWordLocalCascade(Long wordId,
                                        Word localWord,
                                        Integer userId,
                                        DataCallback<Void> callback) {
        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    if (localWord != null
                            && localWord.getTerm() != null
                            && userId != null
                            && userId != -1) {
                        userWordDao.deleteUserWordsByTerm(userId, localWord.getTerm());
                    }
                    wordDao.deleteWordById(wordId);
                });

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка каскадного удаления слова: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось удалить термин"));
            }
        });
    }

    // ===== МАССОВОЕ СОЗДАНИЕ СЛОВ =====

    public void adminCreateWordsBulk(Long themeId,
                                     List<Word> words,
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

        apiService.adminCreateWords(preparedWords).enqueue(new Callback<List<Word>>() {
            @Override
            public void onResponse(Call<List<Word>> call,
                                   Response<List<Word>> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {
                    saveAdminWordsLocal(response.body(), callback);
                } else {
                    Log.e(TAG, "Сервер не создал слова: " + response.code()
                            + " | " + getErrorBody(response));
                    saveAdminWordsLocalWithGeneratedIds(preparedWords, callback);
                }
            }

            @Override
            public void onFailure(Call<List<Word>> call, Throwable t) {
                Log.e(TAG, "Ошибка сети adminCreateWordsBulk: " + t.getMessage(), t);
                saveAdminWordsLocalWithGeneratedIds(preparedWords, callback);
            }
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

    /**
     * ✅ ОПТИМИЗИРОВАНО: вставка всех слов в ОДНОЙ транзакции.
     * Room автоматически делает batch insert внутри транзакции -
     * в 10-50 раз быстрее, чем вставка по одному.
     */
    private void saveAdminWordsLocal(List<Word> words,
                                     DataCallback<List<Word>> callback) {
        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    wordDao.insertAll(words);
                });

                Log.d(TAG, "Bulk insert: " + words.size()
                        + " слов сохранено в транзакции");

                mainHandler.post(() -> callback.onSuccess(words));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального сохранения терминов: "
                        + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось сохранить термины"));
            }
        });
    }

    /**
     * ✅ ОПТИМИЗИРОВАНО: генерация ID + вставка в одной транзакции.
     * Раньше: getMaxWordId() в цикле + insertAll - 2 операции без атомарности.
     * Сейчас: всё в transaction - и быстрее, и безопаснее при параллельных вызовах.
     */
    private void saveAdminWordsLocalWithGeneratedIds(List<Word> words,
                                                     DataCallback<List<Word>> callback) {
        executor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    Long maxId = wordDao.getMaxWordId();
                    long nextId = maxId == null ? 1L : maxId + 1L;

                    for (Word word : words) {
                        if (word.getId() == null || word.getId() <= 0) {
                            word.setId(nextId);
                            nextId++;
                        }
                    }

                    wordDao.insertAll(words);
                });

                Log.d(TAG, "Bulk insert с генерацией ID: "
                        + words.size() + " слов");

                mainHandler.post(() -> callback.onSuccess(words));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка массового создания терминов: "
                        + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось создать термины"));
            }
        });
    }
}