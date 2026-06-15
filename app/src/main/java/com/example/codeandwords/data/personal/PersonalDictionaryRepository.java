package com.example.codeandwords.data.personal;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.ThemeDao;
import com.example.codeandwords.db.UserWordDao;
import com.example.codeandwords.db.WordDao;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserWord;
import com.example.codeandwords.model.Word;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Репозиторий личного словаря пользователя: добавление, удаление, синхронизация с сервером
public class PersonalDictionaryRepository {

    private static final String TAG = "PersonalDictRepo";

    private final UserWordDao userWordDao;
    private final WordDao wordDao;
    private final ThemeDao themeDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public PersonalDictionaryRepository(UserWordDao userWordDao,
                                        WordDao wordDao,
                                        ThemeDao themeDao,
                                        ApiService apiService,
                                        ExecutorService executor,
                                        Handler mainHandler) {
        this.userWordDao = userWordDao;
        this.wordDao = wordDao;
        this.themeDao = themeDao;
        this.apiService = apiService;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeThemeTitle(Theme theme) {
        if (theme == null || theme.getTitle() == null || theme.getTitle().trim().isEmpty()) {
            return "Без темы";
        }
        return theme.getTitle().trim();
    }

    // Пытается найти тему слова локально по термину и переводу
    private Theme findThemeForWordLocally(String term, String translation) {
        String safeTerm = normalizeText(term);
        String safeTranslation = normalizeText(translation);

        if (safeTerm.isEmpty()) return null;

        try {
            Word sourceWord = null;
            if (!safeTranslation.isEmpty()) {
                sourceWord = wordDao.getWordByTermAndTranslation(safeTerm, safeTranslation);
            }
            if (sourceWord == null) {
                sourceWord = wordDao.getWordByTerm(safeTerm);
            }
            if (sourceWord == null || sourceWord.getThemeId() == null) return null;

            return themeDao.getThemeById(sourceWord.getThemeId());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка автопривязки темы: " + e.getMessage(), e);
            return null;
        }
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) return response.errorBody().string();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // Разбирает JSON-объект из ответа сервера в модель UserWord
    private UserWord parseUserWordFromJson(JsonObject json, Integer fallbackUserId) {
        UserWord word = new UserWord();
        if (json == null) return word;

        if (json.has("id") && !json.get("id").isJsonNull())
            word.setServerId(json.get("id").getAsLong());

        if (json.has("user_id") && !json.get("user_id").isJsonNull())
            word.setUserId(json.get("user_id").getAsInt());
        else
            word.setUserId(fallbackUserId);

        // Поддержка обоих вариантов написания поля theme_id
        if (json.has("themeid") && !json.get("themeid").isJsonNull())
            word.setThemeId(json.get("themeid").getAsLong());
        else if (json.has("theme_id") && !json.get("theme_id").isJsonNull())
            word.setThemeId(json.get("theme_id").getAsLong());

        if (json.has("themetitle") && !json.get("themetitle").isJsonNull())
            word.setThemeTitle(json.get("themetitle").getAsString());
        else if (json.has("theme_title") && !json.get("theme_title").isJsonNull())
            word.setThemeTitle(json.get("theme_title").getAsString());
        else
            word.setThemeTitle("Без темы");

        if (json.has("word") && !json.get("word").isJsonNull())
            word.setWord(json.get("word").getAsString());
        if (json.has("translation") && !json.get("translation").isJsonNull())
            word.setTranslation(json.get("translation").getAsString());
        if (json.has("transcription") && !json.get("transcription").isJsonNull())
            word.setTranscription(json.get("transcription").getAsString());
        if (json.has("notes") && !json.get("notes").isJsonNull())
            word.setNotes(json.get("notes").getAsString());

        word.setDateAdded(System.currentTimeMillis());
        word.setSynced(true);

        return word;
    }

    // Добавляет слово без привязки к теме
    public void addUserWord(String word, String translation, String transcription,
                            String notes, User currentUser, DataCallback<Void> callback) {
        executor.execute(() ->
                addWordInternalSync(null, "Без темы", word, translation,
                        transcription, notes, currentUser, callback));
    }

    // Добавляет слово с явно указанной темой
    public void addUserWord(Long themeId, String themeTitle, String word, String translation,
                            String transcription, String notes, User currentUser,
                            DataCallback<Void> callback) {
        executor.execute(() ->
                addWordInternalSync(themeId, themeTitle, word, translation,
                        transcription, notes, currentUser, callback));
    }

    // Добавляет слово из общего словаря в личный, автоматически определяя тему
    public void addWordToPersonalDictionary(Word word, User currentUser, DataCallback<Void> callback) {
        if (word == null) {
            callback.onError("Слово не найдено");
            return;
        }
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            Long finalThemeId = word.getThemeId();
            String finalThemeTitle = "Без темы";

            try {
                if (finalThemeId != null && finalThemeId > 0) {
                    Theme theme = themeDao.getThemeById(finalThemeId);
                    if (theme != null) finalThemeTitle = safeThemeTitle(theme);
                } else {
                    Theme autoTheme = findThemeForWordLocally(word.getTerm(), word.getTranslation());
                    if (autoTheme != null && autoTheme.getId() != null) {
                        finalThemeId = autoTheme.getId();
                        finalThemeTitle = safeThemeTitle(autoTheme);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Не удалось определить тему слова: " + e.getMessage(), e);
            }

            addWordInternalSync(finalThemeId, finalThemeTitle, word.getTerm(),
                    word.getTranslation(), word.getTranscription(),
                    word.getExampleSentence(), currentUser, callback);
        });
    }

    // Основной метод добавления: локальное сохранение → уведомление UI → отправка на сервер
    private void addWordInternalSync(Long themeId, String themeTitle, String word,
                                     String translation, String transcription, String notes,
                                     User currentUser, DataCallback<Void> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }

        String safeWord = normalizeText(word);
        String safeTranslation = normalizeText(translation);
        String safeTranscription = normalizeText(transcription);
        String safeNotes = normalizeText(notes);

        if (safeWord.isEmpty() || safeTranslation.isEmpty()) {
            mainHandler.post(() -> callback.onError("Заполните слово и перевод"));
            return;
        }

        try {
            UserWord existing = userWordDao.findWordByUserAndTerm(currentUser.getId(), safeWord);
            if (existing != null) {
                mainHandler.post(() -> callback.onError("Это слово уже есть в личном словаре"));
                return;
            }

            Long finalThemeId = themeId;
            String finalThemeTitle = normalizeText(themeTitle);

            // Автоопределение темы, если не задана явно
            if (finalThemeId == null || finalThemeTitle.isEmpty()
                    || "Без темы".equals(finalThemeTitle)) {
                Theme autoTheme = findThemeForWordLocally(safeWord, safeTranslation);
                if (autoTheme != null && autoTheme.getId() != null) {
                    finalThemeId = autoTheme.getId();
                    finalThemeTitle = safeThemeTitle(autoTheme);
                }
            }

            if (finalThemeTitle.isEmpty()) finalThemeTitle = "Без темы";

            UserWord userWord = new UserWord(currentUser.getId(), finalThemeId, finalThemeTitle,
                    safeWord, safeTranslation, safeTranscription, safeNotes);

            // Шаг 1: быстрое локальное сохранение
            long localId = userWordDao.insert(userWord);
            userWord.setId(localId);

            // Шаг 2: немедленное уведомление UI (иконка закрасится сразу)
            mainHandler.post(() -> callback.onSuccess(null));

            // Шаг 3: отправка на сервер в фоне с сохранением serverId
            JsonObject payload = new JsonObject();
            payload.addProperty("user_id", currentUser.getId());
            payload.addProperty("word", safeWord);
            payload.addProperty("translation", safeTranslation);
            if (!safeTranscription.isEmpty()) payload.addProperty("transcription", safeTranscription);
            if (!safeNotes.isEmpty()) payload.addProperty("notes", safeNotes);
            if (finalThemeId != null) payload.addProperty("themeid", finalThemeId);
            payload.addProperty("themetitle", finalThemeTitle);

            apiService.insertUserPersonalWordRaw(payload).enqueue(new Callback<List<JsonObject>>() {
                @Override
                public void onResponse(Call<List<JsonObject>> call,
                                       Response<List<JsonObject>> response) {
                    if (response.isSuccessful()
                            && response.body() != null
                            && !response.body().isEmpty()) {
                        JsonObject serverSaved = response.body().get(0);
                        executor.execute(() -> {
                            try {
                                if (serverSaved.has("id") && !serverSaved.get("id").isJsonNull()) {
                                    long serverId = serverSaved.get("id").getAsLong();
                                    userWordDao.updateUserWordServerId(localId, serverId);
                                    Log.d(TAG, "Слово сохранено на сервере. serverId=" + serverId);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка сохранения serverId: " + e.getMessage(), e);
                            }
                        });
                    } else {
                        Log.e(TAG, "Не удалось отправить слово на сервер: "
                                + response.code() + " | " + getErrorBody(response));
                    }
                }

                @Override
                public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                    Log.e(TAG, "Ошибка сети/парсинга при отправке слова: " + t.getMessage(), t);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Ошибка добавления слова: " + e.getMessage(), e);
            mainHandler.post(() -> callback.onError("Не удалось добавить слово"));
        }
    }

    // Удаляет слово: сначала с сервера (если есть serverId), затем из локальной БД
    public void deleteUserWord(UserWord word, Runnable onDone) {
        if (word == null) {
            if (onDone != null) mainHandler.post(onDone);
            return;
        }

        if (word.getServerId() != null) {
            apiService.deleteUserPersonalWordFromServer(
                    "eq." + word.getUserId(), "eq." + word.getServerId()
            ).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Ошибка удаления на сервере: " + response.code()
                                + " | " + getErrorBody(response));
                    }
                }
                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Log.e(TAG, "Сеть: ошибка удаления на сервере: " + t.getMessage(), t);
                }
            });
        }

        executor.execute(() -> {
            try {
                userWordDao.delete(word);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка удаления слова: " + e.getMessage(), e);
            }
            if (onDone != null) mainHandler.post(onDone);
        });
    }

    // Удаляет слово из личного словаря по термину
    public void deleteUserWordByTerm(String term, User currentUser, DataCallback<Void> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }

        String safeTerm = normalizeText(term);
        if (safeTerm.isEmpty()) {
            mainHandler.post(() -> callback.onError("Пустое слово"));
            return;
        }

        executor.execute(() -> {
            try {
                UserWord existing = userWordDao.findWordByUserAndTerm(currentUser.getId(), safeTerm);
                if (existing != null && existing.getServerId() != null) {
                    apiService.deleteUserPersonalWordFromServer(
                            "eq." + currentUser.getId(), "eq." + existing.getServerId()
                    ).enqueue(new Callback<Void>() {
                        @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                        @Override public void onFailure(Call<Void> call, Throwable t) {}
                    });
                }
                userWordDao.deleteUserWordsByTerm(currentUser.getId(), safeTerm);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка удаления слова по термину: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось удалить слово"));
            }
        });
    }

    // Возвращает все слова личного словаря пользователя
    public void getUserPersonalWords(User currentUser, DataCallback<List<UserWord>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<UserWord> words = userWordDao.getUserWords(currentUser.getId());
                mainHandler.post(() -> callback.onSuccess(words != null ? words : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить словарь"));
            }
        });
    }

    // Возвращает слова личного словаря пользователя, отфильтрованные по теме
    public void getUserPersonalWordsByTheme(Long themeId, String themeTitle,
                                            User currentUser, DataCallback<List<UserWord>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<UserWord> words = themeId == null
                        ? userWordDao.getUserWordsWithoutTheme(currentUser.getId())
                        : userWordDao.getUserWordsByThemeId(currentUser.getId(), themeId);
                mainHandler.post(() -> callback.onSuccess(words != null ? words : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки словаря по теме: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить слова темы"));
            }
        });
    }

    // Возвращает список уникальных названий тем из личного словаря пользователя
    public void getUserDictionaryThemeTitles(User currentUser, DataCallback<List<String>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<String> themes = userWordDao.getUserDictionaryThemeTitles(currentUser.getId());
                mainHandler.post(() -> callback.onSuccess(themes != null ? themes : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки тем словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить темы словаря"));
            }
        });
    }

    // Проверяет, добавлено ли слово в личный словарь пользователя
    public void isWordInPersonalDictionary(Word word, User currentUser, DataCallback<Boolean> callback) {
        if (word == null || word.getTerm() == null || word.getTerm().trim().isEmpty()) {
            callback.onSuccess(false);
            return;
        }
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        String safeTerm = word.getTerm().trim();

        executor.execute(() -> {
            try {
                UserWord existingWord = userWordDao.findWordByUserAndTerm(currentUser.getId(), safeTerm);
                mainHandler.post(() -> callback.onSuccess(existingWord != null));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка проверки слова в словаре: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось проверить слово в словаре"));
            }
        });
    }

    // Восстанавливает привязку к теме для слов личного словаря, у которых она отсутствует
    public void repairPersonalDictionaryThemes(User currentUser, DataCallback<Void> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<UserWord> personalWords = userWordDao.getUserWords(currentUser.getId());
                if (personalWords == null || personalWords.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(null));
                    return;
                }

                List<UserWord> needsRepair = new ArrayList<>();
                for (UserWord uw : personalWords) {
                    if (uw == null || uw.getWord() == null) continue;
                    boolean broken = uw.getThemeId() == null
                            || uw.getThemeTitle() == null
                            || uw.getThemeTitle().trim().isEmpty()
                            || "Без темы".equals(uw.getThemeTitle());
                    if (broken) needsRepair.add(uw);
                }

                if (needsRepair.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(null));
                    return;
                }

                // Кэш тем для минимизации обращений к БД
                Map<Long, String> themeCache = new HashMap<>();

                for (UserWord uw : needsRepair) {
                    Word sourceWord = wordDao.getWordByTerm(uw.getWord());
                    if (sourceWord == null || sourceWord.getThemeId() == null) continue;

                    Long sourceThemeId = sourceWord.getThemeId();
                    String sourceThemeTitle = themeCache.get(sourceThemeId);

                    if (sourceThemeTitle == null) {
                        Theme theme = themeDao.getThemeById(sourceThemeId);
                        if (theme == null || theme.getTitle() == null
                                || theme.getTitle().trim().isEmpty()) continue;
                        sourceThemeTitle = theme.getTitle().trim();
                        themeCache.put(sourceThemeId, sourceThemeTitle);
                    }

                    userWordDao.updateUserWordTheme(uw.getId(), sourceThemeId, sourceThemeTitle);
                }

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка восстановления тем словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось восстановить темы словаря"));
            }
        });
    }

    public void syncPersonalWords(User currentUser, DataCallback<Void> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }
        syncPersonalWordsFromServer(currentUser.getId(), callback);
    }

    public void syncPersonalWords(Integer userId, DataCallback<Void> callback) {
        if (userId == null || userId <= 0) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }
        syncPersonalWordsFromServer(userId, callback);
    }

    // Загружает словарь с сервера, заменяет локальные данные, сохраняя несинхронизированные записи
    public void syncPersonalWordsFromServer(Integer userId, DataCallback<Void> callback) {
        apiService.getUserPersonalWordsFromServer("eq." + userId, "date_added.desc")
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<JsonObject> remoteJsonWords = response.body();

                            executor.execute(() -> {
                                try {
                                    List<UserWord> localBefore = userWordDao.getUserWords(userId);
                                    List<UserWord> unsyncedLocal = new ArrayList<>();

                                    // Собираем локальные слова, ещё не попавшие на сервер
                                    if (localBefore != null) {
                                        for (UserWord local : localBefore) {
                                            if (local == null) continue;
                                            if (local.getServerId() == null || !local.isSynced()) {
                                                unsyncedLocal.add(local);
                                            }
                                        }
                                    }

                                    List<UserWord> remoteWords = new ArrayList<>();
                                    for (JsonObject json : remoteJsonWords) {
                                        UserWord parsed = parseUserWordFromJson(json, userId);
                                        if (parsed.getWord() != null
                                                && !parsed.getWord().trim().isEmpty()) {
                                            remoteWords.add(parsed);
                                        }
                                    }

                                    // Полная замена локальных данных серверными
                                    userWordDao.deleteAllUserWordsForUser(userId);
                                    if (!remoteWords.isEmpty()) {
                                        userWordDao.insertAll(remoteWords);
                                    }

                                    // Восстанавливаем несинхронизированные локальные слова
                                    for (UserWord local : unsyncedLocal) {
                                        if (local == null || local.getWord() == null) continue;
                                        boolean alreadyExists = false;
                                        for (UserWord remote : remoteWords) {
                                            if (remote == null || remote.getWord() == null) continue;
                                            if (remote.getWord().trim()
                                                    .equalsIgnoreCase(local.getWord().trim())) {
                                                alreadyExists = true;
                                                break;
                                            }
                                        }
                                        if (!alreadyExists) {
                                            local.setId(null);
                                            userWordDao.insert(local);
                                        }
                                    }

                                    Log.d(TAG, "Словарь синхронизирован. Сервер: "
                                            + remoteWords.size() + ", локально несинхр.: "
                                            + unsyncedLocal.size());
                                    mainHandler.post(() -> callback.onSuccess(null));

                                } catch (Exception e) {
                                    Log.e(TAG, "Ошибка сохранения слов: " + e.getMessage(), e);
                                    mainHandler.post(() -> callback.onError(
                                            "Ошибка БД: " + e.getMessage()));
                                }
                            });
                        } else {
                            mainHandler.post(() -> callback.onError(
                                    "Ошибка сервера: " + getErrorBody(response)));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e(TAG, "Сбой сети/парсинга при загрузке словаря: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError(
                                "Нет соединения: " + t.getMessage()));
                    }
                });
    }
}