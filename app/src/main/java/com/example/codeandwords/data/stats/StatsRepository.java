package com.example.codeandwords.data.stats;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.AchievementDao;
import com.example.codeandwords.db.DailyQuestDao;
import com.example.codeandwords.db.LessonHistoryDao;
import com.example.codeandwords.db.UserDao;
import com.example.codeandwords.db.UserStatsDao;
import com.example.codeandwords.model.AchievementWithProgress;
import com.example.codeandwords.model.DailyQuest;
import com.example.codeandwords.model.LessonHistory;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.ThemeProgressStats;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserOverallStats;
import com.example.codeandwords.model.UserStats;
import com.example.codeandwords.model.UserWordProgress;
import com.example.codeandwords.model.Word;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Репозиторий статистики: уроки, достижения, прогресс по темам, квесты, синхронизация с сервером
public class StatsRepository {

    private static final String TAG = "StatsRepository";

    private final UserDao userDao;
    private final UserStatsDao userStatsDao;
    private final LessonHistoryDao lessonHistoryDao;
    private final AchievementDao achievementDao;
    private final DailyQuestDao dailyQuestDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private StatsListener listener;

    // Кэш ID изученных слов с TTL 30 секунд
    private volatile List<Long> cachedLearnedWordIds;
    private volatile long lastLearnedIdsLoadTime = 0;
    private static final long LEARNED_IDS_CACHE_TTL = 30_000;

    public StatsRepository(UserDao userDao,
                           UserStatsDao userStatsDao,
                           LessonHistoryDao lessonHistoryDao,
                           AchievementDao achievementDao,
                           DailyQuestDao dailyQuestDao,
                           ApiService apiService,
                           ExecutorService executor,
                           Handler mainHandler) {
        this.userDao = userDao;
        this.userStatsDao = userStatsDao;
        this.lessonHistoryDao = lessonHistoryDao;
        this.achievementDao = achievementDao;
        this.dailyQuestDao = dailyQuestDao;
        this.apiService = apiService;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    // Интерфейс для делегирования операций с данными пользователя во ViewModel
    public interface StatsListener {
        String toSqlTimestamp(long millis);
        int safeInt(Integer value);
        void saveCurrentUserToPrefs(User user);
        void refreshAchievementsSync(User user, UserStats stats);
        void updateQuestProgress(String type, int value);
        void updateTeamChallengeProgressAfterLesson(int userId, int earnedXp);
        User getCurrentUser();
        void restoreCurrentUserFromPrefs();
        void addXp(int xpReward);
        void getThemes(DataCallback<List<Theme>> callback);
        void getWordsByTheme(Long themeId, DataCallback<List<Word>> callback);
    }

    public void setListener(StatsListener listener) {
        this.listener = listener;
    }

    public static class LeagueData {
        public final String title;
        public final String icon;
        public LeagueData(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    // Синхронизирует статистику с сервера при логине:
    // последовательно загружает user_stats и lesson_history
    public void syncStatsFromServer(Integer userId, DataCallback<Void> callback) {
        if (userId == null || userId <= 0) {
            if (callback != null) callback.onError("Некорректный userId");
            return;
        }

        downloadUserStatsFromServer(userId, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                downloadLessonHistoryFromServer(userId, callback);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "syncStatsFromServer: user_stats не скачаны: " + error);
                downloadLessonHistoryFromServer(userId, callback);
            }
        });
    }

    // Скачивает user_stats через upsert с merge-duplicates — сервер возвращает текущую запись
    private void downloadUserStatsFromServer(Integer userId, DataCallback<Void> callback) {
        JsonObject minimalPayload = new JsonObject();
        minimalPayload.addProperty("user_id", userId);

        apiService.upsertUserStatsRaw("user_id", minimalPayload)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().isEmpty()) {
                            Log.w(TAG, "downloadUserStats: пустой ответ или ошибка "
                                    + response.code());
                            if (callback != null) callback.onSuccess(null);
                            return;
                        }

                        JsonObject statsJson = response.body().get(0);
                        UserStats serverStats = parseUserStatsFromJson(statsJson, userId);

