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
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Репозиторий слов: локальная загрузка по теме и загрузка по ID с fallback на сеть
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

    // Возвращает слова темы из локальной БД; при отсутствии сообщает об ошибке
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

    // Загружает слова по списку ID: сначала из Room, недостающие запрашивает с сервера.
    // При пустой локальной БД (новое устройство) загружает все слова из сети.
    public void loadWordsByIds(List<Long> ids, DataCallback<List<Word>> callback) {
        Log.d("WordRepoDiag", ">>> loadWordsByIds, ids count="
                + (ids == null ? "null" : ids.size()));

        if (ids == null || ids.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        executor.execute(() -> {
            try {
                // Шаг 1: ищем слова в локальной БД
                List<Word> localWords = null;
                try {
                    localWords = wordDao.getWordsByIds(ids);
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка чтения из Room", e);
                }

                if (localWords == null) {
                    localWords = new ArrayList<>();
                }

                // Если все запрошенные слова найдены локально — возвращаем сразу
                if (localWords.size() == ids.size()) {
                    Log.d(TAG, "Все " + ids.size() + " слов загружены из локальной БД");
                    List<Word> result = localWords;
                    mainHandler.post(() -> callback.onSuccess(result));
                    return;
                }

                // Шаг 2: определяем отсутствующие ID
                List<Long> missingIds = new ArrayList<>(ids);
                for (Word w : localWords) {
                    if (w != null && w.getId() != null) {
                        missingIds.remove(w.getId());
                    }
                }

                Log.d(TAG, "Локально найдено: " + localWords.size()
                        + ", не хватает: " + missingIds.size()
                        + " из " + ids.size());

                if (missingIds.isEmpty()) {
                    List<Word> result = localWords;
                    mainHandler.post(() -> callback.onSuccess(result));
                    return;
                }

                // Шаг 3: формируем PostgREST-фильтр "in.(...)" для сетевого запроса
                StringBuilder filter = new StringBuilder("in.(");
                for (int i = 0; i < missingIds.size(); i++) {
                    if (i > 0) filter.append(",");
                    filter.append(missingIds.get(i));
                }
                filter.append(")");

                Log.d(TAG, "Запрашиваем из сети " + missingIds.size()
                        + " слов: id=" + filter);

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

                                    // При ошибке сервера возвращаем частичный локальный результат
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

                                // Фильтруем слова с валидным термином и переводом
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
                                    // Сохраняем полученные с сервера слова в Room
                                    try {
                                        if (!finalValidWords.isEmpty()) {
                                            wordDao.insertAll(finalValidWords);
                                            Log.d(TAG, "Сохранено в Room "
                                                    + finalValidWords.size() + " слов");
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Ошибка сохранения в Room", e);
                                    }

                                    // Объединяем локальные и серверные слова,
                                    // сохраняя исходный порядок из ids
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

                                // При отсутствии сети возвращаем то, что есть локально
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

    // Cache-first: возвращает локальные слова темы СРАЗУ если они есть,
    // и обновляет локальный кэш с сервера в фоне БЕЗ повторного колбэка.
    // Если локально пусто — ждёт ответ сервера и возвращает его.
    public void getWordsByThemeCacheFirst(Long themeId, DataCallback<List<Word>> callback) {
        if (themeId == null || themeId <= 0) {
            mainHandler.post(() -> callback.onError("Некорректный ID темы"));
            return;
        }

        // Защита от двойного вызова колбэка
        final AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        executor.execute(() -> {
            try {
                List<Word> localWords = null;
                try {
                    localWords = wordDao.getWordsByTheme(themeId);
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка чтения слов темы из Room", e);
                }

                final boolean hasLocal = localWords != null && !localWords.isEmpty();

                // Если есть локально — отдаём ОДИН раз и обновляем кэш в фоне без колбэка
                if (hasLocal) {
                    final List<Word> cached = localWords;
                    if (callbackInvoked.compareAndSet(false, true)) {
                        mainHandler.post(() -> callback.onSuccess(cached));
                    }
                    // Фоновое обновление кэша БЕЗ повторного колбэка
                    refreshWordsByThemeFromServer(themeId, null);
                } else {
                    // Локально пусто — ждём ответ сервера и возвращаем его в колбэк
                    refreshWordsByThemeFromServerWithCallback(themeId, callback, callbackInvoked);
                }

            } catch (Exception e) {
                Log.e(TAG, "Критическая ошибка в getWordsByThemeCacheFirst", e);
                refreshWordsByThemeFromServerWithCallback(themeId, callback, callbackInvoked);
            }
        });
    }

    // Фоновое обновление локального кэша БЕЗ вызова UI-колбэка
    private void refreshWordsByThemeFromServer(Long themeId, Runnable onDone) {
        String filter = "eq." + themeId;

        apiService.getWordsByTheme(filter, "*", "id.asc")
                .enqueue(new Callback<List<Word>>() {
                    @Override
                    public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Log.w(TAG, "Фоновое обновление темы " + themeId
                                    + " не удалось: " + response.code());
                            if (onDone != null) onDone.run();
                            return;
                        }

                        List<Word> validWords = filterValid(response.body());

                        executor.execute(() -> {
                            try {
                                wordDao.deleteWordsByThemeId(themeId);
                                if (!validWords.isEmpty()) {
                                    wordDao.insertAll(validWords);
                                }
                                Log.d(TAG, "Фоновое обновление темы " + themeId
                                        + ": сохранено " + validWords.size() + " слов");
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка фонового сохранения слов темы", e);
                            }
                            if (onDone != null) onDone.run();
                        });
                    }

                    @Override
                    public void onFailure(Call<List<Word>> call, Throwable t) {
                        Log.w(TAG, "Фоновое обновление темы " + themeId
                                + " прервано: " + t.getMessage());
                        if (onDone != null) onDone.run();
                    }
                });
    }

    // Загружает слова темы с сервера и ОБЯЗАТЕЛЬНО вызывает колбэк ровно один раз
    private void refreshWordsByThemeFromServerWithCallback(Long themeId,
                                                           DataCallback<List<Word>> callback,
                                                           AtomicBoolean callbackInvoked) {
        String filter = "eq." + themeId;

        apiService.getWordsByTheme(filter, "*", "id.asc")
                .enqueue(new Callback<List<Word>>() {
                    @Override
                    public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Log.e(TAG, "Supabase не загрузил слова темы " + themeId
                                    + ": " + response.code() + " | " + getErrorBody(response));

                            if (callbackInvoked.compareAndSet(false, true)) {
                                mainHandler.post(() -> callback.onError("В теме пока нет слов"));
                            }
                            return;
                        }

                        List<Word> validWords = filterValid(response.body());

                        Log.d(TAG, "С сервера получено " + validWords.size()
                                + " слов для темы " + themeId);

                        executor.execute(() -> {
                            try {
                                wordDao.deleteWordsByThemeId(themeId);
                                if (!validWords.isEmpty()) {
                                    wordDao.insertAll(validWords);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка сохранения слов темы в Room", e);
                            }

                            if (callbackInvoked.compareAndSet(false, true)) {
                                if (validWords.isEmpty()) {
                                    mainHandler.post(() -> callback.onError("В теме пока нет слов"));
                                } else {
                                    mainHandler.post(() -> callback.onSuccess(validWords));
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<List<Word>> call, Throwable t) {
                        Log.e(TAG, "Сетевой сбой getWordsByTheme: " + t.getMessage(), t);
                        if (callbackInvoked.compareAndSet(false, true)) {
                            mainHandler.post(() -> callback.onError("Нет подключения к интернету"));
                        }
                    }
                });
    }

    // Фильтрует список, оставляя только записи с непустым term и translation
    private List<Word> filterValid(List<Word> words) {
        List<Word> result = new ArrayList<>();
        if (words == null) return result;
        for (Word w : words) {
            if (w != null
                    && w.getTerm() != null
                    && w.getTranslation() != null
                    && !w.getTerm().trim().isEmpty()
                    && !w.getTranslation().trim().isEmpty()) {
                result.add(w);
            }
        }
        return result;
    }
}