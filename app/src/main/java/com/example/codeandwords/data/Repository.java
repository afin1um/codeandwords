package com.example.codeandwords.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.api.RetrofitClient;
import com.example.codeandwords.db.AchievementDao;
import com.example.codeandwords.db.AppDatabase;
import com.example.codeandwords.db.DailyQuestDao;
import com.example.codeandwords.db.LessonHistoryDao;
import com.example.codeandwords.db.ThemeDao;
import com.example.codeandwords.db.UserDao;
import com.example.codeandwords.db.UserStatsDao;
import com.example.codeandwords.db.UserWordDao;
import com.example.codeandwords.db.WordDao;
import com.example.codeandwords.model.Achievement;
import com.example.codeandwords.model.AchievementWithProgress;
import com.example.codeandwords.model.DailyQuest;
import com.example.codeandwords.model.LessonHistory;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserAchievement;
import com.example.codeandwords.model.UserStats;
import com.example.codeandwords.model.UserWord;
import com.example.codeandwords.model.UserWordProgress;
import com.example.codeandwords.model.Word;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Repository {

    private static final String PREFS_NAME = "codeandwords_prefs";
    private static final String KEY_USER_ID = "current_user_id";
    private static final String KEY_USERNAME = "current_username";
    private static final String KEY_EMAIL = "current_email";
    private static final String KEY_PASSWORD_HASH = "current_password_hash";
    private static final String KEY_CURRENT_LEVEL = "current_level";
    private static final String KEY_TOTAL_XP = "total_xp";
    private static final String KEY_ROLE = "role";

    private final UserDao userDao;
    private final ThemeDao themeDao;
    private final WordDao wordDao;
    private final AchievementDao achievementDao;
    private final DailyQuestDao dailyQuestDao;
    private final UserWordDao userWordDao;
    private final UserStatsDao userStatsDao;
    private final LessonHistoryDao lessonHistoryDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final SharedPreferences prefs;
    private final Context appContext;

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    private static User currentUser;

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    public interface OnUserIdRetrieved {
        void onRetrieved(Integer userId);
    }

    public Repository(Context context) {
        this.appContext = context.getApplicationContext();

        AppDatabase db = AppDatabase.getDatabase(appContext);
        this.userDao = db.userDao();
        this.themeDao = db.themeDao();
        this.wordDao = db.wordDao();
        this.achievementDao = db.achievementDao();
        this.dailyQuestDao = db.dailyQuestDao();
        this.userWordDao = db.userWordDao();
        this.userStatsDao = db.userStatsDao();
        this.lessonHistoryDao = db.lessonHistoryDao();
        this.apiService = RetrofitClient.getApiService();
        this.executor = AppDatabase.databaseWriteExecutor;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        restoreCurrentUserFromPrefs();
        initTtsIfNeeded();
    }

    private void initTtsIfNeeded() {
        if (tts != null) return;

        tts = new TextToSpeech(appContext, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            } else {
                isTtsReady = false;
                Log.e("TTS", "Ошибка инициализации TextToSpeech");
            }
        });
    }

    private void restoreCurrentUserFromPrefs() {
        if (currentUser != null) return;

        int savedUserId = prefs.getInt(KEY_USER_ID, -1);
        if (savedUserId == -1) return;

        User restoredUser = new User();
        restoredUser.setId(savedUserId);
        restoredUser.setUsername(prefs.getString(KEY_USERNAME, ""));
        restoredUser.setEmail(prefs.getString(KEY_EMAIL, ""));
        restoredUser.setPasswordHash(prefs.getString(KEY_PASSWORD_HASH, ""));
        restoredUser.setCurrentLevel(prefs.getInt(KEY_CURRENT_LEVEL, 1));
        restoredUser.setTotalXp(prefs.getInt(KEY_TOTAL_XP, 0));
        restoredUser.setRole(prefs.getString(KEY_ROLE, "user"));

        currentUser = restoredUser;
    }

    private void saveCurrentUserToPrefs(User user) {
        if (user == null || user.getId() == null) return;

        prefs.edit()
                .putInt(KEY_USER_ID, user.getId())
                .putString(KEY_USERNAME, user.getUsername())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_PASSWORD_HASH, user.getPasswordHash())
                .putInt(KEY_CURRENT_LEVEL, user.getCurrentLevel() != null ? user.getCurrentLevel() : 1)
                .putInt(KEY_TOTAL_XP, user.getTotalXp() != null ? user.getTotalXp() : 0)
                .putString(KEY_ROLE, user.getRole() != null ? user.getRole() : "user")
                .apply();
    }

    private void clearCurrentUserPrefs() {
        prefs.edit().clear().apply();
    }

    private void cacheUserSafely(User user) {
        if (user == null) return;

        executor.execute(() -> {
            try {
                userDao.insertUser(user);
            } catch (Exception e) {
                Log.e("Repository", "Ошибка кэширования пользователя: " + e.getMessage(), e);
            }
        });
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return password;
        }
    }

    private String toSqlTimestamp(long millis) {
        if (millis <= 0) {
            millis = System.currentTimeMillis();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(millis));
    }

    private String toSqlDate(long millis) {
        if (millis <= 0) {
            millis = System.currentTimeMillis();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(millis));
    }

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
        payload.addProperty("created_at", toSqlTimestamp(stats.createdAt));
        payload.addProperty("updated_at", toSqlTimestamp(stats.updatedAt));
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
        payload.addProperty("finished_at", toSqlTimestamp(lessonHistory.finishedAt));
        payload.addProperty("was_perfect", lessonHistory.wasPerfect);
        payload.addProperty("completed_before9", lessonHistory.completedBefore9);
        payload.addProperty("completed_after22", lessonHistory.completedAfter22);

        return payload;
    }

    private JsonObject buildUserAchievementPayload(UserAchievement userAchievement) {
        JsonObject payload = new JsonObject();
        payload.addProperty("user_id", userAchievement.userId);
        payload.addProperty("achievement_id", userAchievement.achievementId);
        payload.addProperty("current_progress", safeInt(userAchievement.currentProgress));
        payload.addProperty("is_unlocked", userAchievement.isUnlocked);
        payload.addProperty("is_new", userAchievement.isNew);

        long timestamp = userAchievement.dateReceived > 0
                ? userAchievement.dateReceived
                : System.currentTimeMillis();

        payload.addProperty("received_at", toSqlTimestamp(timestamp));
        payload.addProperty("unlocked_at", toSqlTimestamp(timestamp));
        payload.addProperty("last_updated_at", toSqlTimestamp(System.currentTimeMillis()));

        return payload;
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e("Repository", "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "empty error body";
    }

    public void register(User user, DataCallback<User> callback) {
        String rawPassword = user.getPasswordHash().trim();
        String cleanEmail = user.getEmail().trim().toLowerCase();
        String hashedPassword = (rawPassword.length() == 64) ? rawPassword : hashPassword(rawPassword);

        user.setEmail(cleanEmail);
        user.setPasswordHash(hashedPassword);

        apiService.login("eq." + cleanEmail, null).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    mainHandler.post(() -> callback.onError("Пользователь с таким Email уже существует"));
                } else {
                    performActualRegistration(user, callback);
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка проверки почты: " + t.getMessage()));
            }
        });
    }

    private void performActualRegistration(User user, DataCallback<User> callback) {
        apiService.register(user).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    User regUser = response.body().get(0);
                    currentUser = regUser;
                    saveCurrentUserToPrefs(regUser);
                    cacheUserSafely(regUser);
                    recordLoginEvent();
                    mainHandler.post(() -> callback.onSuccess(regUser));
                } else if (response.code() == 409) {
                    mainHandler.post(() -> callback.onError("Этот аккаунт уже зарегистрирован"));
                } else {
                    mainHandler.post(() -> callback.onError("Ошибка сервера: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Сбой сети: " + t.getMessage()));
            }
        });
    }

    public void login(User user, DataCallback<User> callback) {
        String cleanEmail = user.getEmail().trim().toLowerCase();
        String rawPassword = user.getPasswordHash().trim();
        String hashedPassword = (rawPassword.length() == 64) ? rawPassword : hashPassword(rawPassword);

        apiService.login("eq." + cleanEmail, "eq." + hashedPassword).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    currentUser = response.body().get(0);
                    saveCurrentUserToPrefs(currentUser);
                    cacheUserSafely(currentUser);
                    recordLoginEvent();
                    mainHandler.post(() -> callback.onSuccess(currentUser));
                } else {
                    mainHandler.post(() -> callback.onError("Неверный логин или пароль"));
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
            }
        });
    }

    public void logout(Runnable callback) {
        currentUser = null;
        clearCurrentUserPrefs();
        mainHandler.post(callback);
    }

    public void getCurrentUser(DataCallback<User> callback) {
        if (currentUser != null) {
            callback.onSuccess(currentUser);
            return;
        }

        restoreCurrentUserFromPrefs();

        if (currentUser != null) {
            callback.onSuccess(currentUser);
        } else {
            callback.onError("Ошибка авторизации");
        }
    }

    public void getCurrentUserId(OnUserIdRetrieved callback) {
        if (currentUser != null && currentUser.getId() != null) {
            callback.onRetrieved(currentUser.getId());
            return;
        }

        restoreCurrentUserFromPrefs();

        if (currentUser != null && currentUser.getId() != null) {
            callback.onRetrieved(currentUser.getId());
        } else {
            callback.onRetrieved(-1);
        }
    }

    public void deleteUserWord(UserWord word, Runnable onDone) {
        executor.execute(() -> {
            try {
                userWordDao.delete(word);
            } catch (Exception e) {
                Log.e("Repository", "Ошибка удаления слова: " + e.getMessage(), e);
            }
            mainHandler.post(onDone);
        });
    }

    public void addUserWord(String word, String translation, String transcription, String notes, DataCallback<Void> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        String safeWord = word == null ? "" : word.trim();
        String safeTranslation = translation == null ? "" : translation.trim();
        String safeTranscription = transcription == null ? "" : transcription.trim();
        String safeNotes = notes == null ? "" : notes.trim();

        if (safeWord.isEmpty() || safeTranslation.isEmpty()) {
            callback.onError("Заполните слово и перевод");
            return;
        }

        executor.execute(() -> {
            try {
                UserWord existing = userWordDao.findWordByUserAndTerm(currentUser.getId(), safeWord);

                if (existing != null) {
                    mainHandler.post(() -> callback.onError("Это слово уже есть в личном словаре"));
                    return;
                }

                UserWord userWord = new UserWord(
                        currentUser.getId(),
                        safeWord,
                        safeTranslation,
                        safeTranscription,
                        safeNotes
                );

                userWordDao.insert(userWord);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка добавления слова: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось добавить слово"));
            }
        });
    }

    public void addWordToPersonalDictionary(Word word, DataCallback<Void> callback) {
        if (word == null) {
            callback.onError("Слово не найдено");
            return;
        }

        addUserWord(
                word.getTerm(),
                word.getTranslation(),
                word.getTranscription() != null ? word.getTranscription() : "",
                word.getExampleSentence() != null ? word.getExampleSentence() : "",
                callback
        );
    }

    public void getUserPersonalWords(DataCallback<List<UserWord>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<UserWord> words = userWordDao.getUserWords(currentUser.getId());
                mainHandler.post(() -> callback.onSuccess(words));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка загрузки словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить словарь"));
            }
        });
    }

    public void getThemes(DataCallback<List<Theme>> callback) {
        executor.execute(() -> {
            try {
                List<Theme> themes = themeDao.getAllThemes();
                mainHandler.post(() -> {
                    if (!themes.isEmpty()) callback.onSuccess(themes);
                    else callback.onError("Темы не найдены");
                });
            } catch (Exception e) {
                Log.e("Repository", "Ошибка загрузки тем: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки тем"));
            }
        });
    }

    public void getThemeById(long themeId, DataCallback<Theme> callback) {
        executor.execute(() -> {
            try {
                Theme theme = themeDao.getThemeById(themeId);
                mainHandler.post(() -> {
                    if (theme != null) callback.onSuccess(theme);
                    else callback.onError("Тема не найдена");
                });
            } catch (Exception e) {
                Log.e("Repository", "Ошибка загрузки темы: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки темы"));
            }
        });
    }

    public void getWordsByTheme(Long themeId, DataCallback<List<Word>> callback) {
        executor.execute(() -> {
            try {
                List<Word> words = wordDao.getWordsByTheme(themeId);
                mainHandler.post(() -> {
                    if (!words.isEmpty()) callback.onSuccess(words);
                    else callback.onError("Слова не найдены");
                });
            } catch (Exception e) {
                Log.e("Repository", "Ошибка загрузки слов: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки слов"));
            }
        });
    }

    public void addXp(int xpReward) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }
        if (currentUser == null) return;

        updateQuestProgress("XP", xpReward);
        updateQuestProgress("GAME_PLAYED", 1);

        executor.execute(() -> {
            try {
                int oldTotalXp = safeInt(currentUser.getTotalXp());
                int newTotalXp = oldTotalXp + xpReward;
                int newLevel = (newTotalXp / 100) + 1;

                userDao.updateProgress(currentUser.getId(), newTotalXp, newLevel);
                currentUser.setTotalXp(newTotalXp);
                currentUser.setCurrentLevel(newLevel);
                saveCurrentUserToPrefs(currentUser);
                syncUserProgressToRemote(currentUser);

                UserStats stats = getOrCreateUserStats(currentUser.getId());
                refreshAchievementsSync(currentUser, stats);
            } catch (Exception e) {
                Log.e("Repository", "Ошибка обновления XP: " + e.getMessage(), e);
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
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }
        if (currentUser == null) return;

        addXp(earnedXp);

        executor.execute(() -> {
            try {
                int userId = currentUser.getId();
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
                        userId,
                        lessonType,
                        themeId,
                        earnedXp,
                        totalWords,
                        mistakesCount,
                        fixedErrorsCount,
                        now,
                        mistakesCount == 0,
                        before9,
                        after22
                );

                lessonHistoryDao.insert(history);
                syncLessonHistoryToRemote(history);

                refreshAchievementsSync(currentUser, stats);
            } catch (Exception e) {
                Log.e("Repository", "Ошибка записи урока: " + e.getMessage(), e);
            }
        });
    }

    private void recordLoginEvent() {
        if (currentUser == null || currentUser.getId() == null) return;

        executor.execute(() -> {
            try {
                UserStats stats = getOrCreateUserStats(currentUser.getId());
                long now = System.currentTimeMillis();
                long todayStart = getStartOfDay(now);
                long yesterdayStart = todayStart - 24L * 60L * 60L * 1000L;

                if (stats.lastLoginDay == todayStart) {
                    refreshAchievementsSync(currentUser, stats);
                    return;
                }

                if (stats.lastLoginDay == yesterdayStart) {
                    stats.loginStreak += 1;
                } else {
                    stats.loginStreak = 1;
                }

                stats.lastLoginDay = todayStart;
                stats.updatedAt = now;
                userStatsDao.insertOrUpdate(stats);
                syncUserStatsToRemote(stats);
                refreshAchievementsSync(currentUser, stats);
            } catch (Exception e) {
                Log.e("Repository", "Ошибка записи входа: " + e.getMessage(), e);
            }
        });
    }

    private UserStats getOrCreateUserStats(int userId) {
        UserStats stats = userStatsDao.getByUserId(userId);
        if (stats == null) {
            stats = new UserStats(userId);
            userStatsDao.insertOrUpdate(stats);
            syncUserStatsToRemote(stats);
        }
        return stats;
    }

    private long getStartOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private int getHourOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    private int getAchievementProgress(Achievement achievement, User user, UserStats stats) {
        String type = achievement.getConditionType();

        switch (type) {
            case "LOGIN_STREAK":
                return stats.loginStreak;
            case "MAX_XP_DAY":
                return stats.maxXpInDay;
            case "PERFECT_STREAK":
                return stats.perfectLessonsStreak;
            case "EARLY_BIRD":
                return stats.lessonsBefore9;
            case "ERROR_FIXER":
                return stats.fixedErrorsTotal;
            case "TASK_MASTER":
                return stats.completedTasksTotal;
            case "NIGHT_OWL":
                return stats.lessonsAfter22;
            case "TOTAL_XP":
                return safeInt(user.getTotalXp());
            case "PERFECT_TOTAL":
                return stats.perfectLessonsTotal;
            case "SPRINT_XP":
                return stats.sprintXpTotal;
            default:
                return 0;
        }
    }

    private void refreshAchievementsSync(User user, UserStats stats) {
        try {
            List<Achievement> allAchievements = achievementDao.getAllAchievements();
            int achievementRewardXp = 0;
            long now = System.currentTimeMillis();

            for (Achievement achievement : allAchievements) {
                int progress = getAchievementProgress(achievement, user, stats);
                int maxProgress = safeInt(achievement.getMaxProgress());
                int normalizedProgress = Math.min(progress, maxProgress > 0 ? maxProgress : progress);

                UserAchievement userAchievement =
                        achievementDao.getUserAchievement(user.getId(), achievement.getId().intValue());

                if (userAchievement == null) {
                    boolean unlockedNow = progress >= safeInt(achievement.getConditionValue());

                    userAchievement = new UserAchievement(
                            user.getId().longValue(),
                            achievement.getId(),
                            now,
                            normalizedProgress,
                            unlockedNow,
                            unlockedNow
                    );

                    achievementDao.insertUserAchievement(userAchievement);
                    syncUserAchievementToRemote(userAchievement);

                    if (unlockedNow) {
                        achievementRewardXp += safeInt(achievement.getXpReward());
                    }
                } else {
                    boolean wasUnlocked = userAchievement.isUnlocked;
                    userAchievement.currentProgress = normalizedProgress;

                    if (!wasUnlocked && progress >= safeInt(achievement.getConditionValue())) {
                        userAchievement.isUnlocked = true;
                        userAchievement.isNew = true;
                        userAchievement.dateReceived = now;
                        achievementRewardXp += safeInt(achievement.getXpReward());
                    }

                    achievementDao.updateUserAchievement(userAchievement);
                    syncUserAchievementToRemote(userAchievement);
                }
            }

            if (achievementRewardXp > 0) {
                grantAchievementXp(achievementRewardXp);
            }
        } catch (Exception e) {
            Log.e("Repository", "Ошибка обновления достижений: " + e.getMessage(), e);
        }
    }

    private void grantAchievementXp(int xpReward) {
        if (currentUser == null) return;

        int oldTotalXp = safeInt(currentUser.getTotalXp());
        int newTotalXp = oldTotalXp + xpReward;
        int newLevel = (newTotalXp / 100) + 1;

        userDao.updateProgress(currentUser.getId(), newTotalXp, newLevel);
        currentUser.setTotalXp(newTotalXp);
        currentUser.setCurrentLevel(newLevel);
        saveCurrentUserToPrefs(currentUser);
        syncUserProgressToRemote(currentUser);
    }

    private void syncUserProgressToRemote(User user) {
        try {
            User patchUser = new User();
            patchUser.setCurrentLevel(user.getCurrentLevel());
            patchUser.setTotalXp(user.getTotalXp());

            apiService.updateUserProgress("eq." + user.getId(), patchUser).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (!response.isSuccessful()) {
                        Log.e("Repository", "Не удалось синхронизировать users: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Log.e("Repository", "Ошибка sync users: " + t.getMessage(), t);
                }
            });
        } catch (Exception e) {
            Log.e("Repository", "Ошибка подготовки sync users: " + e.getMessage(), e);
        }
    }

    private void syncUserStatsToRemote(UserStats stats) {
        JsonObject payload = buildUserStatsPayload(stats);

        apiService.upsertUserStatsRaw("user_id", payload).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (!response.isSuccessful()) {
                    Log.e("Repository", "Не удалось создать/обновить user_stats: "
                            + response.code() + " | " + getErrorBody(response));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка upsert user_stats: " + t.getMessage(), t);
            }
        });
    }

    private void syncLessonHistoryToRemote(LessonHistory lessonHistory) {
        JsonObject payload = buildLessonHistoryPayload(lessonHistory);

        apiService.insertLessonRaw(payload).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (!response.isSuccessful()) {
                    Log.e("Repository", "Не удалось вставить lesson_history: "
                            + response.code() + " | " + getErrorBody(response));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка insert lesson_history: " + t.getMessage(), t);
            }
        });
    }

    private void syncUserAchievementToRemote(UserAchievement userAchievement) {
        String userFilter = "eq." + userAchievement.userId;
        String achievementFilter = "eq." + userAchievement.achievementId;
        JsonObject payload = buildUserAchievementPayload(userAchievement);

        apiService.getUserAchievementRecordRaw(userFilter, achievementFilter).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    apiService.updateUserAchievementRaw(userFilter, achievementFilter, payload)
                            .enqueue(new Callback<Void>() {
                                @Override
                                public void onResponse(Call<Void> call, Response<Void> response) {
                                    if (!response.isSuccessful()) {
                                        Log.e("Repository", "Не удалось обновить user_achievements: "
                                                + response.code() + " | " + getErrorBody(response));
                                    }
                                }

                                @Override
                                public void onFailure(Call<Void> call, Throwable t) {
                                    Log.e("Repository", "Ошибка update user_achievements: " + t.getMessage(), t);
                                }
                            });
                } else {
                    apiService.insertUserAchievementRaw(payload).enqueue(new Callback<List<JsonObject>>() {
                        @Override
                        public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                            if (!response.isSuccessful()) {
                                Log.e("Repository", "Не удалось создать user_achievements: "
                                        + response.code() + " | " + getErrorBody(response));
                            }
                        }

                        @Override
                        public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                            Log.e("Repository", "Ошибка create user_achievements: " + t.getMessage(), t);
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка get user_achievements: " + t.getMessage(), t);
            }
        });
    }

    public void incrementWordProgress(Integer userId, Long wordId) {
        String filter = "user_id=eq." + userId + "&word_id=eq." + wordId;
        apiService.getUserProgress(filter).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isEmpty()) {
                        UserWordProgress newProg = new UserWordProgress();
                        newProg.setUserId(userId.longValue());
                        newProg.setWordId(wordId);
                        newProg.setCorrectAnswersCount(1);
                        newProg.setIsLearned(false);

                        apiService.createWordProgress(newProg).enqueue(new Callback<List<UserWordProgress>>() {
                            @Override
                            public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> r) {
                            }

                            @Override
                            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                            }
                        });
                    } else {
                        UserWordProgress existing = response.body().get(0);
                        int newCount = existing.getCorrectAnswersCount() + 1;
                        existing.setCorrectAnswersCount(newCount);

                        if (newCount >= 3) {
                            existing.setIsLearned(true);
                        }

                        apiService.updateWordProgress("eq." + existing.getId(), existing).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> r) {
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                            }
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                Log.e("Repository", "Ошибка обновления прогресса слова: " + t.getMessage(), t);
            }
        });
    }

    public void getLearnedWordsCount(Integer userId, DataCallback<Integer> callback) {
        String filter = "user_id=eq." + userId + "&is_learned=eq.true";
        apiService.getUserProgress(filter).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> response) {
                if (response.isSuccessful() && response.body() != null) callback.onSuccess(response.body().size());
                else callback.onSuccess(0);
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getDailyQuests(DataCallback<List<DailyQuest>> callback) {
        executor.execute(() -> {
            try {
                List<DailyQuest> quests = dailyQuestDao.getAllQuests();
                long today = System.currentTimeMillis();

                if (quests.isEmpty() || !isSameDay(quests.get(0).getDateCreated(), today)) {
                    dailyQuestDao.deleteAll();
                    quests = generateNewQuests();
                    dailyQuestDao.insertAll(quests);
                }

                List<DailyQuest> finalQuests = quests;
                mainHandler.post(() -> callback.onSuccess(finalQuests));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка загрузки квестов: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки квестов"));
            }
        });
    }

    private List<DailyQuest> generateNewQuests() {
        List<DailyQuest> newQuests = new ArrayList<>();
        long now = System.currentTimeMillis();
        Random random = new Random();

        int targetXp = (random.nextInt(3) + 1) * 50;
        newQuests.add(new DailyQuest("Набрать " + targetXp + " XP", targetXp, 20, "XP", now));

        int targetGames = random.nextInt(3) + 3;
        newQuests.add(new DailyQuest("Сыграть " + targetGames + " раз", targetGames, 30, "GAME_PLAYED", now));

        return newQuests;
    }

    private boolean isSameDay(long date1, long date2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTimeInMillis(date1);
        cal2.setTimeInMillis(date2);

        return cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
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
                            addXp(q.getXpReward());
                        }

                        dailyQuestDao.updateQuest(q);
                    }
                }
            } catch (Exception e) {
                Log.e("Repository", "Ошибка обновления квестов: " + e.getMessage(), e);
            }
        });
    }

    public void getAchievements(DataCallback<List<AchievementWithProgress>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }
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
                Log.e("Repository", "Ошибка загрузки достижений: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки достижений"));
            }
        });
    }

    public void getLeaderboard(DataCallback<List<User>> callback) {
        executor.execute(() -> {
            try {
                List<User> users = userDao.getLeaderboard();
                mainHandler.post(() -> {
                    if (users != null) {
                        callback.onSuccess(users);
                    } else {
                        callback.onError("Список пуст");
                    }
                });
            } catch (Exception e) {
                Log.e("Repository", "Ошибка загрузки рейтинга: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка загрузки рейтинга"));
            }
        });
    }

    public void speak(String text, boolean isSlow) {
        if (tts == null || !isTtsReady || text == null || text.trim().isEmpty()) {
            return;
        }

        tts.setSpeechRate(isSlow ? 0.5f : 1.0f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WORD_TTS");
    }

    public void onDestroy() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }
    }
}