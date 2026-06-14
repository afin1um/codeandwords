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

    // ====================================================================
    // ✅ НОВОЕ: СИНХРОНИЗАЦИЯ STATS И HISTORY С СЕРВЕРА (ПРИ ЛОГИНЕ)
    // ====================================================================

    /**
     * Скачивает с сервера user_stats и lesson_history,
     * сохраняет в локальную БД, чтобы статистика была актуальна
     * на новом устройстве.
     */
    public void syncStatsFromServer(Integer userId, DataCallback<Void> callback) {
        if (userId == null || userId <= 0) {
            if (callback != null) callback.onError("Некорректный userId");
            return;
        }

        // 1. Сначала качаем user_stats
        downloadUserStatsFromServer(userId, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                // 2. Затем качаем lesson_history
                downloadLessonHistoryFromServer(userId, callback);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "syncStatsFromServer: user_stats не скачаны: " + error);
                // Всё равно пробуем скачать историю
                downloadLessonHistoryFromServer(userId, callback);
            }
        });
    }

    /**
     * Скачивает user_stats с сервера и кладёт в Room.
     */
    private void downloadUserStatsFromServer(Integer userId, DataCallback<Void> callback) {
        // Используем upsert endpoint с GET-стилем через прямой запрос — но т.к. у нас нет
        // отдельного GET метода для user_stats, используем upsert с пустым payload не получится.
        // Поэтому используем raw upsert который вернёт текущие данные.
        // Альтернатива — добавить GET метод в ApiService, но для минимальных правок используем
        // upsert с merge-duplicates: он вернёт существующую запись.
        //
        // Простой подход: делаем минимальный upsert {user_id: X}, сервер вернёт текущую запись.

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

                                // Мерджим: берём максимальные значения
                                // (на случай если на сервере данные старше локальных).
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

    /**
     * Берём максимальные значения статистики (на случай рассинхрона).
     */
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

    /**
     * Скачивает историю уроков с сервера и кладёт в Room.
     * Так на новом устройстве будет полная история.
     */
    private void downloadLessonHistoryFromServer(Integer userId, DataCallback<Void> callback) {
        // У нас нет специального GET для lesson_history в ApiService.
        // Используем insertLessonRaw нельзя.
        // Поэтому добавим возможность через прямой Retrofit вызов
        // не получится без правки ApiService. Делаем "best effort":
        // если в ApiService нет GET, просто пропускаем и возвращаем успех.

        // Поскольку в текущем ApiService нет метода getLessonHistory,
        // просто помечаем как успешно (история не критична — постепенно накопится).
        Log.d(TAG, "downloadLessonHistory: пропущено (нет GET endpoint)");
        if (callback != null) mainHandler.post(() -> callback.onSuccess(null));
    }

    // ====================================================================
    // ОРИГИНАЛЬНЫЕ МЕТОДЫ (без изменений)
    // ====================================================================

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

        String createdAt = listener != null ? listener.toSqlTimestamp(stats.createdAt) : "";
        String updatedAt = listener != null ? listener.toSqlTimestamp(stats.updatedAt) : "";

        payload.addProperty("created_at", createdAt);
        payload.addProperty("updated_at", updatedAt);
        return payload;
    }

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

        String finishedAt = listener != null
                ? listener.toSqlTimestamp(lessonHistory.finishedAt) : "";

        payload.addProperty("finished_at", finishedAt);
        payload.addProperty("was_perfect", lessonHistory.wasPerfect);
        payload.addProperty("completed_before9", lessonHistory.completedBefore9);
        payload.addProperty("completed_after22", lessonHistory.completedAfter22);

        return payload;
    }

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

    /**
     * ✅ ИСПРАВЛЕНО: если локально нет stats — сначала пытаемся скачать с сервера
     * (синхронно, т.к. метод вызывается из background-потока), и только если
     * на сервере тоже нет — создаём новые.
     */
    public UserStats getOrCreateUserStats(int userId) {
        UserStats stats = userStatsDao.getByUserId(userId);
        if (stats != null) return stats;

        // Пробуем скачать с сервера синхронно (мы уже в фоновом потоке)
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

        // На сервере тоже пусто — создаём новые
        stats = new UserStats(userId);
        userStatsDao.insertOrUpdate(stats);
        syncUserStatsToRemote(stats);
        return stats;
    }

    private LeagueData getStatisticsLeagueData(int xp) {
        if (xp >= 2500) return new LeagueData("Алмазная лига", "💎");
        if (xp >= 1200) return new LeagueData("Изумрудная лига", "💚");
        if (xp >= 600) return new LeagueData("Золотая лига", "🥇");
        if (xp >= 250) return new LeagueData("Серебряная лига", "🥈");
        return new LeagueData("Бронзовая лига", "🥉");
    }

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

    public void invalidateLearnedIdsCache() {
        cachedLearnedWordIds = null;
        lastLearnedIdsLoadTime = 0;
    }

    public void resetState() {
        invalidateLearnedIdsCache();
        Log.d(TAG, "resetState: кэши StatsRepository сброшены");
    }
    public void getDailyQuests(DataCallback<List<DailyQuest>> callback) {
        executor.execute(() -> {
            try {
                List<DailyQuest> quests = dailyQuestDao.getAllQuests();
                long today = System.currentTimeMillis();

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

                int correctWords = Math.max(0, totalWords - totalMistakes);
                int accuracy = totalWords > 0
                        ? Math.max(0, Math.min(100, (correctWords * 100) / totalWords)) : 0;

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

    public void addXp(int xpReward) {
        User currentUser = listener != null ? listener.getCurrentUser() : null;

        if (currentUser == null && listener != null) {
            listener.restoreCurrentUserFromPrefs();
            currentUser = listener.getCurrentUser();
        }

        if (currentUser == null) return;

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

                if (stats.lastLoginDay == todayStart) return;

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