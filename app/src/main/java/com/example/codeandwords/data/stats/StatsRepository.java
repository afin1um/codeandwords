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
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StatsRepository {

    private final UserDao userDao;
    private final UserStatsDao userStatsDao;
    private final LessonHistoryDao lessonHistoryDao;
    private final AchievementDao achievementDao;
    private final DailyQuestDao dailyQuestDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private StatsListener listener;

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

    // ===== ИНТЕРФЕЙС СЛУШАТЕЛЯ =====

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

    // ===== ВНУТРЕННИЙ КЛАСС =====

    public static class LeagueData {
        public final String title;
        public final String icon;

        public LeagueData(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

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

        String createdAt = listener != null
                ? listener.toSqlTimestamp(stats.createdAt) : "";
        String updatedAt = listener != null
                ? listener.toSqlTimestamp(stats.updatedAt) : "";

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

    public UserStats getOrCreateUserStats(int userId) {
        UserStats stats = userStatsDao.getByUserId(userId);
        if (stats == null) {
            stats = new UserStats(userId);
            userStatsDao.insertOrUpdate(stats);
            syncUserStatsToRemote(stats);
        }
        return stats;
    }

    public LeagueData getStatisticsLeagueData(int xp) {
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

    private ThemeProgressStats buildThemeProgressFromActualWords(
            Theme theme,
            List<Word> actualThemeWords,
            List<Long> learnedWordIds) {

        int totalWords = actualThemeWords != null ? actualThemeWords.size() : 0;
        int learnedWords = 0;

        if (actualThemeWords != null && learnedWordIds != null
                && !learnedWordIds.isEmpty()) {
            for (Word word : actualThemeWords) {
                if (word == null || word.getId() == null) continue;
                if (learnedWordIds.contains(word.getId())) learnedWords++;
            }
        }

        int progressPercent = totalWords > 0
                ? Math.max(0, Math.min(100, (learnedWords * 100) / totalWords))
                : 0;
        boolean mastered = totalWords > 0 && learnedWords >= totalWords;

        return new ThemeProgressStats(theme, learnedWords, totalWords, progressPercent, mastered);
    }

    private ThemeProgressStats buildThemeProgress(Theme theme,
                                                  List<Word> words,
                                                  List<Long> learnedIds) {
        int total = words != null ? words.size() : 0;
        int learned = 0;

        if (words != null && learnedIds != null) {
            for (Word word : words) {
                if (word != null && word.getId() != null
                        && learnedIds.contains(word.getId())) {
                    learned++;
                }
            }
        }

        int percent = total > 0 ? Math.min(100, (learned * 100) / total) : 0;
        boolean mastered = total > 0 && learned >= total;

        return new ThemeProgressStats(theme, learned, total, percent, mastered);
    }

    private void sortThemeProgressAndReturn(List<ThemeProgressStats> result,
                                            DataCallback<List<ThemeProgressStats>> callback) {
        if (result == null) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        result.sort((a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;

            Theme themeA = a.getTheme();
            Theme themeB = b.getTheme();

            Long idA = themeA != null ? themeA.getId() : Long.MAX_VALUE;
            Long idB = themeB != null ? themeB.getId() : Long.MAX_VALUE;

            if (idA == null) idA = Long.MAX_VALUE;
            if (idB == null) idB = Long.MAX_VALUE;

            return Long.compare(idA, idB);
        });

        mainHandler.post(() -> callback.onSuccess(result));
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e("StatsRepository", "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // ===== КВЕСТЫ =====

    public void getDailyQuests(DataCallback<List<DailyQuest>> callback) {
        executor.execute(() -> {
            try {
                List<DailyQuest> quests = dailyQuestDao.getAllQuests();
                long today = System.currentTimeMillis();

                if (quests.isEmpty()
                        || !isSameDay(quests.get(0).getDateCreated(), today)) {
                    dailyQuestDao.deleteAll();
                    quests = generateNewQuests();
                    dailyQuestDao.insertAll(quests);
                }

                List<DailyQuest> finalQuests = quests;
                mainHandler.post(() -> callback.onSuccess(finalQuests));
            } catch (Exception e) {
                Log.e("StatsRepository",
                        "Ошибка загрузки квестов: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки квестов"));
            }
        });
    }

    public void updateQuestProgress(String type, int amount) {
        executor.execute(() -> {
            try {
                List<DailyQuest> quests = dailyQuestDao.getAllQuests();
                for (DailyQuest q : quests) {
                    if (!q.isCompleted() && q.getType().equals(type)) {
                        int newProgress = q.getCurrentProgress() + amount;
                        q.setCurrentProgress(newProgress);

                        if (newProgress >= q.getTargetCount()) {
                            q.setCompleted(true);
                            if (listener != null) {
                                listener.addXp(q.getXpReward());
                            }
                        }

                        dailyQuestDao.updateQuest(q);
                    }
                }
            } catch (Exception e) {
                Log.e("StatsRepository",
                        "Ошибка обновления квестов: " + e.getMessage(), e);
            }
        });
    }

    // ===== ДОСТИЖЕНИЯ И ЛИДЕРБОРД =====

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

                if (list != null) {
                    for (AchievementWithProgress item : list) {
                        Log.d("StatsRepository",
                                "id=" + item.id
                                        + ", title=" + item.title
                                        + ", isUnlocked=" + item.isUnlocked
                                        + ", progress=" + item.currentProgress
                                        + "/" + item.maxProgress);
                    }
                }

                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                Log.e("StatsRepository",
                        "Ошибка загрузки достижений: " + e.getMessage(), e);
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
                Log.e("StatsRepository",
                        "Ошибка загрузки рейтинга: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки рейтинга"));
            }
        });
    }

    // ===== СТАТИСТИКА =====

    public void getUserOverallStatistics(User currentUser,
                                         DataCallback<UserOverallStats> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        User user = currentUser;
        int userId = user.getId();

        getLearnedWordIdsForCurrentUser(currentUser,
                new DataCallback<List<Long>>() {
                    @Override
                    public void onSuccess(List<Long> learnedWordIds) {
                        buildOverallStats(user, userId, learnedWordIds, callback);
                    }

                    @Override
                    public void onError(String error) {
                        buildOverallStats(user, userId, new ArrayList<>(), callback);
                    }
                });
    }

    private void buildOverallStats(User user, int userId,
                                   List<Long> learnedWordIds,
                                   DataCallback<UserOverallStats> callback) {
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
                        ? Math.max(0, Math.min(100, (correctWords * 100) / totalWords))
                        : 0;

                int totalXp = user.getTotalXp() != null ? user.getTotalXp() : 0;
                int level = user.getCurrentLevel() != null
                        ? user.getCurrentLevel()
                        : ((totalXp / 100) + 1);

                LeagueData leagueData = getStatisticsLeagueData(totalXp);

                UserOverallStats stats = new UserOverallStats(
                        totalLessons, totalWords, totalMistakes, fixedErrors,
                        accuracy,
                        learnedWordIds != null ? learnedWordIds.size() : 0,
                        totalXp, level,
                        leagueData.title, leagueData.icon
                );

                mainHandler.post(() -> callback.onSuccess(stats));
            } catch (Exception e) {
                Log.e("StatsRepository",
                        "Ошибка расчёта статистики: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(
                        "Не удалось рассчитать статистику"));
            }
        });
    }

    public void getThemeProgressStatistics(User currentUser,
                                           DataCallback<List<ThemeProgressStats>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        getLearnedWordIdsForCurrentUser(currentUser, new DataCallback<List<Long>>() {
            @Override
            public void onSuccess(List<Long> learnedWordIds) {
                if (listener == null) {
                    callback.onError("Listener не установлен");
                    return;
                }

                listener.getThemes(new DataCallback<List<Theme>>() {
                    @Override
                    public void onSuccess(List<Theme> themes) {
                        calculateThemeProgressFromActualWords(
                                themes != null ? themes : new ArrayList<>(),
                                learnedWordIds != null ? learnedWordIds : new ArrayList<>(),
                                callback
                        );
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error != null ? error : "Не удалось загрузить темы");
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error != null
                        ? error : "Не удалось загрузить изученные слова");
            }
        });
    }

    public void getLearnedWordIdsForCurrentUser(User currentUser,
                                                DataCallback<List<Long>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        apiService.getUserProgressByUser(
                "eq." + currentUser.getId(),
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError(
                            "Не удалось загрузить прогресс пользователя"));
                    return;
                }

                List<Long> learnedIds = new ArrayList<>();

                for (UserWordProgress progress : response.body()) {
                    if (progress == null) continue;
                    if (progress.getWordId() != null && progress.getIsLearned()) {
                        if (!learnedIds.contains(progress.getWordId())) {
                            learnedIds.add(progress.getWordId());
                        }
                    }
                }

                mainHandler.post(() -> callback.onSuccess(learnedIds));
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError(
                        "Ошибка загрузки прогресса: " + (t.getMessage() != null
                                ? t.getMessage() : "")));
            }
        });
    }

    private void calculateThemeProgressFromActualWords(
            List<Theme> themes,
            List<Long> learnedWordIds,
            DataCallback<List<ThemeProgressStats>> callback) {

        if (themes == null || themes.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        List<ThemeProgressStats> result = new ArrayList<>();
        final int[] completed = {0};
        final int totalThemes = themes.size();

        for (Theme theme : themes) {
            if (theme == null || theme.getId() == null) {
                completed[0]++;
                if (completed[0] >= totalThemes) {
                    sortThemeProgressAndReturn(result, callback);
                }
                continue;
            }

            if (listener == null) {
                completed[0]++;
                if (completed[0] >= totalThemes) {
                    sortThemeProgressAndReturn(result, callback);
                }
                continue;
            }

            listener.getWordsByTheme(theme.getId(), new DataCallback<List<Word>>() {
                @Override
                public void onSuccess(List<Word> words) {
                    ThemeProgressStats stats = buildThemeProgressFromActualWords(
                            theme,
                            words != null ? words : new ArrayList<>(),
                            learnedWordIds != null ? learnedWordIds : new ArrayList<>()
                    );
                    result.add(stats);
                    completed[0]++;
                    if (completed[0] >= totalThemes) {
                        sortThemeProgressAndReturn(result, callback);
                    }
                }

                @Override
                public void onError(String error) {
                    ThemeProgressStats stats = buildThemeProgressFromActualWords(
                            theme, new ArrayList<>(),
                            learnedWordIds != null ? learnedWordIds : new ArrayList<>()
                    );
                    result.add(stats);
                    completed[0]++;
                    if (completed[0] >= totalThemes) {
                        sortThemeProgressAndReturn(result, callback);
                    }
                }
            });
        }
    }

    public void getRecentLessonHistory(int limit, User currentUser,
                                       DataCallback<List<LessonHistory>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        int userId = currentUser.getId();
        int safeLimit = Math.max(1, limit);

        executor.execute(() -> {
            try {
                List<LessonHistory> history =
                        lessonHistoryDao.getRecentByUser(userId, safeLimit);
                mainHandler.post(() -> callback.onSuccess(
                        history != null ? history : new ArrayList<>()));
            } catch (Exception e) {
                Log.e("StatsRepository",
                        "Ошибка загрузки истории: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(
                        "Не удалось загрузить историю занятий"));
            }
        });
    }

    public void getLessonHistoryForStatistics(User currentUser,
                                              DataCallback<List<LessonHistory>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        int userId = currentUser.getId();

        executor.execute(() -> {
            try {
                List<LessonHistory> history = lessonHistoryDao.getAllByUser(userId);
                mainHandler.post(() -> callback.onSuccess(
                        history != null ? history : new ArrayList<>()));
            } catch (Exception e) {
                Log.e("StatsRepository",
                        "Ошибка загрузки LessonHistory: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(
                        "Не удалось загрузить историю занятий"));
            }
        });
    }

    // ===== СИНХРОНИЗАЦИЯ =====

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
                int oldTotalXp = listener != null
                        ? listener.safeInt(finalUser.getTotalXp()) : 0;
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
                Log.e("StatsRepository",
                        "Ошибка обновления XP: " + e.getMessage(), e);
            }
        });
    }

    public void recordLessonCompletion(String lessonType,
                                       Long themeId,
                                       int earnedXp,
                                       int totalWords,
                                       int mistakesCount,
                                       int fixedErrorsCount,
                                       boolean isTimedMode) {
        User currentUser = listener != null ? listener.getCurrentUser() : null;

        if (currentUser == null && listener != null) {
            listener.restoreCurrentUserFromPrefs();
            currentUser = listener.getCurrentUser();
        }

        if (currentUser == null) return;

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
                Log.e("StatsRepository",
                        "Ошибка записи урока: " + e.getMessage(), e);
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
                Log.e("StatsRepository",
                        "Ошибка записи входа: " + e.getMessage(), e);
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
                                Log.e("StatsRepository",
                                        "Sync users failed: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Log.e("StatsRepository",
                                    "Ошибка sync users: " + t.getMessage(), t);
                        }
                    });
        } catch (Exception e) {
            Log.e("StatsRepository",
                    "Ошибка подготовки sync: " + e.getMessage(), e);
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
                            Log.e("StatsRepository",
                                    "upsert user_stats failed: " + response.code()
                                            + " | " + getErrorBody(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e("StatsRepository",
                                "Ошибка upsert stats: " + t.getMessage(), t);
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
                            Log.e("StatsRepository",
                                    "insert lesson failed: " + response.code()
                                            + " | " + getErrorBody(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e("StatsRepository",
                                "Ошибка insert lesson: " + t.getMessage(), t);
                    }
                });
    }

    // ===== ИНТЕРФЕЙС CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}