package com.example.codeandwords.data.word;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.WordDao;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WordRepository {

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
                    mainHandler.post(() -> callback.onError("Слова не найдены"));
                }
            } catch (Exception e) {
                Log.e("WordRepository",
                        "Ошибка локальной загрузки слов: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки слов"));
            }
        });
    }

    public void loadWordsByIds(List<Long> ids, DataCallback<List<Word>> callback) {
        if (ids == null || ids.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        StringBuilder idsFilter = new StringBuilder("in.(");

        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                idsFilter.append(",");
            }
            idsFilter.append(ids.get(i));
        }

        idsFilter.append(")");

        apiService.getWordsByIds(
                idsFilter.toString(),
                "*",
                "id.asc"
        ).enqueue(new Callback<List<Word>>() {
            @Override
            public void onResponse(Call<List<Word>> call,
                                   Response<List<Word>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Word> validWords = new ArrayList<>();

                    for (Word word : response.body()) {
                        if (word == null) continue;

                        String term = word.getTerm() == null
                                ? "" : word.getTerm().trim();
                        String translation = word.getTranslation() == null
                                ? "" : word.getTranslation().trim();

                        if (!term.isEmpty() && !translation.isEmpty()) {
                            validWords.add(word);
                        }
                    }

                    executor.execute(() -> {
                        try {
                            wordDao.insertAll(validWords);
                        } catch (Exception e) {
                            Log.e("WordRepository",
                                    "Ошибка кеширования слов: " + e.getMessage(), e);
                        }

                        mainHandler.post(() -> callback.onSuccess(validWords));
                    });
                } else {
                    mainHandler.post(() -> callback.onError(
                            "Слова для тренировки не найдены"));
                }
            }

            @Override
            public void onFailure(Call<List<Word>> call, Throwable t) {
                mainHandler.post(() -> callback.onError(
                        "Ошибка загрузки слов: " + t.getMessage()));
            }
        });
    }

    // ===== ИНТЕРФЕЙС CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}