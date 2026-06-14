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
     * Загрузка слов по списку ID.
     * Сначала ищет в локальной Room-базе, затем догружает недостающие с сервера.
     * ✅ ИСПРАВЛЕНО: корректная обработка для новых устройств (пустая локальная БД).
     */
    public void loadWordsByIds(List<Long> ids, DataCallback<List<Word>> callback) {
        if (ids == null || ids.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        executor.execute(() -> {
            try {
                // 1. Пытаемся взять слова из локальной базы Room
                List<Word> localWords = null;
                try {
                    localWords = wordDao.getWordsByIds(ids);
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка чтения из Room", e);
                }

                if (localWords == null) {
                    localWords = new ArrayList<>();
                }

                // Если нашли ВСЕ запрошенные слова локально — сразу возвращаем
                if (localWords.size() == ids.size()) {
                    Log.d(TAG, "Все " + ids.size() + " слов загружены из локальной БД");
                    List<Word> result = localWords;
                    mainHandler.post(() -> callback.onSuccess(result));
                    return;
                }

                // 2. Определяем, каких слов нет локально
                List<Long> missingIds = new ArrayList<>(ids);
                for (Word w : localWords) {
                    if (w != null && w.getId() != null) {
                        missingIds.remove(w.getId());
                    }
                }

                Log.d(TAG, "Локально найдено: " + localWords.size()
                        + ", не хватает: " + missingIds.size()
                        + " из " + ids.size());

                // Если все слова есть локально (после уточнённой проверки)
                if (missingIds.isEmpty()) {
                    List<Word> result = localWords;
                    mainHandler.post(() -> callback.onSuccess(result));
                    return;
                }

                // 3. Формируем фильтр для Supabase: id=in.(1,2,3)
                StringBuilder filter = new StringBuilder("in.(");
                for (int i = 0; i < missingIds.size(); i++) {
                    if (i > 0) filter.append(",");
                    filter.append(missingIds.get(i));
                }
                filter.append(")");

                Log.d(TAG, "Запрашиваем из сети " + missingIds.size()
                        + " слов: id=" + filter);

                // Сохраняем финальную ссылку для использования в callback
                final List<Word> finalLocalWords = localWords;

                apiService.getWordsByIds(filter.toString(), "*", "id.asc")
                        .enqueue(new Callback<List<Word>>() {
                            @Override
                            public void onResponse(Call<List<Word>> call,
                                                   Response<List<Word>> response) {
                                if (!response.isSuccessful()) {
                                    StringBuilder errorBuilder = new StringBuilder();
                                    errorBuilder.append("код: ").append(response.code());
                                    try {
                                        if (response.errorBody() != null) {
                                            errorBuilder.append(", ")
                                                    .append(response.errorBody().string());
                                        }
                                    } catch (Exception ignored) {}

                                    final String errorDetails = errorBuilder.toString();

                                    Log.e(TAG, "Ошибка сервера при загрузке слов: "
                                            + errorDetails);

                                    // Если локально что-то есть — возвращаем частичный результат
                                    if (!finalLocalWords.isEmpty()) {
                                        Log.w(TAG, "Возвращаем " + finalLocalWords.size()
                                                + " слов из локальной БД (неполный набор)");
                                        mainHandler.post(() ->
                                                callback.onSuccess(finalLocalWords));
                                    } else {
                                        mainHandler.post(() ->
                                                callback.onError(
                                                        "Не удалось загрузить слова с сервера ("
                                                                + errorDetails + ")"));
                                    }
                                    return;
                                }

                                List<Word> fetched = response.body();
                                if (fetched == null) {
                                    fetched = new ArrayList<>();
                                }

                                // Фильтруем валидные слова
                                List<Word> validWords = new ArrayList<>();
                                for (Word w : fetched) {
                                    if (w != null
                                            && w.getTerm() != null
                                            && w.getTranslation() != null
                                            && !w.getTerm().trim().isEmpty()
                                            && !w.getTranslation().trim().isEmpty()) {
                                        validWords.add(w);
                                    }
                                }

                                Log.d(TAG, "С сервера получено " + validWords.size()
                                        + " валидных слов из " + fetched.size());

                                final List<Word> finalValidWords = validWords;

                                executor.execute(() -> {
                                    try {
                                        if (!finalValidWords.isEmpty()) {
                                            wordDao.insertAll(finalValidWords);
                                            Log.d(TAG, "Сохранено в Room "
                                                    + finalValidWords.size() + " слов");
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Ошибка сохранения в Room", e);
                                    }

                                    Map<Long, Word> map = new HashMap<>();
                                    for (Word w : finalLocalWords) {
                                        if (w != null && w.getId() != null) {
                                            map.put(w.getId(), w);
                                        }
                                    }
                                    for (Word w : finalValidWords) {
                                        if (w != null && w.getId() != null) {
                                            map.put(w.getId(), w);
                                        }
                                    }

                                    List<Word> finalList = new ArrayList<>();
                                    for (Long id : ids) {
                                        Word w = map.get(id);
                                        if (w != null) {
                                            finalList.add(w);
                                        }
                                    }

                                    Log.d(TAG, "Итого собрано "
                                            + finalList.size() + " слов из "
                                            + ids.size() + " запрошенных");

                                    if (finalList.isEmpty()) {
                                        mainHandler.post(() ->
                                                callback.onError(
                                                        "Не удалось загрузить данные слов. "
                                                                + "Проверьте подключение к интернету."));
                                    } else {
                                        mainHandler.post(() ->
                                                callback.onSuccess(finalList));
                                    }
                                });
                            }

                            @Override
                            public void onFailure(Call<List<Word>> call, Throwable t) {
                                Log.e(TAG, "Сетевой сбой getWordsByIds: "
                                        + t.getMessage(), t);

                                // ✅ Если локально что-то есть — отдаём,
                                //    иначе — честная ошибка (а не пустой список)
                                if (!finalLocalWords.isEmpty()) {
                                    Log.w(TAG, "Сеть недоступна. Возвращаем "
                                            + finalLocalWords.size()
                                            + " слов из локальной БД");
                                    mainHandler.post(() ->
                                            callback.onSuccess(finalLocalWords));
                                } else {
                                    mainHandler.post(() ->
                                            callback.onError(
                                                    "Нет подключения к интернету. "
                                                            + "Слова не были загружены ранее."));
                                }
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Критическая ошибка в loadWordsByIds", e);
                mainHandler.post(() ->
                        callback.onError("Ошибка загрузки слов: " + e.getMessage()));
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