                        executor.execute(() -> {
                            try {
                                UserStats localStats = userStatsDao.getByUserId(userId);

                                // Мержим: берём максимальные значения для защиты от рассинхрона
                                UserStats merged = mergeStats(localStats, serverStats);
                                userStatsDao.insertOrUpdate(merged);

                                Log.d(TAG, "user_stats успешно синхронизированы с сервера");
                                if (callback != null) mainHandler.post(() -> callback.onSuccess(null));
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка сохранения user_stats: " + e.getMessage(), e);
                                if (callback != null) mainHandler.post(() -> callback.onError(e.getMessage()));
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e(TAG, "Сеть: downloadUserStats: " + t.getMessage(), t);
                        if (callback != null) mainHandler.post(() -> callback.onError(t.getMessage()));
                    }
                });
    }

    // Мержит локальную и серверную статистику: берёт максимальное значение каждого поля
    private UserStats mergeStats(UserStats local, UserStats server) {
        if (local == null) return server;
        if (server == null) return local;

        UserStats merged = new UserStats(local.userId);
        merged.loginStreak = Math.max(local.loginStreak, server.loginStreak);
        merged.lastLoginDay = Math.max(local.lastLoginDay, server.lastLoginDay);
        merged.maxXpInDay = Math.max(local.maxXpInDay, server.maxXpInDay);
        merged.currentDayXp = Math.max(local.currentDayXp, server.currentDayXp);
        merged.currentXpDay = Math.max(local.currentXpDay, server.currentXpDay);
        merged.perfectLessonsStreak = Math.max(local.perfectLessonsStreak, server.perfectLessonsStreak);
        merged.perfectLessonsTotal = Math.max(local.perfectLessonsTotal, server.perfectLessonsTotal);
        merged.lessonsBefore9 = Math.max(local.lessonsBefore9, server.lessonsBefore9);
        merged.lessonsAfter22 = Math.max(local.lessonsAfter22, server.lessonsAfter22);
        merged.fixedErrorsTotal = Math.max(local.fixedErrorsTotal, server.fixedErrorsTotal);
        merged.completedTasksTotal = Math.max(local.completedTasksTotal, server.completedTasksTotal);
        merged.sprintXpTotal = Math.max(local.sprintXpTotal, server.sprintXpTotal);
        merged.createdAt = local.createdAt > 0 ? local.createdAt : server.createdAt;
        merged.updatedAt = Math.max(local.updatedAt, server.updatedAt);
        return merged;
    }

    // Разбирает JSON-объект статистики в модель UserStats
    private UserStats parseUserStatsFromJson(JsonObject json, int userId) {
        UserStats stats = new UserStats(userId);
        if (json == null) return stats;

        stats.loginStreak = getInt(json, "login_streak");
        stats.lastLoginDay = getLong(json, "last_login_day");
        stats.maxXpInDay = getInt(json, "max_xp_in_day");
        stats.currentDayXp = getInt(json, "current_day_xp");
        stats.currentXpDay = getLong(json, "current_xp_day");
        stats.perfectLessonsStreak = getInt(json, "perfect_lessons_streak");
        stats.perfectLessonsTotal = getInt(json, "perfect_lessons_total");
        stats.lessonsBefore9 = getInt(json, "lessons_before9");
        stats.lessonsAfter22 = getInt(json, "lessons_after22");
        stats.fixedErrorsTotal = getInt(json, "fixed_errors_total");
        stats.completedTasksTotal = getInt(json, "completed_tasks_total");
        stats.sprintXpTotal = getInt(json, "sprint_xp_total");
        stats.createdAt = parseTimestamp(json, "created_at");
        stats.updatedAt = parseTimestamp(json, "updated_at");
        return stats;
    }

    private int getInt(JsonObject json, String key) {
        if (json == null || !json.has(key)) return 0;
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return 0;
        try { return e.getAsInt(); } catch (Exception ex) { return 0; }
    }

    private long getLong(JsonObject json, String key) {
        if (json == null || !json.has(key)) return 0L;
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return 0L;
        try { return e.getAsLong(); } catch (Exception ex) { return 0L; }
    }

    // Парсит timestamp из строки формата "yyyy-MM-dd HH:mm:ss" в Unix-время
    private long parseTimestamp(JsonObject json, String key) {
        if (json == null || !json.has(key)) return 0L;
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return 0L;
        try {
            String value = e.getAsString();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf.parse(value).getTime();
        } catch (Exception ex) {
            return 0L;
        }
    }

    // Загрузка lesson_history пропущена: в ApiService нет GET-метода для этого ресурса
    private void downloadLessonHistoryFromServer(Integer userId, DataCallback<Void> callback) {
        Log.d(TAG, "downloadLessonHistory: пропущено (нет GET endpoint)");
        if (callback != null) mainHandler.post(() -> callback.onSuccess(null));
    }

    // Собирает JSON-объект для отправки user_stats на сервер
    private JsonObject buildUserStatsPayload(UserStats stats) {
        JsonObject payload = new JsonObject();
        payload.addProperty("user_id", stats.userId);
        payload.addProperty("login_streak", stats.loginStreak);
        payload.addProperty("last_login_day", stats.lastLoginDay);
        payload.addProperty("max_xp_in_day", stats.maxXpInDay);
        payload.addProperty("current_day_xp", stats.currentDayXp);
        payload.addProperty("current_xp_day", stats.currentXpDay);
        payload.addProperty("perfect_lessons_streak", stats.perfectLessonsStreak);
        payload.addProperty("perfect_lessons_total", stats.perfectLessonsTotal);
        payload.addProperty("lessons_before9", stats.lessonsBefore9);
        payload.addProperty("lessons_after22", stats.lessonsAfter22);
        payload.addProperty("fixed_errors_total", stats.fixedErrorsTotal);
        payload.addProperty("completed_tasks_total", stats.completedTasksTotal);
        payload.addProperty("sprint_xp_total", stats.sprintXpTotal);

        // Защита от некорректных дат: если 0 или null — подставляем текущее время
        long createdMillis = stats.createdAt > 0 ? stats.createdAt : System.currentTimeMillis();
        long updatedMillis = stats.updatedAt > 0 ? stats.updatedAt : System.currentTimeMillis();

        String createdAt = listener != null
                ? listener.toSqlTimestamp(createdMillis)
                : formatTimestampFallback(createdMillis);
        String updatedAt = listener != null
                ? listener.toSqlTimestamp(updatedMillis)
                : formatTimestampFallback(updatedMillis);

        payload.addProperty("created_at", createdAt);
        payload.addProperty("updated_at", updatedAt);
        return payload;
    }

    // Запасной форматтер на случай отсутствия listener
    private String formatTimestampFallback(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(millis));
    }

    // Собирает JSON-объект для отправки истории урока на сервер
    private JsonObject buildLessonHistoryPayload(LessonHistory lessonHistory) {
        JsonObject payload = new JsonObject();
        payload.addProperty("user_id", lessonHistory.userId);
        payload.addProperty("lesson_type", lessonHistory.lessonType);

        if (lessonHistory.themeId != null) {
            payload.addProperty("theme_id", lessonHistory.themeId);
        } else {
            payload.add("theme_id", JsonNull.INSTANCE);
        }

        payload.addProperty("earned_xp", lessonHistory.earnedXp);
        payload.addProperty("total_words", lessonHistory.totalWords);
        payload.addProperty("mistakes_count", lessonHistory.mistakesCount);
        payload.addProperty("fixed_errors_count", lessonHistory.fixedErrorsCount);

        // Защита от некорректной даты завершения
        long finishedMillis = lessonHistory.finishedAt > 0
                ? lessonHistory.finishedAt
                : System.currentTimeMillis();

        String finishedAt = listener != null
                ? listener.toSqlTimestamp(finishedMillis)
                : formatTimestampFallback(finishedMillis);

        payload.addProperty("finished_at", finishedAt);
        payload.addProperty("was_perfect", lessonHistory.wasPerfect);
        payload.addProperty("completed_before9", lessonHistory.completedBefore9);
        payload.addProperty("completed_after22", lessonHistory.completedAfter22);

        return payload;
    }

    // Возвращает начало текущего дня в миллисекундах
    public long getStartOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public int getHourOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    public boolean isSameDay(long date1, long date2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTimeInMillis(date1);
        cal2.setTimeInMillis(date2);
        return cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
    }

    // Возвращает локальную статистику или скачивает с сервера синхронно (вызов из фонового потока)
    public UserStats getOrCreateUserStats(int userId) {
        UserStats stats = userStatsDao.getByUserId(userId);
        if (stats != null) return stats;

        // Пробуем скачать с сервера синхронно, т.к. уже в фоновом потоке
        try {
            JsonObject minimalPayload = new JsonObject();
            minimalPayload.addProperty("user_id", userId);

            Response<List<JsonObject>> response =
                    apiService.upsertUserStatsRaw("user_id", minimalPayload).execute();

            if (response.isSuccessful() && response.body() != null
                    && !response.body().isEmpty()) {
                UserStats serverStats = parseUserStatsFromJson(
                        response.body().get(0), userId);
                userStatsDao.insertOrUpdate(serverStats);
                Log.d(TAG, "getOrCreateUserStats: скачаны с сервера");
                return serverStats;
            }
        } catch (Exception e) {
            Log.w(TAG, "getOrCreateUserStats: не удалось скачать с сервера: " + e.getMessage());
        }

        // Данных нет ни локально, ни на сервере — создаём новые
        stats = new UserStats(userId);
        userStatsDao.insertOrUpdate(stats);
        syncUserStatsToRemote(stats);
        return stats;
    }

    // Определяет лигу пользователя по суммарному XP
    private LeagueData getStatisticsLeagueData(int xp) {
        if (xp >= 2500) return new LeagueData("Алмазная лига", "💎");
        if (xp >= 1200) return new LeagueData("Изумрудная лига", "💚");
        if (xp >= 600) return new LeagueData("Золотая лига", "🥇");
        if (xp >= 250) return new LeagueData("Серебряная лига", "🥈");
        return new LeagueData("Бронзовая лига", "🥉");
    }

    // Генерирует два ежедневных квеста: XP и количество игр со случайными целями
    private List<DailyQuest> generateNewQuests() {
        List<DailyQuest> newQuests = new ArrayList<>();
        long now = System.currentTimeMillis();
        Random random = new Random();

        int targetXp = (random.nextInt(3) + 1) * 50;
        newQuests.add(new DailyQuest(
                "Набрать " + targetXp + " XP", targetXp, 20, "XP", now));

        int targetGames = random.nextInt(3) + 3;
        newQuests.add(new DailyQuest(
                "Сыграть " + targetGames + " раз", targetGames, 30, "GAME_PLAYED", now));

        return newQuests;
    }

    // Считает количество изученных слов для конкретной темы из фактических данных
    private ThemeProgressStats buildThemeProgressFromActualWords(Theme theme, List<Word> actualThemeWords, List<Long> learnedWordIds) {
        int totalWords = actualThemeWords != null ? actualThemeWords.size() : 0;
        int learnedWords = 0;
        if (actualThemeWords != null && learnedWordIds != null && !learnedWordIds.isEmpty()) {
            for (Word word : actualThemeWords) {
                if (word == null || word.getId() == null) continue;
                if (learnedWordIds.contains(word.getId())) learnedWords++;
            }
        }
        int progressPercent = totalWords > 0 ? Math.max(0, Math.min(100, (learnedWords * 100) / totalWords)) : 0;
        boolean mastered = totalWords > 0 && learnedWords >= totalWords;
        return new ThemeProgressStats(theme, learnedWords, totalWords, progressPercent, mastered);
    }

    // Собирает итоговый список прогресса по темам в исходном порядке
    private void finishThemeProgress(List<Theme> originalThemes, ConcurrentHashMap<Long, ThemeProgressStats> map, DataCallback<List<ThemeProgressStats>> callback) {
        List<ThemeProgressStats> result = new ArrayList<>();
        for (Theme theme : originalThemes) {
            if (theme == null || theme.getId() == null) continue;
            ThemeProgressStats stats = map.get(theme.getId());
            if (stats != null) result.add(stats);
        }
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) return response.errorBody().string();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // Инвалидирует кэш ID изученных слов для принудительного обновления
    public void invalidateLearnedIdsCache() {
        cachedLearnedWordIds = null;
        lastLearnedIdsLoadTime = 0;
    }

    // Сбрасывает состояние репозитория при выходе из аккаунта
    public void resetState() {
        invalidateLearnedIdsCache();
        Log.d(TAG, "resetState: кэши StatsRepository сброшены");
    }

    // Возвращает ежедневные квесты; при необходимости генерирует новые
    public void getDailyQuests(DataCallback<List<DailyQuest>> callback) {
        executor.execute(() -> {
            try {
                List<DailyQuest> quests = dailyQuestDao.getAllQuests();
                long today = System.currentTimeMillis();

                // Если квесты устарели или отсутствуют — генерируем новые
                if (quests.isEmpty() || !isSameDay(quests.get(0).getDateCreated(), today)) {
                    quests = generateNewQuests();
                    dailyQuestDao.replaceAll(quests);
                }

                List<DailyQuest> finalQuests = quests;
                mainHandler.post(() -> callback.onSuccess(finalQuests));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки квестов: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки квестов"));
            }
        });
    }

    // Обновляет прогресс активных квестов указанного типа; выдаёт XP при завершении
    public void updateQuestProgress(String type, int amount) {
        executor.execute(() -> {
            try {
                List<DailyQuest> quests = dailyQuestDao.getActiveQuestsByType(type);

                for (DailyQuest q : quests) {
                    int newProgress = q.getCurrentProgress() + amount;
                    q.setCurrentProgress(newProgress);

                    if (newProgress >= q.getTargetCount()) {
                        q.setCompleted(true);
                        if (listener != null) listener.addXp(q.getXpReward());
                    }

                    dailyQuestDao.updateQuest(q);
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка обновления квестов: " + e.getMessage(), e);
            }
        });
    }

    // Возвращает список достижений с прогрессом для текущего пользователя
    public void getAchievements(User currentUser,
                                DataCallback<List<AchievementWithProgress>> callback) {
        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<AchievementWithProgress> list =
                        achievementDao.getAchievementsWithProgress(currentUser.getId());
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки достижений: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки достижений"));
            }
        });
    }

    // Возвращает таблицу лидеров из локальной БД
    public void getLeaderboard(DataCallback<List<User>> callback) {
        executor.execute(() -> {
            try {
                List<User> users = userDao.getLeaderboard();
                mainHandler.post(() -> {
                    if (users != null) callback.onSuccess(users);
                    else callback.onError("Список пуст");
                });
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки рейтинга: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки рейтинга"));
            }
        });
    }

    // Возвращает общую статистику пользователя: уроки, слова, точность, XP, лига
    public void getUserOverallStatistics(User currentUser, DataCallback<UserOverallStats> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        final int userId = currentUser.getId();

        getLearnedWordIdsForCurrentUser(currentUser, new DataCallback<List<Long>>() {
            @Override
            public void onSuccess(List<Long> learnedWordIds) {
                int learnedCount = learnedWordIds != null ? learnedWordIds.size() : 0;
                calculateAndReturnStats(currentUser, userId, learnedCount, callback);
            }

            @Override
            public void onError(String error) {
                calculateAndReturnStats(currentUser, userId, 0, callback);
            }
        });
    }

    // Рассчитывает итоговую статистику из истории уроков.
    // Точность считается по формуле «верных попыток / всего попыток»,
    // что корректно работает даже когда mistakesCount > totalWords
    // (например, в Matching-режиме ошибки считаются по каждой неверной попытке)
    private void calculateAndReturnStats(User currentUser, int userId, int learnedCount, DataCallback<UserOverallStats> callback) {
        executor.execute(() -> {
            try {
                List<LessonHistory> history = lessonHistoryDao.getAllByUser(userId);

                int totalLessons = history != null ? history.size() : 0;
                int totalWords = 0;
                int totalMistakes = 0;
                int fixedErrors = 0;

                if (history != null) {
                    for (LessonHistory item : history) {
                        if (item == null) continue;
                        totalWords += Math.max(0, item.totalWords);
                        totalMistakes += Math.max(0, item.mistakesCount);
                        fixedErrors += Math.max(0, item.fixedErrorsCount);
                    }
                }

                // Точность по попыткам: каждое слово в итоге пройдено верно (= totalWords верных),
                // а каждая ошибка — это дополнительная неверная попытка
                int totalAttempts = totalWords + totalMistakes;
                int accuracy = totalAttempts > 0
                        ? Math.max(0, Math.min(100, (totalWords * 100) / totalAttempts))
                        : 0;

                int totalXp = currentUser.getTotalXp() != null ? currentUser.getTotalXp() : 0;
                int level = currentUser.getCurrentLevel() != null
                        ? currentUser.getCurrentLevel() : ((totalXp / 100) + 1);

                LeagueData leagueData = getStatisticsLeagueData(totalXp);

                UserOverallStats stats = new UserOverallStats(
                        totalLessons, totalWords, totalMistakes, fixedErrors,
                        accuracy, learnedCount, totalXp, level,
                        leagueData.title, leagueData.icon
                );

                mainHandler.post(() -> callback.onSuccess(stats));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка расчёта статистики: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось рассчитать статистику"));
            }
        });
    }

    // Возвращает прогресс по всем темам с использованием кэша ID изученных слов
    public void getThemeProgressStatistics(User currentUser, DataCallback<List<ThemeProgressStats>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        if (cachedLearnedWordIds != null && System.currentTimeMillis() - lastLearnedIdsLoadTime < LEARNED_IDS_CACHE_TTL) {
            processThemeProgress(currentUser, new ArrayList<>(cachedLearnedWordIds), callback);
        } else {
            getLearnedWordIdsForCurrentUser(currentUser, new DataCallback<List<Long>>() {
                @Override
                public void onSuccess(List<Long> learnedWordIds) {
                    processThemeProgress(currentUser, learnedWordIds, callback);
                }
                @Override
                public void onError(String error) {
                    processThemeProgress(currentUser, new ArrayList<>(), callback);
                }
            });
        }
    }

    private void processThemeProgress(User currentUser, List<Long> learnedWordIds, DataCallback<List<ThemeProgressStats>> callback) {
        if (listener == null) {
            callback.onError("Listener не установлен");
            return;
        }

        listener.getThemes(new DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> themes) {
                calculateThemeProgressOptimized(themes != null ? themes : new ArrayList<>(), learnedWordIds, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error != null ? error : "Не удалось загрузить темы");
            }
        });
    }

    // Рассчитывает прогресс по всем темам параллельно с помощью AtomicInteger-счётчика
    private void calculateThemeProgressOptimized(
            List<Theme> themes,
            List<Long> learnedWordIds,
            DataCallback<List<ThemeProgressStats>> callback) {

        if (themes == null || themes.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        if (listener == null) {
            mainHandler.post(() -> callback.onError("Listener не установлен"));
            return;
        }

        final int totalThemes = themes.size();
        final ConcurrentHashMap<Long, ThemeProgressStats> resultMap = new ConcurrentHashMap<>();
        final AtomicInteger completed = new AtomicInteger(0);

        for (Theme theme : themes) {
            if (theme == null || theme.getId() == null) {
                int done = completed.incrementAndGet();
                if (done >= totalThemes) finishThemeProgress(themes, resultMap, callback);
                continue;
            }

            final long themeKey = theme.getId();

            listener.getWordsByTheme(themeKey, new DataCallback<List<Word>>() {
                @Override
                public void onSuccess(List<Word> words) {
                    ThemeProgressStats stats = buildThemeProgressFromActualWords(theme, words != null ? words : new ArrayList<>(), learnedWordIds);
                    resultMap.put(themeKey, stats);
                    int done = completed.incrementAndGet();
                    if (done >= totalThemes) finishThemeProgress(themes, resultMap, callback);
                }

                @Override
                public void onError(String error) {
                    ThemeProgressStats stats = buildThemeProgressFromActualWords(theme, new ArrayList<>(), learnedWordIds);
                    resultMap.put(themeKey, stats);
                    int done = completed.incrementAndGet();
                    if (done >= totalThemes) finishThemeProgress(themes, resultMap, callback);
                }
            });
        }
    }

    // Возвращает ID изученных слов из кэша или загружает с сервера
    public void getLearnedWordIdsForCurrentUser(User currentUser, DataCallback<List<Long>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        long now = System.currentTimeMillis();
        if (cachedLearnedWordIds != null && (now - lastLearnedIdsLoadTime) < LEARNED_IDS_CACHE_TTL) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>(cachedLearnedWordIds)));
            return;
        }

        apiService.getUserProgressByUser("eq." + currentUser.getId(), "word_id,is_learned", "id.asc", null)
                .enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            mainHandler.post(() -> callback.onError("Не удалось загрузить прогресс"));
                            return;
                        }
                        List<Long> learnedIds = new ArrayList<>();
                        for (UserWordProgress p : response.body()) {
                            if (p != null && p.getWordId() != null && Boolean.TRUE.equals(p.getIsLearned())) {
                                learnedIds.add(p.getWordId());
                            }
                        }
                        cachedLearnedWordIds = Collections.unmodifiableList(learnedIds);
                        lastLearnedIdsLoadTime = System.currentTimeMillis();
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>(learnedIds)));
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
                    }
                });
    }

    // Возвращает последние N уроков из локальной истории
    public void getRecentLessonHistory(int limit, User currentUser, DataCallback<List<LessonHistory>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }
        int userId = currentUser.getId();
        int safeLimit = Math.max(1, limit);
        executor.execute(() -> {
            try {
                List<LessonHistory> history = lessonHistoryDao.getRecentByUser(userId, safeLimit);
                mainHandler.post(() -> callback.onSuccess(history != null ? history : new ArrayList<>()));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Не удалось загрузить историю"));
            }
        });
    }

    // Возвращает полную историю уроков пользователя для экрана статистики
    public void getLessonHistoryForStatistics(User currentUser, DataCallback<List<LessonHistory>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }
        int userId = currentUser.getId();
        executor.execute(() -> {
            try {
                List<LessonHistory> history = lessonHistoryDao.getAllByUser(userId);
                mainHandler.post(() -> callback.onSuccess(history != null ? history : new ArrayList<>()));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Не удалось загрузить историю"));
            }
        });
    }

    // Начисляет XP пользователю, обновляет уровень и синхронизирует с сервером
    public void addXp(int xpReward) {
        User currentUser = listener != null ? listener.getCurrentUser() : null;

        if (currentUser == null && listener != null) {
            listener.restoreCurrentUserFromPrefs();
            currentUser = listener.getCurrentUser();
        }

        if (currentUser == null) return;

        // Обновляем прогресс квестов вместе с XP
        if (listener != null) {
            listener.updateQuestProgress("XP", xpReward);
            listener.updateQuestProgress("GAME_PLAYED", 1);
        }

        User finalUser = currentUser;

        executor.execute(() -> {
            try {
                int oldTotalXp = listener != null ? listener.safeInt(finalUser.getTotalXp()) : 0;
                int newTotalXp = oldTotalXp + xpReward;
                int newLevel = (newTotalXp / 100) + 1;

                userDao.updateProgress(finalUser.getId(), newTotalXp, newLevel);
                finalUser.setTotalXp(newTotalXp);
                finalUser.setCurrentLevel(newLevel);

                if (listener != null) listener.saveCurrentUserToPrefs(finalUser);

                syncUserProgressToRemote(finalUser);

                UserStats stats = getOrCreateUserStats(finalUser.getId());
                if (listener != null) listener.refreshAchievementsSync(finalUser, stats);

            } catch (Exception e) {
                Log.e(TAG, "Ошибка обновления XP: " + e.getMessage(), e);
            }
        });
    }

    // Записывает завершение урока: обновляет статистику, историю и синхронизирует с сервером
    public void recordLessonCompletion(String lessonType, Long themeId, int earnedXp,
                                       int totalWords, int mistakesCount,
                                       int fixedErrorsCount, boolean isTimedMode) {
        User currentUser = listener != null ? listener.getCurrentUser() : null;

        if (currentUser == null && listener != null) {
            listener.restoreCurrentUserFromPrefs();
            currentUser = listener.getCurrentUser();
        }

        if (currentUser == null) return;

        invalidateLearnedIdsCache();
        addXp(earnedXp);

        User finalUser = currentUser;

        executor.execute(() -> {
            try {
                int userId = finalUser.getId();
                UserStats stats = getOrCreateUserStats(userId);
                long now = System.currentTimeMillis();
                long todayStart = getStartOfDay(now);
                int hour = getHourOfDay(now);

                // Сбрасываем дневной XP при смене дня
                if (stats.currentXpDay != todayStart) {
                    stats.currentXpDay = todayStart;
                    stats.currentDayXp = 0;
                }

                stats.currentDayXp += earnedXp;
                if (stats.currentDayXp > stats.maxXpInDay) {
                    stats.maxXpInDay = stats.currentDayXp;
                }

                stats.completedTasksTotal += 1;
                stats.fixedErrorsTotal += fixedErrorsCount;

                // Серия идеальных уроков обнуляется при первой ошибке
                if (mistakesCount == 0) {
                    stats.perfectLessonsStreak += 1;
                    stats.perfectLessonsTotal += 1;
                } else {
                    stats.perfectLessonsStreak = 0;
                }

                boolean before9 = hour < 9;
                boolean after22 = hour >= 22;

                if (before9) stats.lessonsBefore9 += 1;
                if (after22) stats.lessonsAfter22 += 1;
                if (isTimedMode) stats.sprintXpTotal += earnedXp;

                stats.updatedAt = now;
                userStatsDao.insertOrUpdate(stats);
                syncUserStatsToRemote(stats);

                LessonHistory history = new LessonHistory(
                        userId, lessonType, themeId,
                        earnedXp, totalWords, mistakesCount,
                        fixedErrorsCount, now,
                        mistakesCount == 0, before9, after22
                );

                lessonHistoryDao.insert(history);
                syncLessonHistoryToRemote(history);

                if (listener != null) {
                    listener.refreshAchievementsSync(finalUser, stats);
                    listener.updateTeamChallengeProgressAfterLesson(userId, earnedXp);
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка записи урока: " + e.getMessage(), e);
            }
        });
    }

    // Фиксирует событие входа, обновляет streak и синхронизирует статистику
    public void recordLoginEvent() {
        User currentUser = listener != null ? listener.getCurrentUser() : null;
        if (currentUser == null || currentUser.getId() == null) return;

        User finalUser = currentUser;

        executor.execute(() -> {
            try {
                UserStats stats = getOrCreateUserStats(finalUser.getId());
                long now = System.currentTimeMillis();
                long todayStart = getStartOfDay(now);
                long yesterdayStart = todayStart - 24L * 60L * 60L * 1000L;

                // Повторный вход в тот же день не меняет streak
                if (stats.lastLoginDay == todayStart) return;

                // Streak продолжается только если вход был вчера
                stats.loginStreak = stats.lastLoginDay == yesterdayStart
                        ? stats.loginStreak + 1 : 1;
                stats.lastLoginDay = todayStart;
                stats.updatedAt = now;

                userStatsDao.insertOrUpdate(stats);
                syncUserStatsToRemote(stats);

                if (listener != null) listener.refreshAchievementsSync(finalUser, stats);

            } catch (Exception e) {
                Log.e(TAG, "Ошибка записи входа: " + e.getMessage(), e);
            }
        });
    }

    // Отправляет обновлённые XP и уровень пользователя на сервер
    public void syncUserProgressToRemote(User user) {
        try {
            if (user == null || user.getId() == null) return;

            JsonObject payload = new JsonObject();
            payload.addProperty("current_level",
                    user.getCurrentLevel() != null ? user.getCurrentLevel() : 1);
            payload.addProperty("total_xp",
                    user.getTotalXp() != null ? user.getTotalXp() : 0);

            apiService.updateUserProgress("eq." + user.getId(), payload)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (!response.isSuccessful()) {
                                Log.e(TAG, "Sync users failed: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Log.e(TAG, "Ошибка sync users: " + t.getMessage(), t);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Ошибка подготовки sync: " + e.getMessage(), e);
        }
    }

    // Отправляет user_stats на сервер через upsert
    public void syncUserStatsToRemote(UserStats stats) {
        JsonObject payload = buildUserStatsPayload(stats);

        apiService.upsertUserStatsRaw("user_id", payload)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "upsert user_stats failed: " + response.code()
                                    + " | " + getErrorBody(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e(TAG, "Ошибка upsert stats: " + t.getMessage(), t);
                    }
                });
    }

    // Отправляет запись об уроке в lesson_history на сервере
    public void syncLessonHistoryToRemote(LessonHistory lessonHistory) {
        JsonObject payload = buildLessonHistoryPayload(lessonHistory);

        apiService.insertLessonRaw(payload)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "insert lesson failed: " + response.code()
                                    + " | " + getErrorBody(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e(TAG, "Ошибка insert lesson: " + t.getMessage(), t);
                    }
                });
    }
}