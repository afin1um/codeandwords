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

public class WordProgressRepository {

    private static final String TAG = "WordProgressRepository";

    // Слово считается выученным после 3 правильных ответов без ошибок в истории
    private static final int REQUIRED_CORRECT = 3;

    private static final int MISTAKES_LIMIT = 15;
    private static final int LEARNED_TRAINING_LIMIT = 15;
    private static final int LEARNED_LIST_LIMIT = 80;

    private final ApiService apiService;
    private final Handler mainHandler;
    private WordProgressListener listener;

    public WordProgressRepository(ApiService apiService, Handler mainHandler) {
        this.apiService = apiService;
        this.mainHandler = mainHandler;
    }

    // ===== ИНТЕРФЕЙС СЛУШАТЕЛЯ =====

    public interface WordProgressListener {
        void loadWordsByIds(List<Long> ids, DataCallback<List<Word>> callback);
        void getWordsByTheme(Long themeId, DataCallback<List<Word>> callback);
    }

    public void setListener(WordProgressListener listener) {
        this.listener = listener;
    }

    // ===== ЕДИНСТВЕННОЕ ОБЪЯВЛЕНИЕ CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    private boolean calculateIsLearned(int correct, int mistakes) {
        return correct >= REQUIRED_CORRECT && mistakes == 0;
    }

    // ======================================================================
    // INCREMENT WORD PROGRESS — вызывается при ПРАВИЛЬНОМ ответе
    // ======================================================================

    public void incrementWordProgress(Integer userId, Long wordId) {
        if (userId == null || wordId == null) return;

        apiService.getUserWordProgress(
                        "eq." + userId,
                        "eq." + wordId,
                        "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
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
                            prog.setIsLearned(false);

                            apiService.createWordProgress(prog).enqueue(noOpCallback());
                            return;
                        }

                        UserWordProgress existing = response.body().get(0);

                        int oldCorrect = safeInt(existing.getCorrectAnswersCount());
                        int oldMistakes = safeInt(existing.getMistakesCount());

                        int newCorrect = oldCorrect + 1;
                        existing.setCorrectAnswersCount(newCorrect);

                        existing.setIsLearned(calculateIsLearned(newCorrect, oldMistakes));

                        apiService.updateWordProgress("eq." + existing.getId(), existing)
                                .enqueue(noOpVoidCallback());
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        Log.e(TAG, "incrementWordProgress failure: " + t.getMessage());
                    }
                });
    }

    // ======================================================================
    // RECORD WORD MISTAKE — вызывается при ОШИБКЕ
    // ======================================================================

    public void recordWordMistake(Word word, Integer userId) {
        if (word == null || userId == null || word.getId() == null) return;

        apiService.getUserWordProgress(
                        "eq." + userId,
                        "eq." + word.getId(),
                        "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
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
                            prog.setIsLearned(false);

                            apiService.createWordProgress(prog).enqueue(noOpCallback());
                            return;
                        }

                        UserWordProgress existing = response.body().get(0);

                        int oldCorrect = safeInt(existing.getCorrectAnswersCount());
                        int oldMistakes = safeInt(existing.getMistakesCount());

                        int newMistakes = oldMistakes + 1;
                        existing.setMistakesCount(newMistakes);

                        existing.setIsLearned(calculateIsLearned(oldCorrect, newMistakes));

                        apiService.updateWordProgress("eq." + existing.getId(), existing)
                                .enqueue(noOpVoidCallback());
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        Log.e(TAG, "recordWordMistake failure: " + t.getMessage());
                    }
                });
    }

    // ======================================================================
    // RESOLVE WORD MISTAKE — вызывается при ИСПРАВЛЕНИИ ошибки
    // ======================================================================

    public void resolveWordMistake(Word word, Integer userId, DataCallback<Void> callback) {
        if (userId == null || word == null || word.getId() == null) {
            if (callback != null) callback.onError("Некорректные параметры");
            return;
        }

        apiService.getUserWordProgress(
                        "eq." + userId,
                        "eq." + word.getId(),
                        "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().isEmpty()) {

                            UserWordProgress newProg = new UserWordProgress();
                            newProg.setUserId(userId.longValue());
                            newProg.setWordId(word.getId());
                            newProg.setCorrectAnswersCount(1);
                            newProg.setMistakesCount(0);
                            newProg.setIsLearned(false);

                            apiService.createWordProgress(newProg)
                                    .enqueue(new Callback<List<UserWordProgress>>() {
                                        @Override
                                        public void onResponse(Call<List<UserWordProgress>> c,
                                                               Response<List<UserWordProgress>> r) {
                                            if (callback != null) mainHandler.post(() -> callback.onSuccess(null));
                                        }

                                        @Override
                                        public void onFailure(Call<List<UserWordProgress>> c, Throwable t) {
                                            if (callback != null) mainHandler.post(() -> callback.onError(t.getMessage()));
                                        }
                                    });
                            return;
                        }

                        UserWordProgress existing = response.body().get(0);

                        int oldCorrect = safeInt(existing.getCorrectAnswersCount());
                        int oldMistakes = safeInt(existing.getMistakesCount());

                        int newMistakes = Math.max(0, oldMistakes - 1);
                        int newCorrect = oldCorrect + 1;

                        existing.setMistakesCount(newMistakes);
                        existing.setCorrectAnswersCount(newCorrect);

                        existing.setIsLearned(calculateIsLearned(newCorrect, newMistakes));

                        apiService.updateWordProgress("eq." + existing.getId(), existing)
                                .enqueue(new Callback<Void>() {
                                    @Override
                                    public void onResponse(Call<Void> c, Response<Void> r) {
                                        if (callback != null) mainHandler.post(() -> callback.onSuccess(null));
                                    }

                                    @Override
                                    public void onFailure(Call<Void> c, Throwable t) {
                                        if (callback != null) mainHandler.post(() -> callback.onError(t.getMessage()));
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        Log.e(TAG, "resolveWordMistake failure: " + t.getMessage());
                        if (callback != null) mainHandler.post(() -> callback.onError(t.getMessage()));
                    }
                });
    }

    // ======================================================================
    // UPSERT WORD PROGRESS — Обобщенный метод (требуется для компиляции Repository)
    // ======================================================================

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
                        "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
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

                            existing.setIsLearned(calculateIsLearned(newCorrect, newMistakes));

                            apiService.updateWordProgress("eq." + existing.getId(), existing)
                                    .enqueue(new Callback<Void>() {
                                        @Override
                                        public void onResponse(Call<Void> call, Response<Void> response) {
                                            if (callback != null) callback.onSuccess(null);
                                        }

                                        @Override
                                        public void onFailure(Call<Void> call, Throwable t) {
                                            if (callback != null) callback.onError(t.getMessage());
                                        }
                                    });
                            return;
                        }

                        UserWordProgress progress = new UserWordProgress();
                        progress.setUserId(userId);
                        progress.setWordId(wordId);
                        progress.setCorrectAnswersCount(markLearned ? 1 : 0);
                        progress.setMistakesCount(addMistake ? 1 : 0);
                        progress.setIsLearned(calculateIsLearned(markLearned ? 1 : 0, addMistake ? 1 : 0));

                        apiService.createWordProgress(progress)
                                .enqueue(new Callback<List<UserWordProgress>>() {
                                    @Override
                                    public void onResponse(Call<List<UserWordProgress>> call,
                                                           Response<List<UserWordProgress>> response) {
                                        if (callback != null) callback.onSuccess(null);
                                    }

                                    @Override
                                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
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

    // ======================================================================
    // ЗАГРУЗКА ОБУЧЕННЫХ СЛОВ (для экрана «Выученные слова»)
    // ======================================================================

    public void getLearnedWords(Integer userId, DataCallback<List<Word>> callback) {
        if (userId == null) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }

        apiService.getUserProgressByUser(
                        "eq." + userId,
                        "word_id,is_learned",
                        "id.desc",
                        LEARNED_LIST_LIMIT
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            mainHandler.post(() -> callback.onError("Не удалось загрузить слова"));
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
                            mainHandler.post(() -> callback.onError("Listener не установлен"));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
                    }
                });
    }

    // ======================================================================
    // ЗАГРУЗКА ДЛЯ ТРЕНИРОВКИ ВЫУЧЕННЫХ СЛОВ
    // ======================================================================

    public void getLearnedWordsForTraining(Integer userId, DataCallback<List<Word>> callback) {
        if (userId == null) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }

        apiService.getUserProgressByUser(
                        "eq." + userId,
                        "word_id,is_learned",
                        "id.desc",
                        LEARNED_TRAINING_LIMIT
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
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

                        if (ids.isEmpty()) {
                            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                            return;
                        }

                        if (listener != null) {
                            listener.loadWordsByIds(ids, callback);
                        } else {
                            mainHandler.post(() -> callback.onError("Listener не установлен"));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
                    }
                });
    }

    // ======================================================================
    // ЗАГРУЗКА СЛОВ С ОШИБКАМИ
    // ======================================================================

    public void getMistakeWordsForTraining(Integer userId, DataCallback<List<Word>> callback) {
        if (userId == null) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }

        apiService.getUserProgressByUser(
                        "eq." + userId,
                        "word_id,mistakes_count",
                        "mistakes_count.desc",
                        MISTAKES_LIMIT
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            mainHandler.post(() -> callback.onError("Не удалось загрузить ошибки"));
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
                            mainHandler.post(() -> callback.onError("Listener не установлен"));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
                    }
                });
    }

    // ======================================================================
    // ОСТАЛЬНЫЕ МЕТОДЫ СИНХРОНИЗАЦИИ И РАСЧЕТОВ
    // ======================================================================

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

        listener.getWordsByTheme(themeId, new DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                apiService.getUserProgressByUser(
                                "eq." + userId,
                                "word_id,is_learned",
                                "id.asc",
                                1000
                        )
                        .enqueue(new Callback<List<UserWordProgress>>() {
                            @Override
                            public void onResponse(Call<List<UserWordProgress>> call,
                                                   Response<List<UserWordProgress>> response) {
                                Set<Long> learnedSet = new HashSet<>();
                                if (response.isSuccessful() && response.body() != null) {
                                    for (UserWordProgress p : response.body()) {
                                        if (p != null && p.getWordId() != null
                                                && Boolean.TRUE.equals(p.getIsLearned())) {
                                            learnedSet.add(p.getWordId());
                                        }
                                    }
                                }

                                List<Word> result = buildUnlearnedList(words, learnedSet);
                                mainHandler.post(() -> callback.onSuccess(result));
                            }

                            @Override
                            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                                List<Word> fallback = buildUnlearnedList(words, new HashSet<>());
                                mainHandler.post(() -> callback.onSuccess(fallback));
                            }
                        });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

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

    public void getLearnedWordsCount(Integer userId, DataCallback<Integer> callback) {
        if (userId == null || userId <= 0) {
            callback.onError("Некорректный пользователь");
            return;
        }

        apiService.getUserProgressByUser(
                        "eq." + userId,
                        "word_id,is_learned",
                        "id.asc",
                        1000
                )
                .enqueue(new Callback<List<UserWordProgress>>() {
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

    // ===== УТИЛИТАРНЫЕ CALLBACK'И =====

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