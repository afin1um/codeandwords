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

import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PersonalDictionaryRepository {

    private final UserWordDao userWordDao;
    private final WordDao wordDao;
    private final ThemeDao themeDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public PersonalDictionaryRepository(
            UserWordDao userWordDao,
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

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeThemeTitle(Theme theme) {
        if (theme == null || theme.getTitle() == null || theme.getTitle().trim().isEmpty()) {
            return "Без темы";
        }
        return theme.getTitle().trim();
    }

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
            Log.e("PersonalDictionaryRepository", "Ошибка автопривязки темы: " + e.getMessage(), e);
            return null;
        }
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e("PersonalDictionaryRepository", "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // ===== ОСНОВНЫЕ МЕТОДЫ =====

    public void addUserWord(String word,
                            String translation,
                            String transcription,
                            String notes,
                            User currentUser,
                            DataCallback<Void> callback) {
        addUserWord(null, null, word, translation, transcription, notes, currentUser, callback);
    }

    public void addUserWord(Long themeId,
                            String themeTitle,
                            String word,
                            String translation,
                            String transcription,
                            String notes,
                            User currentUser,
                            DataCallback<Void> callback) {
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

        executor.execute(() -> {
            try {
                UserWord existing = userWordDao.findWordByUserAndTerm(
                        currentUser.getId(), safeWord);

                if (existing != null) {
                    mainHandler.post(() -> callback.onError(
                            "Это слово уже есть в личном словаре"));
                    return;
                }

                Long finalThemeId = themeId;
                String finalThemeTitle = normalizeText(themeTitle);

                if (finalThemeId == null
                        || finalThemeTitle.isEmpty()
                        || "Без темы".equals(finalThemeTitle)) {
                    Theme autoTheme = findThemeForWordLocally(safeWord, safeTranslation);

                    if (autoTheme != null && autoTheme.getId() != null) {
                        finalThemeId = autoTheme.getId();
                        finalThemeTitle = safeThemeTitle(autoTheme);
                    }
                }

                if (finalThemeTitle.isEmpty()) {
                    finalThemeTitle = "Без темы";
                }

                UserWord userWord = new UserWord(
                        currentUser.getId(),
                        finalThemeId,
                        finalThemeTitle,
                        safeWord,
                        safeTranslation,
                        safeTranscription,
                        safeNotes
                );

                long insertedId = userWordDao.insert(userWord);

                Log.d("PersonalDictionaryRepository",
                        "Слово добавлено. localId=" + insertedId
                                + ", userId=" + currentUser.getId()
                                + ", word=" + safeWord);

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Ошибка добавления слова: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось добавить слово"));
            }
        });
    }

    public void addWordToPersonalDictionary(Word word,
                                            User currentUser,
                                            DataCallback<Void> callback) {
        if (word == null) {
            callback.onError("Слово не найдено");
            return;
        }

        executor.execute(() -> {
            Long finalThemeId = word.getThemeId();
            String finalThemeTitle = "Без темы";

            try {
                if (finalThemeId != null && finalThemeId > 0) {
                    Theme theme = themeDao.getThemeById(finalThemeId);
                    if (theme != null) {
                        finalThemeTitle = safeThemeTitle(theme);
                    }
                } else {
                    Theme autoTheme = findThemeForWordLocally(
                            word.getTerm(), word.getTranslation());

                    if (autoTheme != null && autoTheme.getId() != null) {
                        finalThemeId = autoTheme.getId();
                        finalThemeTitle = safeThemeTitle(autoTheme);
                    }
                }
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Не удалось определить тему слова: " + e.getMessage(), e);
            }

            Long safeThemeId = finalThemeId;
            String safeTitle = finalThemeTitle;

            mainHandler.post(() -> addUserWord(
                    safeThemeId,
                    safeTitle,
                    word.getTerm(),
                    word.getTranslation(),
                    word.getTranscription() != null ? word.getTranscription() : "",
                    word.getExampleSentence() != null ? word.getExampleSentence() : "",
                    currentUser,
                    callback
            ));
        });
    }

    public void deleteUserWord(UserWord word, Runnable onDone) {
        executor.execute(() -> {
            try {
                userWordDao.delete(word);
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Ошибка удаления слова: " + e.getMessage(), e);
            }
            mainHandler.post(onDone);
        });
    }

    public void deleteUserWordByTerm(String term,
                                     User currentUser,
                                     DataCallback<Void> callback) {
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
                userWordDao.deleteUserWordsByTerm(currentUser.getId(), safeTerm);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Ошибка удаления слова по термину: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось удалить слово"));
            }
        });
    }

    public void getUserPersonalWords(User currentUser,
                                     DataCallback<List<UserWord>> callback) {
        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<UserWord> words = userWordDao.getUserWords(currentUser.getId());
                mainHandler.post(() -> callback.onSuccess(words));
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Ошибка загрузки словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить словарь"));
            }
        });
    }

    public void getUserPersonalWordsByTheme(Long themeId,
                                            String themeTitle,
                                            User currentUser,
                                            DataCallback<List<UserWord>> callback) {
        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<UserWord> words;

                if (themeId == null) {
                    words = userWordDao.getUserWordsWithoutTheme(currentUser.getId());
                } else {
                    words = userWordDao.getUserWordsByThemeId(currentUser.getId(), themeId);
                }

                mainHandler.post(() -> callback.onSuccess(words));
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Ошибка загрузки словаря по теме: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить слова темы"));
            }
        });
    }

    public void getUserDictionaryThemeTitles(User currentUser,
                                             DataCallback<List<String>> callback) {
        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<String> themes = userWordDao.getUserDictionaryThemeTitles(
                        currentUser.getId());
                mainHandler.post(() -> callback.onSuccess(themes));
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Ошибка загрузки тем словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить темы словаря"));
            }
        });
    }

    public void isWordInPersonalDictionary(Word word,
                                           User currentUser,
                                           DataCallback<Boolean> callback) {
        if (word == null
                || word.getTerm() == null
                || word.getTerm().trim().isEmpty()) {
            callback.onSuccess(false);
            return;
        }

        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        String safeTerm = word.getTerm().trim();

        executor.execute(() -> {
            try {
                UserWord existingWord = userWordDao.findWordByUserAndTerm(
                        currentUser.getId(), safeTerm);
                mainHandler.post(() -> callback.onSuccess(existingWord != null));
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Ошибка проверки слова в словаре: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(
                        "Не удалось проверить слово в словаре"));
            }
        });
    }

    public void repairPersonalDictionaryThemes(User currentUser,
                                               DataCallback<Void> callback) {
        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<UserWord> personalWords = userWordDao.getUserWords(currentUser.getId());

                if (personalWords != null) {
                    for (UserWord userWord : personalWords) {
                        if (userWord == null || userWord.getWord() == null) continue;

                        boolean needsRepair =
                                userWord.getThemeId() == null
                                        || userWord.getThemeTitle() == null
                                        || userWord.getThemeTitle().trim().isEmpty()
                                        || userWord.getThemeTitle().equals("Без темы");

                        if (!needsRepair) continue;

                        Word sourceWord = wordDao.getWordByTerm(userWord.getWord());

                        if (sourceWord == null
                                || sourceWord.getThemeId() == null) continue;

                        Theme theme = themeDao.getThemeById(sourceWord.getThemeId());

                        if (theme == null
                                || theme.getTitle() == null
                                || theme.getTitle().trim().isEmpty()) continue;

                        userWordDao.updateUserWordTheme(
                                userWord.getId(),
                                sourceWord.getThemeId(),
                                theme.getTitle().trim()
                        );
                    }
                }

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Ошибка восстановления тем словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(
                        "Не удалось восстановить темы словаря"));
            }
        });
    }

    public void syncPersonalWords(User currentUser, DataCallback<Void> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }
        syncPersonalWords(currentUser.getId(), callback);
    }

    public void syncPersonalWords(Integer userId, DataCallback<Void> callback) {
        if (userId == null || userId == -1) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }

        executor.execute(() -> {
            try {
                List<UserWord> localWords = userWordDao.getUserWords(userId);

                Log.d("PersonalDictionaryRepository",
                        "Личный словарь загружен локально. Кол-во слов: "
                                + (localWords != null ? localWords.size() : 0));

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("PersonalDictionaryRepository",
                        "Ошибка подготовки личного словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(
                        "Не удалось загрузить личный словарь"));
            }
        });
    }

    public void syncPersonalWordsFromServer(Integer userId, DataCallback<Void> callback) {
        apiService.getUserPersonalWordsFromServer("eq." + userId, "date_added.desc")
                .enqueue(new Callback<List<UserWord>>() {
                    @Override
                    public void onResponse(Call<List<UserWord>> call,
                                           Response<List<UserWord>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<UserWord> remoteWords = response.body();

                            executor.execute(() -> {
                                try {
                                    userWordDao.deleteAllUserWordsForUser(userId);

                                    if (!remoteWords.isEmpty()) {
                                        userWordDao.insertAll(remoteWords);
                                    }

                                    mainHandler.post(() -> callback.onSuccess(null));
                                } catch (Exception e) {
                                    Log.e("PersonalDictionaryRepository",
                                            "Ошибка сохранения слов: " + e.getMessage(), e);
                                    mainHandler.post(() -> callback.onError(
                                            "Ошибка БД: " + e.getMessage()));
                                }
                            });
                        } else {
                            String errorMsg = getErrorBody(response);
                            Log.e("PersonalDictionaryRepository",
                                    "Ошибка сервера: " + errorMsg);
                            mainHandler.post(() -> callback.onError(
                                    "Ошибка сервера: " + errorMsg));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<UserWord>> call, Throwable t) {
                        Log.e("PersonalDictionaryRepository",
                                "Сбой сети: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError(
                                "Нет соединения: " + t.getMessage()));
                    }
                });
    }

    // ===== ИНТЕРФЕЙС CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}