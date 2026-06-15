package com.example.codeandwords.data.progress;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.model.UserWordProgress;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Репозиторий прогресса по словам: отслеживание правильных ответов, ошибок и статуса изученности
public class WordProgressRepository {

    private static final String TAG = "WordProgressRepository";

    // Лимиты выборки для тренировок
    private static final int MISTAKES_LIMIT = 15;
    private static final int LEARNED_TRAINING_LIMIT = 15;

    // Константы режимов обучения
    public static final String MODE_SPRINT = "SPRINT";
    public static final String MODE_MATCHING = "MATCHING";
    public static final String MODE_WRITING = "WRITING";

    private final ApiService apiService;
    private final Handler mainHandler;
    private WordProgressListener listener;

    public WordProgressRepository(ApiService apiService, Handler mainHandler) {
        this.apiService = apiService;
        this.mainHandler = mainHandler;
    }

    // Интерфейс для делегирования загрузки слов во ViewModel
    public interface WordProgressListener {
        void loadWordsByIds(List<Long> ids, DataCallback<List<Word>> callback);
        void getWordsByTheme(Long themeId, DataCallback<List<Word>> callback);
    }

    public void setListener(WordProgressListener listener) {
        this.listener = listener;
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    private int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    // Слово считается изученным только если пройдены все три режима обучения
    private boolean calculateIsLearned(UserWordProgress p) {
        if (p == null) return false;
        return Boolean.TRUE.equals(p.getPassedSprint())
                && Boolean.TRUE.equals(p.getPassedMatching())
                && Boolean.TRUE.equals(p.getPassedWriting());
    }

    // Публичные методы для пометки слова как пройденного в конкретном режиме
    public void markSprintPassed(Integer userId, Long wordId) {
        markModePassed(userId, wordId, MODE_SPRINT);
    }

    public void markMatchingPassed(Integer userId, Long wordId) {
        markModePassed(userId, wordId, MODE_MATCHING);
    }

    public void markWritingPassed(Integer userId, Long wordId) {
        markModePassed(userId, wordId, MODE_WRITING);
    }

    // Получает или создаёт запись прогресса, устанавливает флаг режима и пересчитывает is_learned
    private void markModePassed(Integer userId, Long wordId, String mode) {
        if (userId == null || wordId == null || mode == null) return;

        apiService.getUserWordProgress(
                        "eq." + userId,
                        "eq." + wordId,
                        "id,user_id,word_id,correct_answers_count,mistakes_count," +
                                "is_learned,passed_sprint,passed_matching,passed_writing"
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;

                        if (response.body().isEmpty()) {
                            // Записи нет — создаём новую
                            UserWordProgress prog = new UserWordProgress();
                            prog.setUserId(userId.longValue());
                            prog.setWordId(wordId);
                            prog.setCorrectAnswersCount(1);
                            prog.setMistakesCount(0);
                            applyModeFlag(prog, mode);
                            prog.setIsLearned(calculateIsLearned(prog));

                            if (Boolean.TRUE.equals(prog.getIsLearned())) {
                                synchronized (learnedIdsCache) {
                                    learnedIdsCache.add(wordId);
                                }
                                Log.d(TAG, "markModePassed: слово " + wordId
                                        + " добавлено в кэш выученных");
                            }

                            apiService.createWordProgress(prog).enqueue(noOpCallback());
                            return;
                        }

                        // Запись существует — обновляем
                        UserWordProgress existing = response.body().get(0);

                        int oldCorrect = safeInt(existing.getCorrectAnswersCount());
                        existing.setCorrectAnswersCount(oldCorrect + 1);

                        applyModeFlag(existing, mode);
                        existing.setIsLearned(calculateIsLearned(existing));

                        if (Boolean.TRUE.equals(existing.getIsLearned())) {
                            synchronized (learnedIdsCache) {
                                learnedIdsCache.add(wordId);
                            }
                            Log.d(TAG, "markModePassed: слово " + wordId
                                    + " добавлено в кэш выученных");
                        }

                        apiService.updateWordProgress("eq." + existing.getId(), existing)
                                .enqueue(noOpVoidCallback());
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        Log.e(TAG, "markModePassed failure: " + t.getMessage());
                    }
                });
    }

    // Устанавливает флаг соответствующего режима в записи прогресса
    private void applyModeFlag(UserWordProgress p, String mode) {
        switch (mode) {
            case MODE_SPRINT:
                p.setPassedSprint(true);
                break;
            case MODE_MATCHING:
                p.setPassedMatching(true);
                break;
            case MODE_WRITING:
                p.setPassedWriting(true);
                break;
        }
    }

    // Увеличивает счётчик правильных ответов без выставления флагов режимов.
    // Оставлен для обратной совместимости.
    public void incrementWordProgress(Integer userId, Long wordId) {
        if (userId == null || wordId == null) return;

        apiService.getUserWordProgress(
                        "eq." + userId,
                        "eq." + wordId,
                        "id,user_id,word_id,correct_answers_count,mistakes_count," +
                                "is_learned,passed_sprint,passed_matching,passed_writing"
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;

                        if (response.body().isEmpty()) {
                            UserWordProgress prog = new UserWordProgress();
                            prog.setUserId(userId.longValue());
                            prog.setWordId(wordId);
                            prog.setCorrectAnswersCount(1);
                            prog.setMistakesCount(0);
                            prog.setIsLearned(calculateIsLearned(prog));

                            apiService.createWordProgress(prog).enqueue(noOpCallback());
                            return;
                        }

                        UserWordProgress existing = response.body().get(0);
                        int oldCorrect = safeInt(existing.getCorrectAnswersCount());
                        existing.setCorrectAnswersCount(oldCorrect + 1);
                        existing.setIsLearned(calculateIsLearned(existing));

                        apiService.updateWordProgress("eq." + existing.getId(), existing)
                                .enqueue(noOpVoidCallback());
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        Log.e(TAG, "incrementWordProgress failure: " + t.getMessage());
                    }
                });
    }

    // Фиксирует ошибку пользователя по слову: создаёт или обновляет запись прогресса
    public void recordWordMistake(Word word, Integer userId) {
        if (word == null || userId == null || word.getId() == null) return;

        apiService.getUserWordProgress(
                        "eq." + userId,
                        "eq." + word.getId(),
                        "id,user_id,word_id,correct_answers_count,mistakes_count," +
                                "is_learned,passed_sprint,passed_matching,passed_writing"
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;

                        if (response.body().isEmpty()) {
                            UserWordProgress prog = new UserWordProgress();
                            prog.setUserId(userId.longValue());
                            prog.setWordId(word.getId());
                            prog.setCorrectAnswersCount(0);
                            prog.setMistakesCount(1);
                            prog.setIsLearned(calculateIsLearned(prog));

                            apiService.createWordProgress(prog).enqueue(noOpCallback());
                            return;
                        }

                        UserWordProgress existing = response.body().get(0);
                        int oldMistakes = safeInt(existing.getMistakesCount());
                        existing.setMistakesCount(oldMistakes + 1);
                        existing.setIsLearned(calculateIsLearned(existing));

                        apiService.updateWordProgress("eq." + existing.getId(), existing)
                                .enqueue(noOpVoidCallback());
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        Log.e(TAG, "recordWordMistake failure: " + t.getMessage());
                    }
                });
    }

    // Уменьшает счётчик ошибок и увеличивает счётчик правильных ответов.
    // Флаги режимов выставляются отдельно вызывающим кодом.
    public void resolveWordMistake(Word word, Integer userId, DataCallback<Void> callback) {
        if (userId == null || word == null || word.getId() == null) {
            if (callback != null) callback.onError("Некорректные параметры");
            return;
        }

        apiService.getUserWordProgress(
                        "eq." + userId,
                        "eq." + word.getId(),
                        "id,user_id,word_id,correct_answers_count,mistakes_count," +
                                "is_learned,passed_sprint,passed_matching,passed_writing"
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().isEmpty()) {

                            // Записи нет — создаём с одним правильным ответом
                            UserWordProgress newProg = new UserWordProgress();
                            newProg.setUserId(userId.longValue());
                            newProg.setWordId(word.getId());
                            newProg.setCorrectAnswersCount(1);
                            newProg.setMistakesCount(0);
                            newProg.setIsLearned(calculateIsLearned(newProg));

                            apiService.createWordProgress(newProg)
                                    .enqueue(new Callback<List<UserWordProgress>>() {
                                        @Override
                                        public void onResponse(Call<List<UserWordProgress>> c,
                                                               Response<List<UserWordProgress>> r) {
                                            if (callback != null)
                                                mainHandler.post(() -> callback.onSuccess(null));
                                        }

                                        @Override
                                        public void onFailure(Call<List<UserWordProgress>> c,
                                                              Throwable t) {
                                            if (callback != null)
                                                mainHandler.post(() -> callback.onError(t.getMessage()));
                                        }
                                    });
                            return;
                        }

                        UserWordProgress existing = response.body().get(0);
                        int oldCorrect = safeInt(existing.getCorrectAnswersCount());
                        int oldMistakes = safeInt(existing.getMistakesCount());

                        // Ошибки не могут уйти в отрицательные значения
                        existing.setMistakesCount(Math.max(0, oldMistakes - 1));
                        existing.setCorrectAnswersCount(oldCorrect + 1);
                        existing.setIsLearned(calculateIsLearned(existing));

                        apiService.updateWordProgress("eq." + existing.getId(), existing)
                                .enqueue(new Callback<Void>() {
                                    @Override
                                    public void onResponse(Call<Void> c, Response<Void> r) {
                                        if (callback != null)
                                            mainHandler.post(() -> callback.onSuccess(null));
                                    }

                                    @Override
                                    public void onFailure(Call<Void> c, Throwable t) {
                                        if (callback != null)
                                            mainHandler.post(() -> callback.onError(t.getMessage()));
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        Log.e(TAG, "resolveWordMistake failure: " + t.getMessage());
                        if (callback != null)
                            mainHandler.post(() -> callback.onError(t.getMessage()));
                    }
                });
    }

    // Универсальный метод обновления прогресса: используется там, где
    // флаги режимов не важны, а нужно просто увеличить счётчики
    public void upsertWordProgress(Long userId,
                                   Long wordId,
                                   boolean markLearned,
                                   boolean addMistake,
                                   DataCallback<Void> callback) {
        if (userId == null || wordId == null) {
            if (callback != null) callback.onError("Некорректные параметры");
            return;
        }

        apiService.getUserWordProgress(
                        "eq." + userId,
                        "eq." + wordId,
                        "id,user_id,word_id,correct_answers_count,mistakes_count," +
                                "is_learned,passed_sprint,passed_matching,passed_writing"
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && !response.body().isEmpty()) {

                            UserWordProgress existing = response.body().get(0);

                            int newCorrect = safeInt(existing.getCorrectAnswersCount())
                                    + (markLearned ? 1 : 0);
                            int newMistakes = safeInt(existing.getMistakesCount())
                                    + (addMistake ? 1 : 0);

                            existing.setCorrectAnswersCount(newCorrect);
                            existing.setMistakesCount(newMistakes);
                            existing.setIsLearned(calculateIsLearned(existing));

                            apiService.updateWordProgress("eq." + existing.getId(), existing)
                                    .enqueue(new Callback<Void>() {
                                        @Override
                                        public void onResponse(Call<Void> call,
                                                               Response<Void> response) {
                                            if (callback != null) callback.onSuccess(null);
                                        }

                                        @Override
                                        public void onFailure(Call<Void> call, Throwable t) {
                                            if (callback != null) callback.onError(t.getMessage());
                                        }
                                    });
                            return;
                        }

                        // Записи нет — создаём
                        UserWordProgress progress = new UserWordProgress();
                        progress.setUserId(userId);
                        progress.setWordId(wordId);
                        progress.setCorrectAnswersCount(markLearned ? 1 : 0);
                        progress.setMistakesCount(addMistake ? 1 : 0);
                        progress.setIsLearned(calculateIsLearned(progress));

                        apiService.createWordProgress(progress)
                                .enqueue(new Callback<List<UserWordProgress>>() {
                                    @Override
                                    public void onResponse(Call<List<UserWordProgress>> call,
                                                           Response<List<UserWordProgress>> response) {
                                        if (callback != null) callback.onSuccess(null);
                                    }

                                    @Override
                                    public void onFailure(Call<List<UserWordProgress>> call,
                                                          Throwable t) {
                                        if (callback != null) callback.onError(t.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        if (callback != null) callback.onError(t.getMessage());
                    }
                });
    }

    // Возвращает полный список изученных слов пользователя
    public void getLearnedWords(Integer userId, DataCallback<List<Word>> callback) {
        if (userId == null) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }

        apiService.getLearnedProgress(
                "eq." + userId,
                "eq.true",
                "word_id,is_learned",
                "id.desc",
                1000
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError("Не удалось загрузить выученные слова"));
                    return;
                }

                List<Long> ids = new ArrayList<>();
                for (UserWordProgress p : response.body()) {
                    if (p != null && p.getWordId() != null
                            && Boolean.TRUE.equals(p.getIsLearned())) {
                        ids.add(p.getWordId());
                    }
                }

                if (ids.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }

                if (listener != null) {
                    listener.loadWordsByIds(ids, callback);
                } else {
                    mainHandler.post(() -> callback.onError("listener не установлен"));
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
            }
        });
    }

    // Возвращает ограниченный список изученных слов для режима повторения
    public void getLearnedWordsForTraining(Integer userId, DataCallback<List<Word>> callback) {
        Log.d("WordProgressDiag", ">>> getLearnedWordsForTraining, userId=" + userId);

        if (userId == null) {
            Log.e("WordProgressDiag", "userId is null!");
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }

        apiService.getLearnedProgress(
                "eq." + userId,
                "eq.true",
                "word_id,is_learned",
                "id.desc",
                LEARNED_TRAINING_LIMIT
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                Log.d("WordProgressDiag", "getLearnedProgress response: code="
                        + response.code() + ", body="
                        + (response.body() == null ? "null" : response.body().size()));

                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError("Не удалось загрузить прогресс слов"));
                    return;
                }

                List<Long> ids = new ArrayList<>();
                for (UserWordProgress p : response.body()) {
                    if (p != null && p.getWordId() != null
                            && Boolean.TRUE.equals(p.getIsLearned())) {
                        ids.add(p.getWordId());
                    }
                }

                Log.d("WordProgressDiag", "Найдено learned ids: " + ids.size());

                if (ids.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }

                if (listener != null) {
                    Log.d("WordProgressDiag", "Вызываем listener.loadWordsByIds для " + ids.size() + " ids");
                    listener.loadWordsByIds(ids, callback);
                } else {
                    Log.e("WordProgressDiag", "listener == null!");
                    mainHandler.post(() -> callback.onError("listener не установлен"));
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                Log.e("WordProgressDiag", "getLearnedProgress FAILURE: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
            }
        });
    }

    // Возвращает слова с ошибками для работы над ошибками; при сбое сети повторяет запрос
    public void getMistakeWordsForTraining(Integer userId, DataCallback<List<Word>> callback) {
        if (userId == null) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }
        getMistakeWordsWithRetry(userId, callback, 2);
    }

    // Рекурсивный повтор запроса слов с ошибками при сетевом сбое
    private void getMistakeWordsWithRetry(Integer userId,
                                          DataCallback<List<Word>> callback,
                                          int attemptsLeft) {
        apiService.getMistakeProgress(
                "eq." + userId,
                "gt.0",
                "word_id,mistakes_count",
                "mistakes_count.desc",
                MISTAKES_LIMIT
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError(
                            "Не удалось загрузить ошибки (код: " + response.code() + ")"));
                    return;
                }

                List<Long> ids = new ArrayList<>();
                for (UserWordProgress p : response.body()) {
                    if (p != null && p.getWordId() != null
                            && safeInt(p.getMistakesCount()) > 0) {
                        ids.add(p.getWordId());
                    }
                }

                if (ids.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }

                if (listener != null) {
                    listener.loadWordsByIds(ids, callback);
                } else {
                    mainHandler.post(() -> callback.onError("listener не установлен"));
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                // При сбое повторяем запрос с задержкой; после исчерпания попыток — пустой список
                if (attemptsLeft > 0) {
                    mainHandler.postDelayed(() ->
                                    getMistakeWordsWithRetry(userId, callback, attemptsLeft - 1),
                            1500);
                } else {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                }
            }
        });
    }

    // In-memory кэш ID изученных слов с TTL 60 секунд
    private final Set<Long> learnedIdsCache = new HashSet<>();
    private volatile boolean learnedIdsCacheLoaded = false;
    private volatile long learnedIdsCacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 60_000;

    // Возвращает неизученные слова темы: сначала из кэша, затем обновляет с сервера
    public void getUnlearnedWordsByTheme(Long themeId,
                                         Integer userId,
                                         DataCallback<List<Word>> callback) {
        if (userId == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }
        if (listener == null) {
            callback.onError("Listener не установлен");
            return;
        }

        // Шаг 1: загружаем слова темы из локальной БД
        listener.getWordsByTheme(themeId, new DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                long age = System.currentTimeMillis() - learnedIdsCacheTimestamp;

                // Шаг 2: если кэш актуален — используем немедленно и обновляем в фоне
                if (learnedIdsCacheLoaded && age < CACHE_TTL_MS) {
                    Log.d(TAG, "getUnlearnedWordsByTheme: используем кэш ("
                            + learnedIdsCache.size() + " выученных)");
                    Set<Long> cacheCopy;
                    synchronized (learnedIdsCache) {
                        cacheCopy = new HashSet<>(learnedIdsCache);
                    }
                    List<Word> result = buildUnlearnedList(words, cacheCopy);
                    mainHandler.post(() -> callback.onSuccess(result));

                    refreshLearnedIdsCache(userId, null);
                    return;
                }

                // Шаг 3: кэш устарел или отсутствует — загружаем с сервера
                refreshLearnedIdsCache(userId, freshIds -> {
                    Set<Long> idsToUse = freshIds != null ? freshIds : new HashSet<>();
                    List<Word> result = buildUnlearnedList(words, idsToUse);
                    mainHandler.post(() -> callback.onSuccess(result));
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Обновляет кэш ID изученных слов с сервера; вызывает callback с результатом если он задан
    private void refreshLearnedIdsCache(Integer userId,
                                        OnLearnedIdsLoaded callback) {
        apiService.getLearnedProgress(
                "eq." + userId,
                "eq.true",
                "word_id,is_learned",
                "id.asc",
                1000
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                Set<Long> ids = new HashSet<>();
                if (response.isSuccessful() && response.body() != null) {
                    for (UserWordProgress p : response.body()) {
                        if (p != null && p.getWordId() != null
                                && Boolean.TRUE.equals(p.getIsLearned())) {
                            ids.add(p.getWordId());
                        }
                    }
                }

                synchronized (learnedIdsCache) {
                    learnedIdsCache.clear();
                    learnedIdsCache.addAll(ids);
                    learnedIdsCacheLoaded = true;
                    learnedIdsCacheTimestamp = System.currentTimeMillis();
                }

                Log.d(TAG, "refreshLearnedIdsCache: загружено " + ids.size() + " ID");

                if (callback != null) {
                    callback.onLoaded(ids);
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                Log.e(TAG, "refreshLearnedIdsCache failure: " + t.getMessage());
                // При ошибке сети возвращаем то, что есть в кэше
                if (callback != null) {
                    Set<Long> cacheCopy;
                    synchronized (learnedIdsCache) {
                        cacheCopy = new HashSet<>(learnedIdsCache);
                    }
                    callback.onLoaded(cacheCopy);
                }
            }
        });
    }

    private interface OnLearnedIdsLoaded {
        void onLoaded(Set<Long> ids);
    }

    // Прогрев кэша при старте приложения для мгновенного первого входа в игру
    public void warmupLearnedIdsCache(Integer userId) {
        if (userId == null || userId <= 0) return;
        Log.d(TAG, "warmupLearnedIdsCache: прогреваем кэш для userId=" + userId);
        refreshLearnedIdsCache(userId, null);
    }

    // Сброс кэша при выходе из аккаунта
    public void clearLearnedIdsCache() {
        synchronized (learnedIdsCache) {
            learnedIdsCache.clear();
            learnedIdsCacheLoaded = false;
            learnedIdsCacheTimestamp = 0;
        }
    }

    // Фильтрует список слов, исключая уже изученные
    private List<Word> buildUnlearnedList(List<Word> words, Set<Long> learnedIds) {
        List<Word> result = new ArrayList<>();
        if (words == null) return result;

        for (Word w : words) {
            if (w == null || w.getId() == null) continue;
            String term = w.getTerm() == null ? "" : w.getTerm().trim();
            String translation = w.getTranslation() == null ? "" : w.getTranslation().trim();
            if (term.isEmpty() || translation.isEmpty()) continue;
            if (!learnedIds.contains(w.getId())) {
                result.add(w);
            }
        }
        return result;
    }

    // Помечает список слов как изученные через incrementWordProgress
    public void markWordsAsLearned(List<Word> words,
                                   Integer userId,
                                   DataCallback<Void> callback) {
        if (userId == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }
        if (words == null || words.isEmpty()) {
            callback.onSuccess(null);
            return;
        }

        final int[] completed = {0};
        for (Word word : words) {
            if (word == null || word.getId() == null) {
                completed[0]++;
                if (completed[0] >= words.size()) callback.onSuccess(null);
                continue;
            }

            incrementWordProgress(userId, word.getId());
            completed[0]++;
            if (completed[0] >= words.size()) callback.onSuccess(null);
        }
    }

    // Возвращает общее количество изученных слов пользователя
    public void getLearnedWordsCount(Integer userId, DataCallback<Integer> callback) {
        if (userId == null || userId <= 0) {
            callback.onError("Некорректный пользователь");
            return;
        }

        apiService.getLearnedProgress(
                "eq." + userId,
                "eq.true",
                "word_id,is_learned",
                "id.asc",
                1000
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError("Не удалось загрузить прогресс"));
                    return;
                }

                int count = 0;
                for (UserWordProgress p : response.body()) {
                    if (p != null && Boolean.TRUE.equals(p.getIsLearned())) {
                        count++;
                    }
                }

                int finalCount = count;
                mainHandler.post(() -> callback.onSuccess(finalCount));
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
            }
        });
    }

    // Пустой callback для запросов, результат которых не нужен вызывающему коду
    private Callback<List<UserWordProgress>> noOpCallback() {
        return new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) { }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                Log.e(TAG, "noOpCallback failure: " + t.getMessage());
            }
        };
    }

    // Пустой Void-callback для запросов без возвращаемого значения
    private Callback<Void> noOpVoidCallback() {
        return new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) { }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "noOpVoidCallback failure: " + t.getMessage());
            }
        };
    }
}