package com.example.codeandwords.data.word;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.WordDao;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WordRepository {

    private static final String TAG = "WordRepository";

    private final WordDao wordDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public WordRepository(WordDao wordDao,
                          ApiService apiService,
                          ExecutorService executor,
                          Handler mainHandler) {
        this.wordDao = wordDao;
        this.apiService = apiService;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    public void loadWordsLocal(Long themeId, DataCallback<List<Word>> callback) {
        executor.execute(() -> {
            try {
                List<Word> words = wordDao.getWordsByTheme(themeId);
                if (words != null && !words.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(words));
                } else {
                    mainHandler.post(() -> callback.onError("В теме пока нет слов"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локальной загрузки слов", e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки слов"));
            }
        });
    }

    /**
     * Основной метод, используемый для аудирования, режима ошибок и выученных слов
     */
    public void loadWordsByIds(List<Long> ids, DataCallback<List<Word>> callback) {
        if (ids == null || ids.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        executor.execute(() -> {
            try {
                // 1. Пытаемся взять слова из локальной базы Room
                List<Word> localWords = wordDao.getWordsByIds(ids);

                // Если нашли все запрошенные слова — сразу возвращаем (самый быстрый путь)
                if (localWords != null && localWords.size() == ids.size()) {
                    Log.d(TAG, "Слова загружены из локальной БД: " + localWords.size() + " шт.");
                    mainHandler.post(() -> callback.onSuccess(localWords));
                    return;
                }

                // 2. Если каких-то слов нет локально — запрашиваем их из Supabase
                List<Long> missingIds = new ArrayList<>(ids);
                if (localWords != null) {
                    for (Word w : localWords) {
                        missingIds.remove(w.getId());
                    }
                }

                List<Long> idsToFetch = missingIds.isEmpty() ? ids : missingIds;

                // Правильный формат фильтра для Supabase: id=in.(1,2,3,4)
                StringBuilder filter = new StringBuilder("in.(");
                for (int i = 0; i < idsToFetch.size(); i++) {
                    if (i > 0) filter.append(",");
                    filter.append(idsToFetch.get(i));
                }
                filter.append(")");

                Log.d(TAG, "Запрашиваем из сети слова: " + filter);

                apiService.getWordsByIds(filter.toString(), "*", "id.asc")
                        .enqueue(new Callback<List<Word>>() {
                            @Override
                            public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                                if (!response.isSuccessful()) {
                                    Log.e(TAG, "Ошибка сервера. Code: " + response.code()
                                            + " | Error: " + getErrorBody(response));
                                    // Если сеть упала — возвращаем то, что есть локально
                                    mainHandler.post(() -> callback.onSuccess(localWords != null ? localWords : new ArrayList<>()));
                                    return;
                                }

                                List<Word> fetched = response.body();
                                if (fetched == null || fetched.isEmpty()) {
                                    Log.w(TAG, "Сервер вернул пустой список слов");
                                    mainHandler.post(() -> callback.onSuccess(localWords != null ? localWords : new ArrayList<>()));
                                    return;
                                }

                                List<Word> validWords = new ArrayList<>();
                                for (Word w : fetched) {
                                    if (w != null && w.getTerm() != null && w.getTranslation() != null
                                            && !w.getTerm().trim().isEmpty()
                                            && !w.getTranslation().trim().isEmpty()) {
                                        validWords.add(w);
                                    }
                                }

                                // Сохраняем новые слова в локальную БД
                                executor.execute(() -> {
                                    try {
                                        if (!validWords.isEmpty()) {
                                            wordDao.insertAll(validWords);
                                            Log.d(TAG, "Сохранено в Room " + validWords.size() + " слов");
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Ошибка сохранения в Room", e);
                                    }

                                    // Объединяем локальные и свежие данные
                                    Map<Long, Word> map = new HashMap<>();
                                    if (localWords != null) {
                                        for (Word w : localWords) map.put(w.getId(), w);
                                    }
                                    for (Word w : validWords) map.put(w.getId(), w);

                                    List<Word> finalList = new ArrayList<>();
                                    for (Long id : ids) {
                                        if (map.containsKey(id)) finalList.add(map.get(id));
                                    }

                                    mainHandler.post(() -> callback.onSuccess(finalList));
                                });
                            }

                            @Override
                            public void onFailure(Call<List<Word>> call, Throwable t) {
                                Log.e(TAG, "Сетевой сбой getWordsByIds", t);
                                // При любой сетевой ошибке возвращаем то, что есть в локальной БД
                                mainHandler.post(() -> callback.onSuccess(
                                        localWords != null ? localWords : new ArrayList<>()));
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Критическая ошибка в loadWordsByIds", e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки слов"));
            }
        });
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            return "Не удалось прочитать тело ошибки";
        }
        return "Пустое тело ошибки";
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}