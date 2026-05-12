package com.example.codeandwords.data.progress;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.model.UserWordProgress;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WordProgressRepository {

    private final ApiService apiService;
    private final Handler mainHandler;

    private WordProgressListener listener;

    public WordProgressRepository(ApiService apiService,
                                  Handler mainHandler) {
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

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e("WordProgressRepository",
                    "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // ===== ОСНОВНЫЕ МЕТОДЫ =====

    public void incrementWordProgress(Integer userId, Long wordId) {
        String filter = "user_id=eq." + userId + "&word_id=eq." + wordId;

        apiService.getUserProgress(filter).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isEmpty()) {
                        UserWordProgress newProg = new UserWordProgress();
                        newProg.setUserId(userId.longValue());
                        newProg.setWordId(wordId);
                        newProg.setCorrectAnswersCount(1);
                        newProg.setIsLearned(false);

                        apiService.createWordProgress(newProg)
                                .enqueue(new Callback<List<UserWordProgress>>() {
                                    @Override
                                    public void onResponse(
                                            Call<List<UserWordProgress>> call,
                                            Response<List<UserWordProgress>> r) {
                                    }

                                    @Override
                                    public void onFailure(
                                            Call<List<UserWordProgress>> call,
                                            Throwable t) {
                                        Log.e("WordProgressRepository",
                                                "Ошибка создания прогресса: "
                                                        + t.getMessage(), t);
                                    }
                                });
                    } else {
                        UserWordProgress existing = response.body().get(0);
                        int newCount = existing.getCorrectAnswersCount() + 1;
                        existing.setCorrectAnswersCount(newCount);

                        if (newCount >= 3) {
                            existing.setIsLearned(true);
                        }

                        apiService.updateWordProgress("eq." + existing.getId(), existing)
                                .enqueue(new Callback<Void>() {
                                    @Override
                                    public void onResponse(Call<Void> call,
                                                           Response<Void> r) {
                                    }

                                    @Override
                                    public void onFailure(Call<Void> call, Throwable t) {
                                        Log.e("WordProgressRepository",
                                                "Ошибка обновления прогресса: "
                                                        + t.getMessage(), t);
                                    }
                                });
                    }
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                Log.e("WordProgressRepository",
                        "Ошибка обновления прогресса слова: " + t.getMessage(), t);
            }
        });
    }

    public void getLearnedWords(Integer userId, DataCallback<List<Word>> callback) {
        if (userId == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        apiService.getUserProgressByUser(
                "eq." + userId,
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError(
                            "Не удалось загрузить изученные слова"));
                    return;
                }

                List<Long> learnedWordIds = new ArrayList<>();

                for (UserWordProgress progress : response.body()) {
                    if (progress != null
                            && progress.getWordId() != null
                            && progress.getIsLearned()) {
                        learnedWordIds.add(progress.getWordId());
                    }
                }

                if (learnedWordIds.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }

                if (listener != null) {
                    listener.loadWordsByIds(learnedWordIds, callback);
                } else {
                    mainHandler.post(() -> callback.onError("Listener не установлен"));
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError(
                        "Ошибка загрузки изученных слов: " + t.getMessage()));
            }
        });
    }

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
                        "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
                ).enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call,
                                           Response<List<UserWordProgress>> response) {
                        List<Long> learnedWordIds = new ArrayList<>();

                        if (response.isSuccessful() && response.body() != null) {
                            for (UserWordProgress progress : response.body()) {
                                if (progress != null
                                        && progress.getWordId() != null
                                        && progress.getIsLearned()) {
                                    learnedWordIds.add(progress.getWordId());
                                }
                            }
                        }

                        List<Word> result = buildUnlearnedList(words, learnedWordIds);
                        mainHandler.post(() -> callback.onSuccess(result));
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        List<Word> fallback = buildUnlearnedList(words, new ArrayList<>());
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

    private List<Word> buildUnlearnedList(List<Word> words, List<Long> learnedIds) {
        List<Word> result = new ArrayList<>();

        if (words == null) return result;

        for (Word word : words) {
            if (word == null || word.getId() == null) continue;

            String term = word.getTerm() == null ? "" : word.getTerm().trim();
            String translation = word.getTranslation() == null
                    ? "" : word.getTranslation().trim();

            if (!term.isEmpty()
                    && !translation.isEmpty()
                    && !learnedIds.contains(word.getId())) {
                result.add(word);
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
                continue;
            }

            upsertWordProgress(
                    userId.longValue(),
                    word.getId(),
                    true,
                    false,
                    new DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            completed[0]++;
                            if (completed[0] >= words.size()) {
                                callback.onSuccess(null);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            completed[0]++;
                            if (completed[0] >= words.size()) {
                                callback.onSuccess(null);
                            }
                        }
                    }
            );
        }
    }

    public void recordWordMistake(Word word, Integer userId) {
        if (userId == null || word == null || word.getId() == null) return;

        upsertWordProgress(
                userId.longValue(),
                word.getId(),
                false,
                true,
                new DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("WordProgressRepository",
                                "Ошибка записи ошибки слова: " + error);
                    }
                }
        );
    }

    public void upsertWordProgress(Long userId,
                                   Long wordId,
                                   boolean markLearned,
                                   boolean addMistake,
                                   DataCallback<Void> callback) {
        apiService.getUserWordProgress(
                "eq." + userId,
                "eq." + wordId,
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {

                    UserWordProgress existing = response.body().get(0);
                    existing.setCorrectAnswersCount(
                            existing.getCorrectAnswersCount() + (markLearned ? 1 : 0));
                    existing.setMistakesCount(
                            existing.getMistakesCount() + (addMistake ? 1 : 0));

                    if (markLearned) existing.setIsLearned(true);

                    apiService.updateWordProgress("eq." + existing.getId(), existing)
                            .enqueue(new Callback<Void>() {
                                @Override
                                public void onResponse(Call<Void> call,
                                                       Response<Void> response) {
                                    callback.onSuccess(null);
                                }

                                @Override
                                public void onFailure(Call<Void> call, Throwable t) {
                                    callback.onError(t.getMessage());
                                }
                            });
                } else {
                    UserWordProgress progress = new UserWordProgress();
                    progress.setUserId(userId);
                    progress.setWordId(wordId);
                    progress.setCorrectAnswersCount(markLearned ? 1 : 0);
                    progress.setMistakesCount(addMistake ? 1 : 0);
                    progress.setIsLearned(markLearned);

                    apiService.createWordProgress(progress)
                            .enqueue(new Callback<List<UserWordProgress>>() {
                                @Override
                                public void onResponse(
                                        Call<List<UserWordProgress>> call,
                                        Response<List<UserWordProgress>> response) {
                                    callback.onSuccess(null);
                                }

                                @Override
                                public void onFailure(
                                        Call<List<UserWordProgress>> call,
                                        Throwable t) {
                                    callback.onError(t.getMessage());
                                }
                            });
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getLearnedWordsForTraining(Integer userId,
                                           DataCallback<List<Word>> callback) {
        if (userId == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        apiService.getUserProgressByUser(
                "eq." + userId,
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError(
                            "Не удалось загрузить прогресс слов"));
                    return;
                }

                List<Long> learnedWordIds = new ArrayList<>();

                for (UserWordProgress progress : response.body()) {
                    if (progress != null
                            && progress.getWordId() != null
                            && progress.getIsLearned()) {
                        learnedWordIds.add(progress.getWordId());
                    }
                }

                if (learnedWordIds.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }

                if (listener != null) {
                    listener.loadWordsByIds(learnedWordIds, callback);
                } else {
                    mainHandler.post(() -> callback.onError("Listener не установлен"));
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError(
                        "Ошибка загрузки прогресса: " + t.getMessage()));
            }
        });
    }

    public void getMistakeWordsForTraining(Integer userId,
                                           DataCallback<List<Word>> callback) {
        if (userId == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        apiService.getUserProgressByUser(
                "eq." + userId,
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError(
                            "Не удалось загрузить ошибки"));
                    return;
                }

                List<Long> mistakeWordIds = new ArrayList<>();

                for (UserWordProgress progress : response.body()) {
                    if (progress == null) continue;
                    if (progress.getWordId() != null
                            && progress.getMistakesCount() > 0) {
                        mistakeWordIds.add(progress.getWordId());
                    }
                }

                if (mistakeWordIds.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }

                if (listener != null) {
                    listener.loadWordsByIds(mistakeWordIds, callback);
                } else {
                    mainHandler.post(() -> callback.onError("Listener не установлен"));
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError(
                        "Ошибка загрузки ошибок: " + t.getMessage()));
            }
        });
    }

    public void getLearnedWordsCount(Integer userId, DataCallback<Integer> callback) {
        if (userId == null || userId <= 0) {
            callback.onError("Некорректный пользователь");
            return;
        }

        apiService.getUserProgressByUser(
                "eq." + userId,
                "word_id,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError(
                            "Не удалось загрузить прогресс"));
                    return;
                }

                int count = 0;
                for (UserWordProgress progress : response.body()) {
                    if (progress != null
                            && progress.getIsLearned() != null
                            && progress.getIsLearned()) {
                        count++;
                    }
                }

                int finalCount = count;
                mainHandler.post(() -> callback.onSuccess(finalCount));
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError(
                        "Ошибка сети: " + t.getMessage()));
            }
        });
    }

    public void resolveWordMistake(Word word,
                                   Integer userId,
                                   DataCallback<Void> callback) {
        if (userId == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        if (word == null || word.getId() == null) {
            callback.onError("Слово не найдено");
            return;
        }

        apiService.getUserWordProgress(
                "eq." + userId,
                "eq." + word.getId(),
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful()
                        || response.body() == null
                        || response.body().isEmpty()) {

                    UserWordProgress newProgress = new UserWordProgress();
                    newProgress.setUserId(userId.longValue());
                    newProgress.setWordId(word.getId());
                    newProgress.setCorrectAnswersCount(1);
                    newProgress.setMistakesCount(0);
                    newProgress.setIsLearned(true);

                    apiService.createWordProgress(newProgress)
                            .enqueue(new Callback<List<UserWordProgress>>() {
                                @Override
                                public void onResponse(
                                        Call<List<UserWordProgress>> call,
                                        Response<List<UserWordProgress>> r) {
                                    mainHandler.post(() -> callback.onSuccess(null));
                                }

                                @Override
                                public void onFailure(
                                        Call<List<UserWordProgress>> call,
                                        Throwable t) {
                                    mainHandler.post(() -> callback.onError(
                                            "Ошибка сохранения прогресса: "
                                                    + t.getMessage()));
                                }
                            });
                    return;
                }

                UserWordProgress progress = response.body().get(0);
                int newMistakes = Math.max(0, progress.getMistakesCount() - 1);
                progress.setMistakesCount(newMistakes);
                progress.setCorrectAnswersCount(
                        progress.getCorrectAnswersCount() + 1);
                progress.setIsLearned(true);

                apiService.updateWordProgress("eq." + progress.getId(), progress)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call,
                                                   Response<Void> r) {
                                mainHandler.post(() -> callback.onSuccess(null));
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                mainHandler.post(() -> callback.onError(
                                        "Ошибка обновления прогресса: "
                                                + t.getMessage()));
                            }
                        });
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError(
                        "Ошибка загрузки прогресса: " + t.getMessage()));
            }
        });
    }

    // ===== ИНТЕРФЕЙС CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}