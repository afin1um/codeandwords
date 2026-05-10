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
import com.example.codeandwords.model.ThemeProgressStats;
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

import com.example.codeandwords.db.StudyScheduleDao;
import com.example.codeandwords.model.StudySchedule;

import com.example.codeandwords.db.FriendDao;
import com.example.codeandwords.db.TeamDao;
import com.example.codeandwords.db.TeamMemberDao;
import com.example.codeandwords.model.Friend;
import com.example.codeandwords.model.Team;
import com.example.codeandwords.model.TeamMember;

import com.example.codeandwords.ui.profile.AvatarConfig;
import com.example.codeandwords.ui.profile.AvatarPrefs;
import com.google.gson.JsonParser;

import com.example.codeandwords.db.TeamChallengeDao;
import com.example.codeandwords.db.TeamChallengeProgressDao;
import com.example.codeandwords.model.TeamChallenge;
import com.example.codeandwords.model.TeamChallengeProgress;
import com.example.codeandwords.model.UserOverallStats;

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
    private final StudyScheduleDao studyScheduleDao;
    private final LessonHistoryDao lessonHistoryDao;

    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final SharedPreferences prefs;
    private final Context appContext;
    private final ApiService scheduleApiService;

    private final TeamChallengeDao teamChallengeDao;
    private final TeamChallengeProgressDao teamChallengeProgressDao;

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    private static User currentUser;
    private final FriendDao friendDao;
    private final TeamDao teamDao;
    private final TeamMemberDao teamMemberDao;

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
        this.studyScheduleDao = db.studyScheduleDao();
        this.friendDao = db.friendDao();
        this.teamDao = db.teamDao();
        this.teamMemberDao = db.teamMemberDao();
        this.teamChallengeDao = db.teamChallengeDao();
        this.teamChallengeProgressDao = db.teamChallengeProgressDao();

        this.apiService = RetrofitClient.getApiService();
        this.executor = AppDatabase.databaseWriteExecutor;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.scheduleApiService = RetrofitClient.getFastApiService();

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
        restoredUser.setAvatarConfig(prefs.getString("avatar_config", null));

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
                .putString("avatar_config", user.getAvatarConfig())
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
        if (user == null) {
            callback.onError("Введите данные для регистрации");
            return;
        }

        String rawPassword = user.getPasswordHash() == null
                ? ""
                : user.getPasswordHash().trim();

        String cleanEmail = user.getEmail() == null
                ? ""
                : user.getEmail().trim().toLowerCase();

        if (cleanEmail.isEmpty() || rawPassword.isEmpty()) {
            callback.onError("Введите email и пароль");
            return;
        }

        String hashedPassword = rawPassword.length() == 64
                ? rawPassword
                : hashPassword(rawPassword);

        user.setEmail(cleanEmail);
        user.setPasswordHash(hashedPassword);

        apiService.loginByEmail("eq." + cleanEmail).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    mainHandler.post(() -> callback.onError("Пользователь с таким Email уже существует"));
                } else {
                    performActualRegistration(user, callback);
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка проверки почты: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка проверки почты: " + t.getMessage()));
            }
        });
    }

    private void performActualRegistration(User user, DataCallback<User> callback) {
        apiService.register(user).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    User regUser = parseUserFromJson(response.body().get(0));

                    currentUser = regUser;
                    saveCurrentUserToPrefs(regUser);
                    cacheUserSafely(regUser);
                    recordLoginEvent();

                    mainHandler.post(() -> callback.onSuccess(regUser));
                } else if (response.code() == 409) {
                    mainHandler.post(() -> callback.onError("Этот аккаунт уже зарегистрирован"));
                } else {
                    Log.e("Repository", "Ошибка регистрации: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Ошибка сервера: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Сбой сети register: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Сбой сети: " + t.getMessage()));
            }
        });
    }

    public void login(User user, DataCallback<User> callback) {
        if (user == null) {
            callback.onError("Введите данные для входа");
            return;
        }

        String cleanEmail = user.getEmail() == null
                ? ""
                : user.getEmail().trim().toLowerCase();

        String rawPassword = user.getPasswordHash() == null
                ? ""
                : user.getPasswordHash().trim();

        if (cleanEmail.isEmpty() || rawPassword.isEmpty()) {
            callback.onError("Введите email и пароль");
            return;
        }

        String hashedPassword = rawPassword.length() == 64
                ? rawPassword
                : hashPassword(rawPassword);

        apiService.loginByEmail("eq." + cleanEmail).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    mainHandler.post(() -> callback.onError("Пользователь с таким email не найден"));
                    return;
                }

                User serverUser = parseUserFromJson(response.body().get(0));

                String serverPassword = serverUser.getPasswordHash() == null
                        ? ""
                        : serverUser.getPasswordHash().trim();

                boolean passwordMatches =
                        hashedPassword.equals(serverPassword)
                                || rawPassword.equals(serverPassword);

                if (!passwordMatches) {
                    Log.e("RepositoryLogin", "Пароль не совпал. serverPassword=" + serverPassword);
                    mainHandler.post(() -> callback.onError("Неверный пароль"));
                    return;
                }

                currentUser = serverUser;

                if (currentUser.getAvatarConfig() != null
                        && !currentUser.getAvatarConfig().trim().isEmpty()
                        && !currentUser.getAvatarConfig().equals("null")) {
                    AvatarConfig serverAvatar = AvatarConfig.fromJson(currentUser.getAvatarConfig());
                    AvatarPrefs.save(appContext, serverAvatar);
                }

                saveCurrentUserToPrefs(currentUser);
                cacheUserSafely(currentUser);
                recordLoginEvent();

                mainHandler.post(() -> callback.onSuccess(currentUser));
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("RepositoryLogin", "Ошибка сети login: " + t.getMessage(), t);
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
    public void addWordToPersonalDictionary(Word word, DataCallback<Void> callback) {
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
                    Theme autoTheme = findThemeForWordLocally(word.getTerm(), word.getTranslation());

                    if (autoTheme != null && autoTheme.getId() != null) {
                        finalThemeId = autoTheme.getId();
                        finalThemeTitle = safeThemeTitle(autoTheme);
                    }
                }
            } catch (Exception e) {
                Log.e("Repository", "Не удалось определить тему слова: " + e.getMessage(), e);
            }

            Long safeThemeId = finalThemeId;
            String safeThemeTitle = finalThemeTitle;

            mainHandler.post(() -> addUserWord(
                    safeThemeId,
                    safeThemeTitle,
                    word.getTerm(),
                    word.getTranslation(),
                    word.getTranscription() != null ? word.getTranscription() : "",
                    word.getExampleSentence() != null ? word.getExampleSentence() : "",
                    callback
            ));
        });
    }
    public void getThemes(DataCallback<List<Theme>> callback) {
        apiService.getThemes("*", "id.asc").enqueue(new Callback<List<Theme>>() {
            @Override
            public void onResponse(Call<List<Theme>> call, Response<List<Theme>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<Theme> remoteThemes = response.body();

                    executor.execute(() -> {
                        try {
                            themeDao.deleteAll();
                            themeDao.insertAll(remoteThemes);
                        } catch (Exception e) {
                            Log.e("Repository", "Ошибка сохранения тем локально: " + e.getMessage(), e);
                        }

                        mainHandler.post(() -> callback.onSuccess(remoteThemes));
                    });
                } else {
                    Log.e("Repository", "Supabase не загрузил themes: "
                            + response.code() + " | " + getErrorBody(response));
                    loadThemesLocal(callback);
                }
            }

            @Override
            public void onFailure(Call<List<Theme>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети getThemes: " + t.getMessage(), t);
                loadThemesLocal(callback);
            }
        });
    }
    private void loadThemesLocal(DataCallback<List<Theme>> callback) {
        executor.execute(() -> {
            try {
                List<Theme> themes = themeDao.getAllThemes();

                if (themes != null && !themes.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(themes));
                } else {
                    mainHandler.post(() -> callback.onError("Темы не найдены"));
                }
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локальной загрузки тем: " + e.getMessage(), e);
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
        if (themeId == null || themeId <= 0) {
            callback.onError("Тема не найдена");
            return;
        }

        apiService.getWordsByTheme(
                "eq." + themeId,
                "*",
                "id.asc"
        ).enqueue(new Callback<List<Word>>() {
            @Override
            public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<Word> remoteWords = response.body();

                    executor.execute(() -> {
                        try {
                            wordDao.insertAll(remoteWords);
                        } catch (Exception e) {
                            Log.e("Repository", "Ошибка сохранения слов локально: " + e.getMessage(), e);
                        }

                        mainHandler.post(() -> callback.onSuccess(remoteWords));
                    });
                } else {
                    Log.e("Repository", "Supabase не загрузил words: "
                            + response.code() + " | " + getErrorBody(response));
                    loadWordsLocal(themeId, callback);
                }
            }

            @Override
            public void onFailure(Call<List<Word>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети getWordsByTheme: " + t.getMessage(), t);
                loadWordsLocal(themeId, callback);
            }
        });
    }

    private void loadWordsLocal(Long themeId, DataCallback<List<Word>> callback) {
        executor.execute(() -> {
            try {
                List<Word> words = wordDao.getWordsByTheme(themeId);

                if (words != null && !words.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(words));
                } else {
                    mainHandler.post(() -> callback.onError("Слова не найдены"));
                }
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локальной загрузки слов: " + e.getMessage(), e);
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

                // ВАЖНО: обновляем прогресс командных заданий после урока
                updateTeamChallengeProgressAfterLesson(userId, earnedXp);

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

                // ВАЖНО:
                // Если пользователь уже входил сегодня, ничего не обновляем
                // и НЕ проверяем достижения повторно.
                if (stats.lastLoginDay == todayStart) {
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

                // Проверяем достижения только при новом дне входа
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
            if (user == null || user.getId() == null || stats == null) return;

            List<Achievement> allAchievements = achievementDao.getAllAchievements();
            int achievementRewardXp = 0;
            long now = System.currentTimeMillis();

            for (Achievement achievement : allAchievements) {
                if (achievement == null || achievement.getId() == null) continue;

                int progress = getAchievementProgress(achievement, user, stats);
                int maxProgress = safeInt(achievement.getMaxProgress());
                int normalizedProgress = Math.min(progress, maxProgress > 0 ? maxProgress : progress);

                int conditionValue = safeInt(achievement.getConditionValue());
                boolean shouldBeUnlocked = conditionValue > 0 && progress >= conditionValue;

                UserAchievement userAchievement =
                        achievementDao.getUserAchievement(user.getId(), achievement.getId().intValue());

                boolean alreadyUnlockedLocal = userAchievement != null && userAchievement.isUnlocked;
                boolean alreadyUnlockedRemote = isAchievementAlreadyUnlockedRemote(
                        user.getId(),
                        achievement.getId()
                );

                if (userAchievement == null) {
                    userAchievement = new UserAchievement(
                            user.getId().longValue(),
                            achievement.getId(),
                            shouldBeUnlocked ? now : 0,
                            normalizedProgress,
                            shouldBeUnlocked,
                            shouldBeUnlocked && !alreadyUnlockedRemote
                    );

                    achievementDao.insertUserAchievement(userAchievement);
                    syncUserAchievementToRemote(userAchievement);

                    // XP начисляем только если достижение реально новое
                    // и на сервере оно еще не было разблокировано.
                    if (shouldBeUnlocked && !alreadyUnlockedRemote) {
                        achievementRewardXp += safeInt(achievement.getXpReward());
                    }

                } else {
                    userAchievement.currentProgress = normalizedProgress;

                    if (!alreadyUnlockedLocal && shouldBeUnlocked) {
                        userAchievement.isUnlocked = true;
                        userAchievement.isNew = !alreadyUnlockedRemote;
                        userAchievement.dateReceived = now;

                        if (!alreadyUnlockedRemote) {
                            achievementRewardXp += safeInt(achievement.getXpReward());
                        }
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
            if (user == null || user.getId() == null) {
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("current_level", user.getCurrentLevel() != null ? user.getCurrentLevel() : 1);
            payload.addProperty("total_xp", user.getTotalXp() != null ? user.getTotalXp() : 0);

            apiService.updateUserProgress("eq." + user.getId(), payload).enqueue(new Callback<Void>() {
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

    public void getLearnedWords(DataCallback<List<Word>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

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
                    mainHandler.post(() -> callback.onError("Не удалось загрузить изученные слова"));
                    return;
                }

                List<Long> learnedWordIds = new ArrayList<>();

                for (UserWordProgress progress : response.body()) {
                    if (progress != null
                            && progress.getWordId() != null
                            && progress.getIsLearned()) {
                        learnedWordIds.add(progress.getWordId());
                    }
                }

                if (learnedWordIds.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }

                loadWordsByIds(learnedWordIds, callback);
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка загрузки изученных слов: " + t.getMessage()));
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

                if (list != null) {
                    for (AchievementWithProgress item : list) {
                        Log.d("RepositoryAchievements",
                                "id=" + item.id +
                                        ", title=" + item.title +
                                        ", conditionType=" + item.conditionType +
                                        ", iconResName=" + item.iconResName +
                                        ", isUnlocked=" + item.isUnlocked +
                                        ", progress=" + item.currentProgress + "/" + item.maxProgress);
                    }
                }

                List<AchievementWithProgress> finalList = list;
                mainHandler.post(() -> callback.onSuccess(finalList));
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

    private JsonObject buildStudySchedulePayload(StudySchedule schedule) {
        JsonObject payload = new JsonObject();

        payload.addProperty("user_id", schedule.userId);

        if (schedule.themeId > 0) {
            payload.addProperty("theme_id", schedule.themeId);
        } else {
            payload.add("theme_id", JsonNull.INSTANCE);
        }

        payload.addProperty("theme_title", schedule.themeTitle);
        payload.addProperty("theme_short_title", schedule.themeShortTitle);
        payload.addProperty("schedule_date", schedule.scheduleDate);
        payload.addProperty("start_time", schedule.startTime);
        payload.addProperty("end_time", schedule.endTime);

        if (schedule.note != null && !schedule.note.trim().isEmpty()) {
            payload.addProperty("note", schedule.note.trim());
        } else {
            payload.add("note", JsonNull.INSTANCE);
        }

        return payload;
    }

    public void createStudySchedule(StudySchedule schedule, DataCallback<StudySchedule> callback) {
        if (schedule == null) {
            callback.onError("Данные занятия не заполнены");
            return;
        }

        JsonObject payload = buildStudySchedulePayload(schedule);

        scheduleApiService.insertStudyScheduleRaw(payload).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    try {
                        JsonObject saved = response.body().get(0);

                        if (saved.has("id") && !saved.get("id").isJsonNull()) {
                            schedule.id = saved.get("id").getAsInt();
                        }

                        executor.execute(() -> {
                            try {
                                studyScheduleDao.insert(schedule);
                            } catch (Exception e) {
                                Log.e("Repository", "Локально занятие не сохранено: " + e.getMessage(), e);
                            }

                            mainHandler.post(() -> callback.onSuccess(schedule));
                        });

                    } catch (Exception e) {
                        Log.e("Repository", "Ошибка обработки study_schedule: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Ошибка обработки ответа сервера"));
                    }
                } else {
                    Log.e("Repository", "PostgreSQL не сохранил study_schedule: "
                            + response.code() + " | " + getErrorBody(response));

                    mainHandler.post(() -> callback.onError(
                            "PostgreSQL не сохранил запись. Код: " + response.code()
                    ));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети study_schedule: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
            }
        });
    }
    public void deleteStudySchedule(StudySchedule schedule, DataCallback<Void> callback) {
        if (schedule == null) {
            callback.onError("Занятие не найдено");
            return;
        }

        scheduleApiService.deleteStudyScheduleRaw("eq." + schedule.id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                executor.execute(() -> {
                    try {
                        studyScheduleDao.delete(schedule);
                    } catch (Exception e) {
                        Log.e("Repository", "Ошибка локального удаления занятия: " + e.getMessage(), e);
                    }

                    mainHandler.post(() -> callback.onSuccess(null));
                });

                if (!response.isSuccessful()) {
                    Log.e("Repository", "PostgreSQL не удалил study_schedule: "
                            + response.code() + " | " + getErrorBody(response));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("Repository", "Ошибка удаления study_schedule: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка удаления: " + t.getMessage()));
            }
        });
    }
    public void findUserByUsername(String username, DataCallback<User> callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onError("Введите ник пользователя");
            return;
        }

        String cleanUsername = username.trim();

        apiService.findUserByUsername("eq." + cleanUsername)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            User foundUser = parseUserFromJson(response.body().get(0));

                            executor.execute(() -> {
                                try {
                                    userDao.insertUser(foundUser);
                                } catch (Exception e) {
                                    Log.e("Repository", "Ошибка кэширования найденного пользователя: " + e.getMessage(), e);
                                }

                                mainHandler.post(() -> callback.onSuccess(foundUser));
                            });
                        } else {
                            mainHandler.post(() -> callback.onError("Пользователь с таким ником не найден"));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e("Repository", "Ошибка поиска пользователя по нику: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError("Ошибка сети при поиске пользователя"));
                    }
                });
    }
    private JsonObject buildFriendPayload(int userId, int friendId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("user_id", userId);
        payload.addProperty("friend_id", friendId);
        return payload;
    }

    public void addFriend(int friendId, DataCallback<Void> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        int currentUserId = currentUser.getId();

        if (currentUserId == friendId) {
            callback.onError("Нельзя добавить самого себя");
            return;
        }

        JsonObject payload = buildFriendPayload(currentUserId, friendId);

        apiService.upsertFriendRaw("user_id,friend_id", payload)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                        if (response.isSuccessful()) {
                            executor.execute(() -> {
                                try {
                                    friendDao.insert(new Friend(currentUserId, friendId));
                                } catch (Exception e) {
                                    Log.e("Repository", "Ошибка локального сохранения друга: " + e.getMessage(), e);
                                }

                                mainHandler.post(() -> callback.onSuccess(null));
                            });
                        } else {
                            Log.e("Repository", "Ошибка добавления друга: "
                                    + response.code() + " | " + getErrorBody(response));
                            mainHandler.post(() -> callback.onError("Не удалось добавить друга"));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e("Repository", "Ошибка сети addFriend: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError("Ошибка сети при добавлении друга"));
                    }
                });
    }

    public void getFriends(DataCallback<List<User>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        int currentUserId = currentUser.getId();

        executor.execute(() -> {
            try {
                List<User> localFriends = friendDao.getFriends(currentUserId);

                if (localFriends != null && !localFriends.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(localFriends));
                }
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локальной загрузки друзей: " + e.getMessage(), e);
            }
        });

        String select =
                "id,user_id,friend_id," +
                        "user:users!user_friends_user_id_fkey(id,username,email,password_hash,current_level,total_xp,role,created_at,avatar_config)," +
                        "friend:users!user_friends_friend_id_fkey(id,username,email,password_hash,current_level,total_xp,role,created_at,avatar_config)";

        scheduleApiService.getFriendsBothRaw(
                "(user_id.eq." + currentUserId + ",friend_id.eq." + currentUserId + ")",
                select
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        try {
                            friendDao.deleteFriendsByUser(currentUserId);

                            List<User> friends = new ArrayList<>();
                            List<Integer> addedIds = new ArrayList<>();

                            for (JsonObject item : response.body()) {
                                if (item == null) continue;

                                int userId = item.has("user_id") && !item.get("user_id").isJsonNull()
                                        ? item.get("user_id").getAsInt()
                                        : -1;

                                int friendId = item.has("friend_id") && !item.get("friend_id").isJsonNull()
                                        ? item.get("friend_id").getAsInt()
                                        : -1;

                                JsonObject targetUserJson = null;
                                int targetUserId = -1;

                                if (userId == currentUserId) {
                                    targetUserId = friendId;

                                    if (item.has("friend") && item.get("friend").isJsonObject()) {
                                        targetUserJson = item.getAsJsonObject("friend");
                                    }
                                } else if (friendId == currentUserId) {
                                    targetUserId = userId;

                                    if (item.has("user") && item.get("user").isJsonObject()) {
                                        targetUserJson = item.getAsJsonObject("user");
                                    }
                                }

                                if (targetUserId <= 0 || targetUserJson == null) {
                                    continue;
                                }

                                if (addedIds.contains(targetUserId)) {
                                    continue;
                                }

                                User friendUser = parseUserFromJson(targetUserJson);

                                if (friendUser.getId() == null) {
                                    friendUser.setId(targetUserId);
                                }

                                addedIds.add(friendUser.getId());

                                userDao.insertUser(friendUser);

                                // Локально сохраняем в направлении "текущий пользователь -> друг",
                                // чтобы старый FriendDao.getFriends(currentUserId) работал нормально.
                                friendDao.insert(new Friend(currentUserId, friendUser.getId()));

                                friends.add(friendUser);
                            }

                            mainHandler.post(() -> callback.onSuccess(friends));

                        } catch (Exception e) {
                            Log.e("Repository", "Ошибка обработки друзей: " + e.getMessage(), e);
                            mainHandler.post(() -> callback.onError("Не удалось обработать список друзей"));
                        }
                    });
                } else {
                    Log.e("Repository", "Ошибка загрузки друзей из Supabase: "
                            + response.code() + " | " + getErrorBody(response));

                    executor.execute(() -> {
                        try {
                            List<User> localFriends = friendDao.getFriends(currentUserId);
                            mainHandler.post(() -> callback.onSuccess(localFriends));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError("Не удалось загрузить друзей"));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети getFriends: " + t.getMessage(), t);

                executor.execute(() -> {
                    try {
                        List<User> localFriends = friendDao.getFriends(currentUserId);
                        mainHandler.post(() -> callback.onSuccess(localFriends));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Ошибка загрузки друзей"));
                    }
                });
            }
        });
    }

    public void createTeam(String teamName, DataCallback<Integer> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        if (teamName == null || teamName.trim().isEmpty()) {
            callback.onError("Введите название команды");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("team_name", teamName.trim());
        payload.addProperty("owner_id", currentUser.getId());

        scheduleApiService.insertTeamRaw(payload).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    int teamId = response.body().get(0).get("id").getAsInt();

                    executor.execute(() -> {
                        try {
                            teamDao.insert(new Team(teamName.trim(), currentUser.getId()));
                            teamMemberDao.insert(new TeamMember(teamId, currentUser.getId()));
                        } catch (Exception e) {
                            Log.e("Repository", "Ошибка локального сохранения команды: " + e.getMessage(), e);
                        }

                        mainHandler.post(() -> callback.onSuccess(teamId));
                    });
                } else {
                    Log.e("Repository", "Ошибка создания команды: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Не удалось создать команду"));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети createTeam: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети при создании команды"));
            }
        });
    }

    public void addUserToTeam(int teamId, int userId, DataCallback<Void> callback) {
        executor.execute(() -> {
            try {
                teamMemberDao.insert(new TeamMember(teamId, userId));
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка добавления в команду: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось добавить участника"));
            }
        });
    }

    public void updateAvatarConfig(AvatarConfig avatarConfig, DataCallback<Void> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        if (avatarConfig == null) {
            callback.onError("Аватар не заполнен");
            return;
        }

        String json = avatarConfig.toJson();

        JsonObject payload = new JsonObject();
        payload.add("avatar_config", JsonParser.parseString(json));

        apiService.updateAvatarConfig("eq." + currentUser.getId(), payload)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            currentUser.setAvatarConfig(json);
                            saveCurrentUserToPrefs(currentUser);
                            AvatarPrefs.save(appContext, avatarConfig);

                            executor.execute(() -> {
                                try {
                                    userDao.insertUser(currentUser);
                                } catch (Exception e) {
                                    Log.e("Repository", "Ошибка локального сохранения avatar_config: " + e.getMessage(), e);
                                }
                            });

                            mainHandler.post(() -> callback.onSuccess(null));
                        } else {
                            Log.e("Repository", "Ошибка обновления avatar_config: "
                                    + response.code() + " | " + getErrorBody(response));
                            mainHandler.post(() -> callback.onError("Не удалось обновить аватар"));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e("Repository", "Ошибка сети updateAvatarConfig: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
                    }
                });
    }

    private User parseUserFromJson(JsonObject userJson) {
        User user = new User();

        if (userJson == null) {
            return user;
        }

        if (userJson.has("id") && !userJson.get("id").isJsonNull()) {
            user.setId(userJson.get("id").getAsInt());
        }

        if (userJson.has("username") && !userJson.get("username").isJsonNull()) {
            user.setUsername(userJson.get("username").getAsString());
        }

        if (userJson.has("email") && !userJson.get("email").isJsonNull()) {
            user.setEmail(userJson.get("email").getAsString());
        }

        if (userJson.has("password_hash") && !userJson.get("password_hash").isJsonNull()) {
            user.setPasswordHash(userJson.get("password_hash").getAsString());
        }

        if (userJson.has("current_level") && !userJson.get("current_level").isJsonNull()) {
            user.setCurrentLevel(userJson.get("current_level").getAsInt());
        }

        if (userJson.has("total_xp") && !userJson.get("total_xp").isJsonNull()) {
            user.setTotalXp(userJson.get("total_xp").getAsInt());
        }

        if (userJson.has("role") && !userJson.get("role").isJsonNull()) {
            user.setRole(userJson.get("role").getAsString());
        }

        if (userJson.has("created_at") && !userJson.get("created_at").isJsonNull()) {
            user.setCreatedAt(userJson.get("created_at").getAsString());
        }

        if (userJson.has("avatar_config") && !userJson.get("avatar_config").isJsonNull()) {
            user.setAvatarConfig(userJson.get("avatar_config").toString());
        } else {
            user.setAvatarConfig(null);
        }

        if (userJson.has("gender") && !userJson.get("gender").isJsonNull()) {
            user.setGender(userJson.get("gender").getAsString());
        }

        return user;
    }

    public void getStudyScheduleForDate(int userId, String date, DataCallback<List<StudySchedule>> callback) {
        if (userId <= 0 || date == null || date.trim().isEmpty()) {
            callback.onError("Некорректные данные графика");
            return;
        }

        scheduleApiService.getStudyScheduleRaw(
                "eq." + userId,
                "eq." + date,
                "start_time.asc"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        try {
                            List<StudySchedule> schedules = parseStudySchedules(response.body());

                            for (StudySchedule schedule : schedules) {
                                studyScheduleDao.insert(schedule);
                            }

                            mainHandler.post(() -> callback.onSuccess(schedules));
                        } catch (Exception e) {
                            Log.e("Repository", "Ошибка обработки study_schedule: " + e.getMessage(), e);
                            loadLocalStudyScheduleForDate(userId, date, callback);
                        }
                    });
                } else {
                    Log.e("Repository", "Ошибка загрузки study_schedule: "
                            + response.code() + " | " + getErrorBody(response));
                    loadLocalStudyScheduleForDate(userId, date, callback);
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети getStudyScheduleForDate: " + t.getMessage(), t);
                loadLocalStudyScheduleForDate(userId, date, callback);
            }
        });
    }

    public void getStudyScheduleForRange(int userId, String startDate, String endDate, DataCallback<List<StudySchedule>> callback) {
        if (userId <= 0 || startDate == null || startDate.trim().isEmpty()
                || endDate == null || endDate.trim().isEmpty()) {
            callback.onError("Некорректный диапазон дат");
            return;
        }

        scheduleApiService.getStudyScheduleRangeRaw(
                "eq." + userId,
                "gte." + startDate,
                "lte." + endDate,
                "schedule_date.asc,start_time.asc"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        try {
                            List<StudySchedule> schedules = parseStudySchedules(response.body());

                            for (StudySchedule schedule : schedules) {
                                studyScheduleDao.insert(schedule);
                            }

                            mainHandler.post(() -> callback.onSuccess(schedules));
                        } catch (Exception e) {
                            Log.e("Repository", "Ошибка обработки диапазона study_schedule: " + e.getMessage(), e);
                            loadLocalStudyScheduleForRange(userId, startDate, endDate, callback);
                        }
                    });
                } else {
                    Log.e("Repository", "Ошибка загрузки диапазона study_schedule: "
                            + response.code() + " | " + getErrorBody(response));

                    loadLocalStudyScheduleForRange(userId, startDate, endDate, callback);
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети getStudyScheduleForRange: " + t.getMessage(), t);
                loadLocalStudyScheduleForRange(userId, startDate, endDate, callback);
            }
        });
    }

    private void loadLocalStudyScheduleForDate(int userId, String date, DataCallback<List<StudySchedule>> callback) {
        executor.execute(() -> {
            try {
                List<StudySchedule> local = studyScheduleDao.getByDate(userId, date);
                mainHandler.post(() -> callback.onSuccess(local));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локальной загрузки study_schedule: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить график занятий"));
            }
        });
    }

    private void loadLocalStudyScheduleForRange(int userId, String startDate, String endDate, DataCallback<List<StudySchedule>> callback) {
        executor.execute(() -> {
            try {
                List<StudySchedule> local = studyScheduleDao.getByDateRange(userId, startDate, endDate);
                mainHandler.post(() -> callback.onSuccess(local));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локальной загрузки диапазона study_schedule: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить график занятий"));
            }
        });
    }

    private List<StudySchedule> parseStudySchedules(List<JsonObject> jsonList) {
        List<StudySchedule> result = new ArrayList<>();

        if (jsonList == null) {
            return result;
        }

        for (JsonObject item : jsonList) {
            if (item == null) {
                continue;
            }

            StudySchedule schedule = new StudySchedule();

            if (item.has("id") && !item.get("id").isJsonNull()) {
                schedule.id = item.get("id").getAsInt();
            }

            if (item.has("user_id") && !item.get("user_id").isJsonNull()) {
                schedule.userId = item.get("user_id").getAsInt();
            }

            if (item.has("theme_id") && !item.get("theme_id").isJsonNull()) {
                schedule.themeId = item.get("theme_id").getAsInt();
            } else {
                schedule.themeId = 0;
            }

            if (item.has("theme_title") && !item.get("theme_title").isJsonNull()) {
                schedule.themeTitle = item.get("theme_title").getAsString();
            } else {
                schedule.themeTitle = "Без темы";
            }

            if (item.has("theme_short_title") && !item.get("theme_short_title").isJsonNull()) {
                schedule.themeShortTitle = item.get("theme_short_title").getAsString();
            } else {
                schedule.themeShortTitle = makeScheduleShortTitle(schedule.themeTitle);
            }

            if (item.has("schedule_date") && !item.get("schedule_date").isJsonNull()) {
                schedule.scheduleDate = item.get("schedule_date").getAsString();
            }

            if (item.has("start_time") && !item.get("start_time").isJsonNull()) {
                schedule.startTime = item.get("start_time").getAsString();
            }

            if (item.has("end_time") && !item.get("end_time").isJsonNull()) {
                schedule.endTime = item.get("end_time").getAsString();
            }

            if (item.has("note") && !item.get("note").isJsonNull()) {
                schedule.note = item.get("note").getAsString();
            } else {
                schedule.note = "";
            }

            if (schedule.userId > 0 && schedule.scheduleDate != null) {
                result.add(schedule);
            }
        }

        return result;
    }

    private String makeScheduleShortTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "??";
        }

        String clean = title.trim();
        String[] words = clean.split("\\s+");

        if (words.length >= 2) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.getDefault());
        }

        if (clean.length() >= 2) {
            return clean.substring(0, 2).toUpperCase(Locale.getDefault());
        }

        return clean.toUpperCase(Locale.getDefault());
    }

    public void createTeamWithFriends(
            String teamName,
            List<User> selectedFriends,
            String challengeType,
            int targetValue,
            DataCallback<Integer> callback
    ) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        if (teamName == null || teamName.trim().isEmpty()) {
            callback.onError("Введите название команды");
            return;
        }

        if (selectedFriends == null) {
            selectedFriends = new ArrayList<>();
        }

        if (selectedFriends.size() > 3) {
            callback.onError("В команду можно добавить максимум 3 друзей");
            return;
        }

        int ownerId = currentUser.getId();

        JsonObject teamPayload = new JsonObject();
        teamPayload.addProperty("team_name", teamName.trim());
        teamPayload.addProperty("owner_id", ownerId);

        List<User> finalSelectedFriends = selectedFriends;

        scheduleApiService.insertTeamRaw(teamPayload).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    String errorBody = getErrorBody(response);
                    Log.e("Repository", "Ошибка создания команды: " + response.code() + " | " + errorBody);
                    mainHandler.post(() -> callback.onError("Не удалось создать команду: " + response.code()));
                    return;
                }

                int teamId = response.body().get(0).get("id").getAsInt();

                Team createdTeam = new Team();
                createdTeam.id = teamId;
                createdTeam.teamName = teamName.trim();
                createdTeam.ownerId = ownerId;

                executor.execute(() -> {
                    try {
                        teamDao.insert(createdTeam);
                    } catch (Exception e) {
                        Log.e("Repository", "Ошибка локального сохранения созданной команды: " + e.getMessage(), e);
                    }
                });

                List<Integer> memberIds = new ArrayList<>();
                memberIds.add(ownerId);

                for (User friend : finalSelectedFriends) {
                    if (friend != null && friend.getId() != null && !memberIds.contains(friend.getId())) {
                        memberIds.add(friend.getId());
                    }
                }

                createTeamMembersRemote(teamId, memberIds, challengeType, targetValue, callback);
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети createTeamWithFriends: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети при создании команды"));
            }
        });
    }

    private void createTeamMembersRemote(
            int teamId,
            List<Integer> memberIds,
            String challengeType,
            int targetValue,
            DataCallback<Integer> callback
    ) {
        List<JsonObject> membersPayload = new ArrayList<>();

        for (Integer userId : memberIds) {
            JsonObject item = new JsonObject();
            item.addProperty("team_id", teamId);
            item.addProperty("user_id", userId);
            membersPayload.add(item);
        }

        scheduleApiService.insertTeamMembersMinimalRaw(membersPayload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e("Repository", "Ошибка добавления участников: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Команда создана, но участников добавить не удалось"));
                    return;
                }

                createTeamChallengeRemote(teamId, memberIds, challengeType, targetValue, callback);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("Repository", "Ошибка сети createTeamMembersRemote: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети при добавлении участников"));
            }
        });
    }

    private void createTeamChallengeRemote(
            int teamId,
            List<Integer> memberIds,
            String challengeType,
            int targetValue,
            DataCallback<Integer> callback
    ) {
        String title = "LESSONS".equals(challengeType)
                ? "Пройти " + targetValue + " уроков"
                : "Заработать " + targetValue + " XP";

        JsonObject payload = new JsonObject();
        payload.addProperty("team_id", teamId);
        payload.addProperty("title", title);
        payload.addProperty("condition_type", challengeType);
        payload.addProperty("target_value", targetValue);
        payload.addProperty("xp_first", 120);
        payload.addProperty("xp_second", 80);
        payload.addProperty("xp_other", 50);
        payload.addProperty("is_completed", false);

        scheduleApiService.insertTeamChallengeMinimalRaw(payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e("Repository", "Ошибка создания задания команды: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Не удалось создать задание команды"));
                    return;
                }

                findCreatedTeamChallengeAndCreateProgress(teamId, memberIds, callback);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("Repository", "Timeout/сеть createTeamChallengeRemote: " + t.getMessage(), t);

                mainHandler.postDelayed(() -> {
                    findCreatedTeamChallengeAndCreateProgress(teamId, memberIds, callback);
                }, 1200);
            }
        });
    }

    private void createTeamChallengeProgressRemote(
            int teamId,
            int challengeId,
            List<Integer> memberIds,
            DataCallback<Integer> callback
    ) {
        List<JsonObject> progressPayload = new ArrayList<>();

        for (Integer userId : memberIds) {
            JsonObject item = new JsonObject();
            item.addProperty("team_id", teamId);
            item.addProperty("challenge_id", challengeId);
            item.addProperty("user_id", userId);
            item.addProperty("progress", 0);
            item.addProperty("is_completed", false);
            item.addProperty("awarded_xp", 0);
            progressPayload.add(item);
        }

        scheduleApiService.insertTeamChallengeProgressMinimalRaw(progressPayload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    mainHandler.post(() -> callback.onSuccess(teamId));
                } else {
                    Log.e("Repository", "Ошибка создания прогресса команды: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Задание создано, но прогресс участников не создан"));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("Repository", "Ошибка сети createTeamChallengeProgressRemote: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети при создании прогресса"));
            }
        });
    }

    public void getTeamChallenge(int teamId, DataCallback<TeamChallenge> callback) {
        scheduleApiService.getTeamChallengeRaw(
                "eq." + teamId,
                "id.desc",
                1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(parseTeamChallenge(response.body().get(0)));
                } else {
                    callback.onError("Задание команды не найдено");
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                callback.onError("Ошибка загрузки задания команды");
            }
        });
    }

    public void getTeamProgress(int challengeId, DataCallback<List<TeamChallengeProgress>> callback) {
        scheduleApiService.getTeamProgressRaw(
                "eq." + challengeId,
                "id,challenge_id,team_id,user_id,progress,is_completed,completed_at,place,awarded_xp,user:users!team_challenge_progress_user_id_fkey(id,username,email,current_level,total_xp,avatar_config)",
                "progress.desc"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<TeamChallengeProgress> result = new ArrayList<>();

                    for (JsonObject item : response.body()) {
                        TeamChallengeProgress p = new TeamChallengeProgress();

                        if (item.has("id") && !item.get("id").isJsonNull()) {
                            p.id = item.get("id").getAsInt();
                        }

                        if (item.has("challenge_id") && !item.get("challenge_id").isJsonNull()) {
                            p.challengeId = item.get("challenge_id").getAsInt();
                        }

                        if (item.has("team_id") && !item.get("team_id").isJsonNull()) {
                            p.teamId = item.get("team_id").getAsInt();
                        }

                        if (item.has("user_id") && !item.get("user_id").isJsonNull()) {
                            p.userId = item.get("user_id").getAsInt();
                        }

                        if (item.has("progress") && !item.get("progress").isJsonNull()) {
                            p.progress = item.get("progress").getAsInt();
                        }

                        if (item.has("is_completed") && !item.get("is_completed").isJsonNull()) {
                            p.isCompleted = item.get("is_completed").getAsBoolean();
                        }

                        if (item.has("completed_at") && !item.get("completed_at").isJsonNull()) {
                            p.completedAt = item.get("completed_at").getAsString();
                        }

                        if (item.has("place") && !item.get("place").isJsonNull()) {
                            p.place = item.get("place").getAsInt();
                        }

                        if (item.has("awarded_xp") && !item.get("awarded_xp").isJsonNull()) {
                            p.awardedXp = item.get("awarded_xp").getAsInt();
                        }

                        if (item.has("user") && item.get("user").isJsonObject()) {
                            JsonObject userJson = item.getAsJsonObject("user");

                            if (userJson.has("username") && !userJson.get("username").isJsonNull()) {
                                p.username = userJson.get("username").getAsString();
                            }
                        }

                        result.add(p);
                    }

                    callback.onSuccess(result);
                } else {
                    Log.e("Repository", "Ошибка загрузки прогресса команды: "
                            + response.code() + " | " + getErrorBody(response));
                    callback.onError("Не удалось загрузить прогресс команды");
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети getTeamProgress: " + t.getMessage(), t);
                callback.onError("Ошибка сети при загрузке прогресса команды");
            }
        });
    }

    private TeamChallenge parseTeamChallenge(JsonObject item) {
        TeamChallenge c = new TeamChallenge();

        if (item.has("id") && !item.get("id").isJsonNull()) c.id = item.get("id").getAsInt();
        if (item.has("team_id") && !item.get("team_id").isJsonNull()) c.teamId = item.get("team_id").getAsInt();
        if (item.has("title") && !item.get("title").isJsonNull()) c.title = item.get("title").getAsString();
        if (item.has("condition_type") && !item.get("condition_type").isJsonNull()) c.conditionType = item.get("condition_type").getAsString();
        if (item.has("target_value") && !item.get("target_value").isJsonNull()) c.targetValue = item.get("target_value").getAsInt();
        if (item.has("xp_first") && !item.get("xp_first").isJsonNull()) c.xpFirst = item.get("xp_first").getAsInt();
        if (item.has("xp_second") && !item.get("xp_second").isJsonNull()) c.xpSecond = item.get("xp_second").getAsInt();
        if (item.has("xp_other") && !item.get("xp_other").isJsonNull()) c.xpOther = item.get("xp_other").getAsInt();
        if (item.has("is_completed") && !item.get("is_completed").isJsonNull()) c.isCompleted = item.get("is_completed").getAsBoolean();

        return c;
    }

    private void updateTeamChallengeProgressAfterLesson(int userId, int earnedXp) {
        String select =
                "id,challenge_id,team_id,user_id,progress,is_completed,awarded_xp,place," +
                        "challenge:team_challenges!team_challenge_progress_challenge_id_fkey(" +
                        "id,team_id,title,condition_type,target_value,xp_first,xp_second,xp_other,is_completed,winner_user_id" +
                        ")";

        scheduleApiService.getMyActiveTeamProgressRaw(
                "eq." + userId,
                "eq.false",
                select
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e("Repository", "Не удалось загрузить активные командные задания: "
                            + response.code() + " | " + getErrorBody(response));
                    return;
                }

                for (JsonObject progressJson : response.body()) {
                    try {
                        processSingleTeamProgress(progressJson, earnedXp);
                    } catch (Exception e) {
                        Log.e("Repository", "Ошибка обработки командного прогресса: " + e.getMessage(), e);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети updateTeamChallengeProgressAfterLesson: " + t.getMessage(), t);
            }
        });
    }

    private void processSingleTeamProgress(JsonObject progressJson, int earnedXp) {
        if (progressJson == null) return;

        if (!progressJson.has("id") || progressJson.get("id").isJsonNull()) return;
        if (!progressJson.has("progress") || progressJson.get("progress").isJsonNull()) return;
        if (!progressJson.has("is_completed") || progressJson.get("is_completed").isJsonNull()) return;
        if (!progressJson.has("challenge") || !progressJson.get("challenge").isJsonObject()) return;

        boolean alreadyCompleted = progressJson.get("is_completed").getAsBoolean();
        if (alreadyCompleted) {
            return;
        }

        int progressId = progressJson.get("id").getAsInt();
        int oldProgress = progressJson.get("progress").getAsInt();

        JsonObject challengeJson = progressJson.getAsJsonObject("challenge");

        if (!challengeJson.has("id") || challengeJson.get("id").isJsonNull()) return;
        if (!challengeJson.has("condition_type") || challengeJson.get("condition_type").isJsonNull()) return;
        if (!challengeJson.has("target_value") || challengeJson.get("target_value").isJsonNull()) return;

        int challengeId = challengeJson.get("id").getAsInt();
        String conditionType = challengeJson.get("condition_type").getAsString();
        int targetValue = challengeJson.get("target_value").getAsInt();

        int addProgress;

        if ("XP".equals(conditionType)) {
            addProgress = earnedXp;
        } else if ("LESSONS".equals(conditionType)) {
            addProgress = 1;
        } else {
            return;
        }

        int newProgress = oldProgress + addProgress;

        if (newProgress >= targetValue) {
            finishTeamChallengeProgress(progressId, challengeId, challengeJson, newProgress);
        } else {
            JsonObject payload = new JsonObject();
            payload.addProperty("progress", newProgress);

            scheduleApiService.updateTeamChallengeProgressRaw(
                    "eq." + progressId,
                    payload
            ).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (!response.isSuccessful()) {
                        Log.e("Repository", "Не удалось обновить прогресс команды: "
                                + response.code() + " | " + getErrorBody(response));
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Log.e("Repository", "Ошибка сети updateTeamProgress: " + t.getMessage(), t);
                }
            });
        }
    }

    private void finishTeamChallengeProgress(
            int progressId,
            int challengeId,
            JsonObject challengeJson,
            int finalProgress
    ) {
        scheduleApiService.getTeamProgressByIdRaw(
                "eq." + progressId,
                "id,is_completed,awarded_xp,place",
                1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> checkCall, Response<List<JsonObject>> checkResponse) {
                if (!checkResponse.isSuccessful()
                        || checkResponse.body() == null
                        || checkResponse.body().isEmpty()) {
                    Log.e("Repository", "Не удалось проверить прогресс перед завершением: "
                            + checkResponse.code() + " | " + getErrorBody(checkResponse));
                    return;
                }

                JsonObject currentProgress = checkResponse.body().get(0);

                boolean alreadyCompleted =
                        currentProgress.has("is_completed")
                                && !currentProgress.get("is_completed").isJsonNull()
                                && currentProgress.get("is_completed").getAsBoolean();

                if (alreadyCompleted) {
                    Log.d("Repository", "Командная награда уже была начислена. progressId=" + progressId);
                    return;
                }

                scheduleApiService.getCompletedTeamProgressRaw(
                        "eq." + challengeId,
                        "eq.true",
                        "completed_at.asc"
                ).enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                        int alreadyCompletedCount = 0;

                        if (response.isSuccessful() && response.body() != null) {
                            alreadyCompletedCount = response.body().size();
                        }

                        int place = alreadyCompletedCount + 1;
                        int rewardXp = getTeamChallengeRewardByPlace(challengeJson, place);

                        JsonObject payload = new JsonObject();
                        payload.addProperty("progress", finalProgress);
                        payload.addProperty("is_completed", true);
                        payload.addProperty("completed_at", toSqlTimestamp(System.currentTimeMillis()));
                        payload.addProperty("place", place);
                        payload.addProperty("awarded_xp", rewardXp);

                        scheduleApiService.updateTeamChallengeProgressRaw(
                                "eq." + progressId,
                                payload
                        ).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> updateResponse) {
                                if (updateResponse.isSuccessful()) {
                                    grantTeamChallengeXp(rewardXp);

                                    if (place == 1) {
                                        markTeamChallengeWinner(challengeId);
                                    }
                                } else {
                                    Log.e("Repository", "Не удалось завершить командный прогресс: "
                                            + updateResponse.code() + " | " + getErrorBody(updateResponse));
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                Log.e("Repository", "Ошибка сети finishTeamChallengeProgress update: " + t.getMessage(), t);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e("Repository", "Ошибка получения мест команды: " + t.getMessage(), t);
                    }
                });
            }

            @Override
            public void onFailure(Call<List<JsonObject>> checkCall, Throwable t) {
                Log.e("Repository", "Ошибка проверки progressId перед начислением: " + t.getMessage(), t);
            }
        });
    }

    private int getTeamChallengeRewardByPlace(JsonObject challengeJson, int place) {
        int xpFirst = 120;
        int xpSecond = 80;
        int xpOther = 50;

        if (challengeJson.has("xp_first") && !challengeJson.get("xp_first").isJsonNull()) {
            xpFirst = challengeJson.get("xp_first").getAsInt();
        }

        if (challengeJson.has("xp_second") && !challengeJson.get("xp_second").isJsonNull()) {
            xpSecond = challengeJson.get("xp_second").getAsInt();
        }

        if (challengeJson.has("xp_other") && !challengeJson.get("xp_other").isJsonNull()) {
            xpOther = challengeJson.get("xp_other").getAsInt();
        }

        if (place == 1) return xpFirst;
        if (place == 2) return xpSecond;
        return xpOther;
    }

    private void grantTeamChallengeXp(int xpReward) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        executor.execute(() -> {
            try {
                int oldTotalXp = currentUser.getTotalXp() != null ? currentUser.getTotalXp() : 0;
                int newTotalXp = oldTotalXp + xpReward;
                int newLevel = (newTotalXp / 100) + 1;

                userDao.updateProgress(currentUser.getId(), newTotalXp, newLevel);

                currentUser.setTotalXp(newTotalXp);
                currentUser.setCurrentLevel(newLevel);

                saveCurrentUserToPrefs(currentUser);
                syncUserProgressToRemote(currentUser);

            } catch (Exception e) {
                Log.e("Repository", "Ошибка начисления XP за командное задание: " + e.getMessage(), e);
            }
        });
    }

    private void markTeamChallengeWinner(int challengeId) {
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("winner_user_id", currentUser.getId());
        payload.addProperty("completed_at", toSqlTimestamp(System.currentTimeMillis()));

        scheduleApiService.updateTeamChallengeRaw(
                "eq." + challengeId,
                payload
        ).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e("Repository", "Не удалось отметить победителя команды: "
                            + response.code() + " | " + getErrorBody(response));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("Repository", "Ошибка сети markTeamChallengeWinner: " + t.getMessage(), t);
            }
        });
    }

    public void getMyTeams(DataCallback<List<Team>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        int userId = currentUser.getId();

        scheduleApiService.getOwnedTeamsRaw(
                "eq." + userId,
                "id,team_name,owner_id",
                "id.desc"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> ownedCall, Response<List<JsonObject>> ownedResponse) {
                List<Team> result = new ArrayList<>();

                if (ownedResponse.isSuccessful() && ownedResponse.body() != null) {
                    for (JsonObject item : ownedResponse.body()) {
                        Team team = parseTeamFromJson(item);
                        if (team != null && team.id > 0) {
                            addTeamIfNotExists(result, team);
                        }
                    }
                }

                scheduleApiService.getMyTeamMembersRaw(
                        "eq." + userId,
                        "id,team_id,user_id"
                ).enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> membersCall, Response<List<JsonObject>> membersResponse) {
                        if (!membersResponse.isSuccessful() || membersResponse.body() == null) {
                            cacheTeamsLocal(result);
                            mainHandler.post(() -> callback.onSuccess(result));
                            return;
                        }

                        List<Integer> teamIds = new ArrayList<>();

                        for (JsonObject item : membersResponse.body()) {
                            if (item.has("team_id") && !item.get("team_id").isJsonNull()) {
                                int teamId = item.get("team_id").getAsInt();
                                if (!teamIds.contains(teamId)) {
                                    teamIds.add(teamId);
                                }
                            }
                        }

                        if (teamIds.isEmpty()) {
                            cacheTeamsLocal(result);
                            mainHandler.post(() -> callback.onSuccess(result));
                            return;
                        }

                        StringBuilder idsFilter = new StringBuilder("in.(");
                        for (int i = 0; i < teamIds.size(); i++) {
                            if (i > 0) idsFilter.append(",");
                            idsFilter.append(teamIds.get(i));
                        }
                        idsFilter.append(")");

                        scheduleApiService.getTeamsByIdsRaw(
                                idsFilter.toString(),
                                "id,team_name,owner_id",
                                "id.desc"
                        ).enqueue(new Callback<List<JsonObject>>() {
                            @Override
                            public void onResponse(Call<List<JsonObject>> teamsCall, Response<List<JsonObject>> teamsResponse) {
                                if (teamsResponse.isSuccessful() && teamsResponse.body() != null) {
                                    for (JsonObject item : teamsResponse.body()) {
                                        Team team = parseTeamFromJson(item);
                                        if (team != null && team.id > 0) {
                                            addTeamIfNotExists(result, team);
                                        }
                                    }
                                }

                                cacheTeamsLocal(result);
                                mainHandler.post(() -> callback.onSuccess(result));
                            }

                            @Override
                            public void onFailure(Call<List<JsonObject>> teamsCall, Throwable t) {
                                Log.e("Repository", "Ошибка загрузки команд по id: " + t.getMessage(), t);
                                if (!result.isEmpty()) {
                                    cacheTeamsLocal(result);
                                    mainHandler.post(() -> callback.onSuccess(result));
                                } else {
                                    loadMyTeamsLocal(userId, callback);
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> membersCall, Throwable t) {
                        Log.e("Repository", "Ошибка загрузки team_members: " + t.getMessage(), t);
                        if (!result.isEmpty()) {
                            cacheTeamsLocal(result);
                            mainHandler.post(() -> callback.onSuccess(result));
                        } else {
                            loadMyTeamsLocal(userId, callback);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<List<JsonObject>> ownedCall, Throwable t) {
                Log.e("Repository", "Ошибка загрузки owned teams: " + t.getMessage(), t);
                loadMyTeamsLocal(userId, callback);
            }
        });
    }
    private void findCreatedChallengeAndCreateProgress(
            int teamId,
            List<Integer> memberIds,
            DataCallback<Integer> callback
    ) {
        scheduleApiService.getTeamChallengeRaw(
                "eq." + teamId,
                "id.desc",
                1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    int challengeId = response.body().get(0).get("id").getAsInt();
                    createTeamChallengeProgressRemote(teamId, challengeId, memberIds, callback);
                } else {
                    Log.e("Repository", "Задание команды не найдено после создания: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Команда создана, но задание не найдено. Откройте экран команды позже."));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка поиска задания команды после timeout: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Команда создана, но задание пока не загрузилось"));
            }
        });
    }

    private void findCreatedTeamChallengeAndCreateProgress(
            int teamId,
            List<Integer> memberIds,
            DataCallback<Integer> callback
    ) {
        scheduleApiService.getTeamChallengeRaw(
                "eq." + teamId,
                "id.desc",
                1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    int challengeId = response.body().get(0).get("id").getAsInt();
                    createTeamChallengeProgressRemote(teamId, challengeId, memberIds, callback);
                } else {
                    Log.e("Repository", "Задание команды не найдено после создания: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Команда создана, но задание не найдено"));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка поиска задания команды: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка поиска задания команды"));
            }
        });
    }

    private Team parseTeamFromJson(JsonObject item) {
        if (item == null) {
            return null;
        }

        Team team = new Team();

        if (item.has("id") && !item.get("id").isJsonNull()) {
            team.id = item.get("id").getAsInt();
        }

        if (item.has("team_name") && !item.get("team_name").isJsonNull()) {
            team.teamName = item.get("team_name").getAsString();
        }

        if (item.has("owner_id") && !item.get("owner_id").isJsonNull()) {
            team.ownerId = item.get("owner_id").getAsInt();
        }

        return team;
    }

    private void addTeamIfNotExists(List<Team> teams, Team newTeam) {
        if (teams == null || newTeam == null || newTeam.id <= 0) {
            return;
        }

        for (Team team : teams) {
            if (team != null && team.id == newTeam.id) {
                return;
            }
        }

        teams.add(newTeam);
    }

    private void cacheTeamsLocal(List<Team> teams) {
        if (teams == null || teams.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            for (Team team : teams) {
                try {
                    teamDao.insert(team);
                } catch (Exception e) {
                    Log.e("Repository", "Ошибка локального сохранения команды: " + e.getMessage(), e);
                }
            }
        });
    }

    private void loadMyTeamsLocal(int userId, DataCallback<List<Team>> callback) {
        executor.execute(() -> {
            try {
                List<Team> result = new ArrayList<>();

                List<Team> ownedTeams = teamDao.getOwnedTeams(userId);
                if (ownedTeams != null) {
                    for (Team team : ownedTeams) {
                        addTeamIfNotExists(result, team);
                    }
                }

                List<TeamMember> memberships = teamMemberDao.getByUserId(userId);
                if (memberships != null) {
                    for (TeamMember member : memberships) {
                        if (member == null) continue;

                        Team team = teamDao.getById(member.teamId);
                        if (team != null) {
                            addTeamIfNotExists(result, team);
                        }
                    }
                }

                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локальной загрузки команд: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить команды"));
            }
        });
    }

    private boolean isAchievementAlreadyUnlockedRemote(long userId, long achievementId) {
        try {
            Response<List<JsonObject>> response = apiService.getUserAchievementRecordRaw(
                    "eq." + userId,
                    "eq." + achievementId
            ).execute();

            if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                return false;
            }

            JsonObject item = response.body().get(0);

            return item.has("is_unlocked")
                    && !item.get("is_unlocked").isJsonNull()
                    && item.get("is_unlocked").getAsBoolean();

        } catch (Exception e) {
            Log.e("Repository", "Ошибка проверки достижения на сервере: " + e.getMessage(), e);
            return false;
        }
    }
    public void adminCreateTheme(Theme theme, DataCallback<Theme> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (theme == null) {
            callback.onError("Тема не заполнена");
            return;
        }

        String title = theme.getTitle() == null ? "" : theme.getTitle().trim();
        String description = theme.getDescription() == null ? "" : theme.getDescription().trim();
        String difficulty = theme.getDifficultyLevel() == null ? "Easy" : theme.getDifficultyLevel().trim();
        String theoryText = theme.getTheoryText() == null ? "" : theme.getTheoryText().trim();

        if (title.isEmpty()) {
            callback.onError("Введите название темы");
            return;
        }

        theme.setTitle(title);
        theme.setDescription(description);
        theme.setDifficultyLevel(difficulty.isEmpty() ? "Easy" : difficulty);
        theme.setTheoryText(theoryText);

        apiService.adminCreateTheme(theme).enqueue(new retrofit2.Callback<List<Theme>>() {
            @Override
            public void onResponse(retrofit2.Call<List<Theme>> call, retrofit2.Response<List<Theme>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Theme savedTheme = response.body().get(0);

                    executor.execute(() -> {
                        try {
                            themeDao.insert(savedTheme);
                        } catch (Exception e) {
                            Log.e("Repository", "Ошибка локального сохранения темы: " + e.getMessage(), e);
                        }

                        mainHandler.post(() -> callback.onSuccess(savedTheme));
                    });
                } else {
                    Log.e("Repository", "Сервер не создал тему: "
                            + response.code() + " | " + getErrorBody(response));

                    saveThemeLocallyOnly(theme, callback);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<Theme>> call, Throwable t) {
                Log.e("Repository", "Ошибка сети adminCreateTheme: " + t.getMessage(), t);
                saveThemeLocallyOnly(theme, callback);
            }
        });
    }

    private void saveThemeLocallyOnly(Theme theme, DataCallback<Theme> callback) {
        executor.execute(() -> {
            try {
                Long maxId = themeDao.getMaxThemeId();
                long newId = maxId == null ? 1L : maxId + 1L;

                theme.setId(newId);
                themeDao.insert(theme);

                mainHandler.post(() -> callback.onSuccess(theme));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локального создания темы: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось создать тему"));
            }
        });
    }

    public void adminCreateWord(Word word, DataCallback<Word> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        Word preparedWord = prepareAdminWord(word);

        if (preparedWord == null) {
            callback.onError("Заполните термин, перевод и тему");
            return;
        }

        apiService.adminCreateWord(preparedWord).enqueue(new Callback<List<Word>>() {
            @Override
            public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Word savedWord = response.body().get(0);
                    saveAdminWordLocal(savedWord, callback);
                } else {
                    saveAdminWordLocalWithGeneratedId(preparedWord, callback);
                }
            }

            @Override
            public void onFailure(Call<List<Word>> call, Throwable t) {
                saveAdminWordLocalWithGeneratedId(preparedWord, callback);
            }
        });
    }

    private Word prepareAdminWord(Word word) {
        if (word == null) {
            return null;
        }

        Long themeId = word.getThemeId();
        String term = normalizeText(word.getTerm());
        String translation = normalizeText(word.getTranslation());

        if (themeId == null || themeId <= 0 || term.isEmpty() || translation.isEmpty()) {
            return null;
        }

        word.setTerm(term);
        word.setTranslation(translation);
        word.setDefinition(normalizeText(word.getDefinition()));
        word.setTranscription(normalizeText(word.getTranscription()));
        word.setExampleSentence(normalizeText(word.getExampleSentence()));

        return word;
    }

    private void saveAdminWordLocal(Word savedWord, DataCallback<Word> callback) {
        executor.execute(() -> {
            try {
                wordDao.insert(savedWord);
                mainHandler.post(() -> callback.onSuccess(savedWord));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локального сохранения термина: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось сохранить термин"));
            }
        });
    }

    private void saveAdminWordLocalWithGeneratedId(Word word, DataCallback<Word> callback) {
        executor.execute(() -> {
            try {
                if (word.getId() == null || word.getId() <= 0) {
                    Long maxId = wordDao.getMaxWordId();
                    word.setId(maxId == null ? 1L : maxId + 1L);
                }

                wordDao.insert(word);
                mainHandler.post(() -> callback.onSuccess(word));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локального создания термина: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось создать термин"));
            }
        });
    }

    private void saveWordLocallyOnly(Word word, DataCallback<Word> callback) {
        executor.execute(() -> {
            try {
                Long maxId = wordDao.getMaxWordId();
                long newId = maxId == null ? 1L : maxId + 1L;

                word.setId(newId);
                wordDao.insert(word);

                mainHandler.post(() -> callback.onSuccess(word));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локального создания слова: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось добавить термин"));
            }
        });
    }

    public boolean isCurrentUserAdmin() {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        return currentUser != null
                && currentUser.getRole() != null
                && "admin".equalsIgnoreCase(currentUser.getRole().trim());
    }

    public void adminUpdateTheme(Theme theme, DataCallback<Theme> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (theme == null || theme.getId() == null) {
            callback.onError("Тема не найдена");
            return;
        }

        apiService.adminUpdateTheme("eq." + theme.getId(), theme).enqueue(new Callback<List<Theme>>() {
            @Override
            public void onResponse(Call<List<Theme>> call, Response<List<Theme>> response) {
                Theme savedTheme = theme;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    savedTheme = response.body().get(0);
                }

                Theme finalSavedTheme = savedTheme;
                executor.execute(() -> {
                    try {
                        themeDao.insert(finalSavedTheme);
                    } catch (Exception e) {
                        Log.e("Repository", "Ошибка локального обновления темы: " + e.getMessage(), e);
                    }

                    mainHandler.post(() -> callback.onSuccess(finalSavedTheme));
                });
            }

            @Override
            public void onFailure(Call<List<Theme>> call, Throwable t) {
                executor.execute(() -> {
                    try {
                        themeDao.insert(theme);
                        mainHandler.post(() -> callback.onSuccess(theme));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Не удалось обновить тему"));
                    }
                });
            }
        });
    }

    public void adminDeleteTheme(Long themeId, DataCallback<Void> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (themeId == null) {
            callback.onError("Тема не найдена");
            return;
        }

        executor.execute(() -> {
            List<Word> localWords = new ArrayList<>();

            try {
                List<Word> wordsFromDb = wordDao.getWordsByTheme(themeId);

                if (wordsFromDb != null) {
                    localWords.addAll(wordsFromDb);
                }
            } catch (Exception e) {
                Log.e("Repository", "Не удалось получить слова темы перед удалением: " + e.getMessage(), e);
            }

            List<Long> wordIds = extractWordIds(localWords);
            String idsFilter = buildIdsFilter(wordIds);

            mainHandler.post(() -> deleteThemeCascadeRemote(themeId, idsFilter, callback));
        });
    }

    private void deleteThemeCascadeRemote(Long themeId, String wordIdsFilter, DataCallback<Void> callback) {
        apiService.adminDeleteProgressByWordIds(wordIdsFilter).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                deleteWordsByThemeRemote(themeId, callback);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                deleteWordsByThemeRemote(themeId, callback);
            }
        });
    }

    private void deleteWordsByThemeRemote(Long themeId, DataCallback<Void> callback) {
        apiService.adminDeleteWordsByThemeId("eq." + themeId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                deleteThemeRemote(themeId, callback);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                deleteThemeRemote(themeId, callback);
            }
        });
    }

    private void deleteThemeRemote(Long themeId, DataCallback<Void> callback) {
        apiService.adminDeleteTheme("eq." + themeId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                deleteThemeLocalCascade(themeId, callback);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                deleteThemeLocalCascade(themeId, callback);
            }
        });
    }

    private void deleteThemeLocalCascade(Long themeId, DataCallback<Void> callback) {
        executor.execute(() -> {
            try {
                userWordDao.deleteUserWordsByThemeId(themeId);
                wordDao.deleteWordsByThemeId(themeId);
                themeDao.deleteThemeById(themeId);

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка каскадного удаления темы: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось удалить тему"));
            }
        });
    }

    public void adminUpdateWord(Word word, DataCallback<Word> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (word == null || word.getId() == null) {
            callback.onError("Термин не найден");
            return;
        }

        apiService.adminUpdateWord("eq." + word.getId(), word).enqueue(new Callback<List<Word>>() {
            @Override
            public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                Word savedWord = word;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    savedWord = response.body().get(0);
                }

                Word finalSavedWord = savedWord;
                executor.execute(() -> {
                    try {
                        wordDao.insert(finalSavedWord);
                    } catch (Exception e) {
                        Log.e("Repository", "Ошибка локального обновления слова: " + e.getMessage(), e);
                    }

                    mainHandler.post(() -> callback.onSuccess(finalSavedWord));
                });
            }

            @Override
            public void onFailure(Call<List<Word>> call, Throwable t) {
                executor.execute(() -> {
                    try {
                        wordDao.insert(word);
                        mainHandler.post(() -> callback.onSuccess(word));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Не удалось обновить термин"));
                    }
                });
            }
        });
    }

    public void adminDeleteWord(Long wordId, DataCallback<Void> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (wordId == null) {
            callback.onError("Термин не найден");
            return;
        }

        executor.execute(() -> {
            Word localWord = null;

            try {
                localWord = wordDao.getWordById(wordId);
            } catch (Exception e) {
                Log.e("Repository", "Не удалось получить слово перед удалением: " + e.getMessage(), e);
            }

            Word finalLocalWord = localWord;

            mainHandler.post(() -> deleteWordRemoteCascade(wordId, finalLocalWord, callback));
        });
    }

    private void deleteWordRemoteCascade(Long wordId, Word localWord, DataCallback<Void> callback) {
        apiService.adminDeleteProgressByWordId("eq." + wordId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                deleteWordRemote(wordId, localWord, callback);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                deleteWordRemote(wordId, localWord, callback);
            }
        });
    }

    private void deleteWordRemote(Long wordId, Word localWord, DataCallback<Void> callback) {
        apiService.adminDeleteWord("eq." + wordId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                // Перед локальным удалением получаем ID пользователя
                getCurrentUserId(userId -> {
                    // Теперь передаем 4 аргумента, включая userId
                    deleteWordLocalCascade(wordId, localWord, userId, callback);
                });
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Даже если серверная ошибка, пытаемся удалить локально
                getCurrentUserId(userId -> {
                    // Теперь передаем 4 аргумента, включая userId
                    deleteWordLocalCascade(wordId, localWord, userId, callback);
                });
            }
        });
    }

    // Убедитесь, что сам метод deleteWordLocalCascade принимает Integer userId
    private void deleteWordLocalCascade(Long wordId, Word localWord, Integer userId, DataCallback<Void> callback) {
        executor.execute(() -> {
            try {
                if (localWord != null && localWord.getTerm() != null && userId != null && userId != -1) {
                    userWordDao.deleteUserWordsByTerm(userId, localWord.getTerm());
                }

                wordDao.deleteWordById(wordId);

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка каскадного удаления слова: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось удалить термин"));
            }
        });
    }

    private void deleteWordLocal(Long wordId, DataCallback<Void> callback) {
        executor.execute(() -> {
            try {
                wordDao.deleteWordById(wordId);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Не удалось удалить термин"));
            }
        });
    }

    public void getUnlearnedWordsByTheme(Long themeId, DataCallback<List<Word>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        getWordsByTheme(themeId, new DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                apiService.getUserProgressByUser(
                        "eq." + currentUser.getId(),
                        "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
                ).enqueue(new Callback<List<UserWordProgress>>() {
                    @Override
                    public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> response) {
                        List<Long> learnedWordIds = new ArrayList<>();

                        if (response.isSuccessful() && response.body() != null) {
                            for (UserWordProgress progress : response.body()) {
                                if (progress != null
                                        && progress.getWordId() != null
                                        && progress.getIsLearned()) {
                                    learnedWordIds.add(progress.getWordId());
                                }
                            }
                        }

                        List<Word> result = new ArrayList<>();

                        if (words != null) {
                            for (Word word : words) {
                                if (word == null || word.getId() == null) continue;

                                String term = word.getTerm() == null ? "" : word.getTerm().trim();
                                String translation = word.getTranslation() == null ? "" : word.getTranslation().trim();

                                if (!term.isEmpty()
                                        && !translation.isEmpty()
                                        && !learnedWordIds.contains(word.getId())) {
                                    result.add(word);
                                }
                            }
                        }

                        mainHandler.post(() -> callback.onSuccess(result));
                    }

                    @Override
                    public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                        List<Word> fallback = new ArrayList<>();

                        if (words != null) {
                            for (Word word : words) {
                                if (word == null) continue;

                                String term = word.getTerm() == null ? "" : word.getTerm().trim();
                                String translation = word.getTranslation() == null ? "" : word.getTranslation().trim();

                                if (!term.isEmpty() && !translation.isEmpty()) {
                                    fallback.add(word);
                                }
                            }
                        }

                        mainHandler.post(() -> callback.onSuccess(fallback));
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void markWordsAsLearned(List<Word> words, DataCallback<Void> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        if (words == null || words.isEmpty()) {
            callback.onSuccess(null);
            return;
        }

        final int[] completed = {0};

        for (Word word : words) {
            if (word == null || word.getId() == null) {
                completed[0]++;
                continue;
            }

            upsertWordProgress(
                    currentUser.getId().longValue(),
                    word.getId(),
                    true,
                    false,
                    new DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            completed[0]++;

                            if (completed[0] >= words.size()) {
                                callback.onSuccess(null);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            completed[0]++;

                            if (completed[0] >= words.size()) {
                                callback.onSuccess(null);
                            }
                        }
                    }
            );
        }
    }

    public void recordWordMistake(Word word) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) return;
        if (word == null || word.getId() == null) return;

        upsertWordProgress(
                currentUser.getId().longValue(),
                word.getId(),
                false,
                true,
                new DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("Repository", "Ошибка записи ошибки слова: " + error);
                    }
                }
        );
    }

    private void upsertWordProgress(Long userId,
                                    Long wordId,
                                    boolean markLearned,
                                    boolean addMistake,
                                    DataCallback<Void> callback) {
        apiService.getUserWordProgress(
                "eq." + userId,
                "eq." + wordId,
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    UserWordProgress existing = response.body().get(0);

                    existing.setCorrectAnswersCount(existing.getCorrectAnswersCount() + (markLearned ? 1 : 0));
                    existing.setMistakesCount(existing.getMistakesCount() + (addMistake ? 1 : 0));

                    if (markLearned) {
                        existing.setIsLearned(true);
                    }

                    apiService.updateWordProgress("eq." + existing.getId(), existing)
                            .enqueue(new Callback<Void>() {
                                @Override
                                public void onResponse(Call<Void> call, Response<Void> response) {
                                    callback.onSuccess(null);
                                }

                                @Override
                                public void onFailure(Call<Void> call, Throwable t) {
                                    callback.onError(t.getMessage());
                                }
                            });
                } else {
                    UserWordProgress progress = new UserWordProgress();
                    progress.setUserId(userId);
                    progress.setWordId(wordId);
                    progress.setCorrectAnswersCount(markLearned ? 1 : 0);
                    progress.setMistakesCount(addMistake ? 1 : 0);
                    progress.setIsLearned(markLearned);

                    apiService.createWordProgress(progress).enqueue(new Callback<List<UserWordProgress>>() {
                        @Override
                        public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> response) {
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                            callback.onError(t.getMessage());
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getLearnedWordsForTraining(DataCallback<List<Word>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        apiService.getUserProgressByUser(
                "eq." + currentUser.getId(),
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError("Не удалось загрузить прогресс слов"));
                    return;
                }

                List<Long> learnedWordIds = new ArrayList<>();

                for (UserWordProgress progress : response.body()) {
                    if (progress != null && progress.getWordId() != null && progress.getIsLearned()) {
                        learnedWordIds.add(progress.getWordId());
                    }
                }

                if (learnedWordIds.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }

                loadWordsByIds(learnedWordIds, callback);
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка загрузки прогресса: " + t.getMessage()));
            }
        });
    }

    public void getMistakeWordsForTraining(DataCallback<List<Word>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        apiService.getUserProgressByUser(
                "eq." + currentUser.getId(),
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError("Не удалось загрузить ошибки"));
                    return;
                }

                List<Long> mistakeWordIds = new ArrayList<>();

                for (UserWordProgress progress : response.body()) {
                    if (progress == null) continue;

                    if (progress.getWordId() != null && progress.getMistakesCount() > 0) {
                        mistakeWordIds.add(progress.getWordId());
                    }
                }

                if (mistakeWordIds.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }

                loadWordsByIds(mistakeWordIds, callback);
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка загрузки ошибок: " + t.getMessage()));
            }
        });
    }

    private void loadWordsByIds(List<Long> ids, DataCallback<List<Word>> callback) {
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
            public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Word> validWords = new ArrayList<>();

                    for (Word word : response.body()) {
                        if (word == null) continue;

                        String term = word.getTerm() == null ? "" : word.getTerm().trim();
                        String translation = word.getTranslation() == null ? "" : word.getTranslation().trim();

                        if (!term.isEmpty() && !translation.isEmpty()) {
                            validWords.add(word);
                        }
                    }

                    executor.execute(() -> {
                        try {
                            wordDao.insertAll(validWords);
                        } catch (Exception e) {
                            Log.e("Repository", "Ошибка кеширования слов тренировки: " + e.getMessage(), e);
                        }

                        mainHandler.post(() -> callback.onSuccess(validWords));
                    });

                } else {
                    mainHandler.post(() -> callback.onError("Слова для тренировки не найдены"));
                }
            }

            @Override
            public void onFailure(Call<List<Word>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка загрузки слов: " + t.getMessage()));
            }
        });
    }

    public void getLearnedWordsCount(Integer userId, DataCallback<Integer> callback) {
        if (userId == null || userId <= 0) {
            callback.onError("Некорректный пользователь");
            return;
        }

        apiService.getUserProgressByUser(
                "eq." + userId,
                "word_id,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {

                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> callback.onError("Не удалось загрузить прогресс"));
                    return;
                }

                int count = 0;

                for (UserWordProgress progress : response.body()) {
                    if (progress != null
                            && progress.getIsLearned() != null
                            && progress.getIsLearned()) {
                        count++;
                    }
                }

                int finalCount = count;
                mainHandler.post(() -> callback.onSuccess(finalCount));
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
            }
        });
    }
    public void resolveWordMistake(Word word, DataCallback<Void> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        if (word == null || word.getId() == null) {
            callback.onError("Слово не найдено");
            return;
        }

        apiService.getUserWordProgress(
                "eq." + currentUser.getId(),
                "eq." + word.getId(),
                "id,user_id,word_id,correct_answers_count,mistakes_count,is_learned"
        ).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call,
                                   Response<List<UserWordProgress>> response) {

                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    UserWordProgress newProgress = new UserWordProgress();
                    newProgress.setUserId(currentUser.getId().longValue());
                    newProgress.setWordId(word.getId());
                    newProgress.setCorrectAnswersCount(1);
                    newProgress.setMistakesCount(0);
                    newProgress.setIsLearned(true);

                    apiService.createWordProgress(newProgress).enqueue(new Callback<List<UserWordProgress>>() {
                        @Override
                        public void onResponse(Call<List<UserWordProgress>> call,
                                               Response<List<UserWordProgress>> createResponse) {
                            mainHandler.post(() -> callback.onSuccess(null));
                        }

                        @Override
                        public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                            mainHandler.post(() -> callback.onError("Ошибка сохранения прогресса: " + t.getMessage()));
                        }
                    });

                    return;
                }

                UserWordProgress progress = response.body().get(0);

                int oldMistakesCount = progress.getMistakesCount();
                int newMistakesCount = Math.max(0, oldMistakesCount - 1);

                progress.setMistakesCount(newMistakesCount);
                progress.setCorrectAnswersCount(progress.getCorrectAnswersCount() + 1);
                progress.setIsLearned(true);

                apiService.updateWordProgress(
                        "eq." + progress.getId(),
                        progress
                ).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> updateResponse) {
                        mainHandler.post(() -> callback.onSuccess(null));
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        mainHandler.post(() -> callback.onError("Ошибка обновления прогресса: " + t.getMessage()));
                    }
                });
            }

            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка загрузки прогресса: " + t.getMessage()));
            }
        });
    }
    public void addUserWord(String word,
                            String translation,
                            String transcription,
                            String notes,
                            DataCallback<Void> callback) {
        addUserWord(
                null,
                null,
                word,
                translation,
                transcription,
                notes,
                callback
        );
    }

    public void addUserWord(Long themeId,
                            String themeTitle,
                            String word,
                            String translation,
                            String transcription,
                            String notes,
                            DataCallback<Void> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

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
                UserWord existing = userWordDao.findWordByUserAndTerm(currentUser.getId(), safeWord);

                if (existing != null) {
                    mainHandler.post(() -> callback.onError("Это слово уже есть в личном словаре"));
                    return;
                }

                Long finalThemeId = themeId;
                String finalThemeTitle = normalizeText(themeTitle);

                if (finalThemeId == null || finalThemeTitle.isEmpty() || "Без темы".equals(finalThemeTitle)) {
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

                Log.d("Repository", "Слово добавлено в личный словарь. localId=" + insertedId
                        + ", userId=" + currentUser.getId()
                        + ", word=" + safeWord);

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка добавления слова: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось добавить слово"));
            }
        });
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

    public void getUserPersonalWordsByTheme(Long themeId,
                                            String themeTitle,
                                            DataCallback<List<UserWord>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

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
                Log.e("Repository", "Ошибка загрузки словаря по теме: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить слова темы"));
            }
        });
    }

    public void getUserDictionaryThemeTitles(DataCallback<List<String>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        executor.execute(() -> {
            try {
                List<String> themes = userWordDao.getUserDictionaryThemeTitles(currentUser.getId());
                mainHandler.post(() -> callback.onSuccess(themes));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка загрузки тем словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить темы словаря"));
            }
        });
    }
    public void repairPersonalDictionaryThemes(DataCallback<Void> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

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

                        if (sourceWord == null || sourceWord.getThemeId() == null) continue;

                        Theme theme = themeDao.getThemeById(sourceWord.getThemeId());

                        if (theme == null || theme.getTitle() == null || theme.getTitle().trim().isEmpty()) {
                            continue;
                        }

                        userWordDao.updateUserWordTheme(
                                userWord.getId(),
                                sourceWord.getThemeId(),
                                theme.getTitle().trim()
                        );
                    }
                }

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка восстановления тем словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось восстановить темы словаря"));
            }
        });
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

    private Theme findThemeForWordLocally(String term, String translation) {
        String safeTerm = normalizeText(term);
        String safeTranslation = normalizeText(translation);

        if (safeTerm.isEmpty()) {
            return null;
        }

        try {
            Word sourceWord = null;

            if (!safeTranslation.isEmpty()) {
                sourceWord = wordDao.getWordByTermAndTranslation(safeTerm, safeTranslation);
            }

            if (sourceWord == null) {
                sourceWord = wordDao.getWordByTerm(safeTerm);
            }

            if (sourceWord == null || sourceWord.getThemeId() == null) {
                return null;
            }

            return themeDao.getThemeById(sourceWord.getThemeId());
        } catch (Exception e) {
            Log.e("Repository", "Ошибка автопривязки темы: " + e.getMessage(), e);
            return null;
        }
    }

    private String buildIdsFilter(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "in.(-1)";
        }

        StringBuilder builder = new StringBuilder("in.(");

        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }

            builder.append(ids.get(i));
        }

        builder.append(")");
        return builder.toString();
    }

    private List<Long> extractWordIds(List<Word> words) {
        List<Long> ids = new ArrayList<>();

        if (words == null) {
            return ids;
        }

        for (Word word : words) {
            if (word != null && word.getId() != null) {
                ids.add(word.getId());
            }
        }

        return ids;
    }

    public void adminCreateWordsBulk(Long themeId,
                                     List<Word> words,
                                     DataCallback<List<Word>> callback) {
        if (!isCurrentUserAdmin()) {
            callback.onError("Недостаточно прав администратора");
            return;
        }

        if (themeId == null || themeId <= 0) {
            callback.onError("Выберите тему");
            return;
        }

        List<Word> preparedWords = prepareBulkWords(themeId, words);

        if (preparedWords.isEmpty()) {
            callback.onError("Добавьте хотя бы один корректный термин");
            return;
        }

        apiService.adminCreateWords(preparedWords).enqueue(new Callback<List<Word>>() {
            @Override
            public void onResponse(Call<List<Word>> call, Response<List<Word>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    saveAdminWordsLocal(response.body(), callback);
                } else {
                    saveAdminWordsLocalWithGeneratedIds(preparedWords, callback);
                }
            }

            @Override
            public void onFailure(Call<List<Word>> call, Throwable t) {
                saveAdminWordsLocalWithGeneratedIds(preparedWords, callback);
            }
        });
    }

    private List<Word> prepareBulkWords(Long themeId, List<Word> words) {
        List<Word> result = new ArrayList<>();

        if (words == null) {
            return result;
        }

        for (Word word : words) {
            if (word == null) continue;

            String term = normalizeText(word.getTerm());
            String translation = normalizeText(word.getTranslation());

            if (term.isEmpty() || translation.isEmpty()) {
                continue;
            }

            Word prepared = new Word();
            prepared.setId(word.getId());
            prepared.setThemeId(themeId);
            prepared.setTerm(term);
            prepared.setTranslation(translation);
            prepared.setDefinition(normalizeText(word.getDefinition()));
            prepared.setTranscription(normalizeText(word.getTranscription()));
            prepared.setExampleSentence(normalizeText(word.getExampleSentence()));

            result.add(prepared);
        }

        return result;
    }

    private void saveAdminWordsLocal(List<Word> words, DataCallback<List<Word>> callback) {
        executor.execute(() -> {
            try {
                wordDao.insertAll(words);
                mainHandler.post(() -> callback.onSuccess(words));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локального сохранения терминов: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось сохранить термины"));
            }
        });
    }

    private void saveAdminWordsLocalWithGeneratedIds(List<Word> words, DataCallback<List<Word>> callback) {
        executor.execute(() -> {
            try {
                Long maxId = wordDao.getMaxWordId();
                long nextId = maxId == null ? 1L : maxId + 1L;

                for (Word word : words) {
                    if (word.getId() == null || word.getId() <= 0) {
                        word.setId(nextId);
                        nextId++;
                    }
                }

                wordDao.insertAll(words);
                mainHandler.post(() -> callback.onSuccess(words));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка локального массового создания терминов: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось создать термины"));
            }
        });
    }

    public void isWordInPersonalDictionary(Word word, DataCallback<Boolean> callback) {
        if (word == null || word.getTerm() == null || word.getTerm().trim().isEmpty()) {
            callback.onSuccess(false);
            return;
        }

        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        String safeTerm = word.getTerm().trim();

        executor.execute(() -> {
            try {
                UserWord existingWord = userWordDao.findWordByUserAndTerm(
                        currentUser.getId(),
                        safeTerm
                );

                mainHandler.post(() -> callback.onSuccess(existingWord != null));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка проверки слова в словаре: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось проверить слово в словаре"));
            }
        });
    }
    public void updateUsername(String username, DataCallback<User> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        String safeUsername = username == null ? "" : username.trim();

        if (safeUsername.isEmpty()) {
            callback.onError("Введите имя пользователя");
            return;
        }

        if (safeUsername.length() < 3) {
            callback.onError("Имя пользователя должно быть не короче 3 символов");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("username", safeUsername);

        apiService.updateUserProfileRaw("eq." + currentUser.getId(), payload)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            currentUser.setUsername(safeUsername);
                            saveCurrentUserToPrefs(currentUser);

                            executor.execute(() -> {
                                try {
                                    userDao.insertUser(currentUser);
                                } catch (Exception e) {
                                    Log.e("Repository", "Ошибка локального обновления username: " + e.getMessage(), e);
                                }

                                mainHandler.post(() -> callback.onSuccess(currentUser));
                            });
                        } else {
                            Log.e("Repository", "Ошибка обновления username: "
                                    + response.code() + " | " + getErrorBody(response));
                            mainHandler.post(() -> callback.onError("Не удалось обновить профиль"));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e("Repository", "Ошибка сети updateUsername: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
                    }
                });
    }

    public void getMasteredThemesCount(DataCallback<Integer> callback) {
        getThemes(new DataCallback<List<Theme>>() {
            @Override
            public void onSuccess(List<Theme> themes) {
                getLearnedWords(new DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> learnedWords) {
                        if (themes == null || themes.isEmpty()) {
                            callback.onSuccess(0);
                            return;
                        }

                        List<Long> learnedIds = new ArrayList<>();

                        if (learnedWords != null) {
                            for (Word word : learnedWords) {
                                if (word != null && word.getId() != null) {
                                    learnedIds.add(word.getId());
                                }
                            }
                        }

                        calculateMasteredThemesCount(themes, learnedIds, callback);
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private void calculateMasteredThemesCount(List<Theme> themes,
                                              List<Long> learnedWordIds,
                                              DataCallback<Integer> callback) {
        if (themes == null || themes.isEmpty()) {
            callback.onSuccess(0);
            return;
        }

        final int[] completedRequests = {0};
        final int[] masteredCount = {0};
        final int totalThemes = themes.size();

        for (Theme theme : themes) {
            if (theme == null || theme.getId() == null) {
                completedRequests[0]++;

                if (completedRequests[0] == totalThemes) {
                    callback.onSuccess(masteredCount[0]);
                }

                continue;
            }

            getWordsByTheme(theme.getId(), new DataCallback<List<Word>>() {
                @Override
                public void onSuccess(List<Word> words) {
                    if (isThemeMastered(words, learnedWordIds)) {
                        masteredCount[0]++;
                    }

                    completedRequests[0]++;

                    if (completedRequests[0] == totalThemes) {
                        callback.onSuccess(masteredCount[0]);
                    }
                }

                @Override
                public void onError(String error) {
                    completedRequests[0]++;

                    if (completedRequests[0] == totalThemes) {
                        callback.onSuccess(masteredCount[0]);
                    }
                }
            });
        }
    }

    private boolean isThemeMastered(List<Word> words, List<Long> learnedWordIds) {
        if (words == null || words.isEmpty()) {
            return false;
        }

        if (learnedWordIds == null || learnedWordIds.isEmpty()) {
            return false;
        }

        for (Word word : words) {
            if (word == null || word.getId() == null) {
                return false;
            }

            if (!learnedWordIds.contains(word.getId())) {
                return false;
            }
        }

        return true;
    }

    public void getUserOverallStatistics(DataCallback<UserOverallStats> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        User user = currentUser;
        int userId = user.getId();

        getLearnedWordIdsForCurrentUser(new DataCallback<List<Long>>() {
            @Override
            public void onSuccess(List<Long> learnedWordIds) {
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
                                totalLessons,
                                totalWords,
                                totalMistakes,
                                fixedErrors,
                                accuracy,
                                learnedWordIds != null ? learnedWordIds.size() : 0,
                                totalXp,
                                level,
                                leagueData.title,
                                leagueData.icon
                        );

                        mainHandler.post(() -> callback.onSuccess(stats));
                    } catch (Exception e) {
                        Log.e("Repository", "Ошибка расчёта общей статистики: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Не удалось рассчитать статистику"));
                    }
                });
            }

            @Override
            public void onError(String error) {
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
                                totalLessons,
                                totalWords,
                                totalMistakes,
                                fixedErrors,
                                accuracy,
                                0,
                                totalXp,
                                level,
                                leagueData.title,
                                leagueData.icon
                        );

                        mainHandler.post(() -> callback.onSuccess(stats));
                    } catch (Exception e) {
                        Log.e("Repository", "Ошибка расчёта общей статистики без learnedIds: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Не удалось рассчитать статистику"));
                    }
                });
            }
        });
    }

    public void getThemeProgressStatistics(DataCallback<List<ThemeProgressStats>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        getLearnedWordIdsForCurrentUser(new DataCallback<List<Long>>() {
            @Override
            public void onSuccess(List<Long> learnedWordIds) {
                getThemes(new DataCallback<List<Theme>>() {
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
                callback.onError(error != null ? error : "Не удалось загрузить изученные слова");
            }
        });
    }

    private void getLearnedWordIdsForCurrentUser(DataCallback<List<Long>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

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
                    mainHandler.post(() -> callback.onError("Не удалось загрузить прогресс пользователя"));
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
                        "Ошибка загрузки прогресса: " + (t.getMessage() != null ? t.getMessage() : "")
                ));
            }
        });
    }

    private void calculateThemeProgressFromActualWords(List<Theme> themes,
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

            getWordsByTheme(theme.getId(), new DataCallback<List<Word>>() {
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
                            theme,
                            new ArrayList<>(),
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

    private ThemeProgressStats buildThemeProgressFromActualWords(Theme theme,
                                                                 List<Word> actualThemeWords,
                                                                 List<Long> learnedWordIds) {
        int totalWords = actualThemeWords != null ? actualThemeWords.size() : 0;
        int learnedWords = 0;

        if (actualThemeWords != null && learnedWordIds != null && !learnedWordIds.isEmpty()) {
            for (Word word : actualThemeWords) {
                if (word == null || word.getId() == null) continue;

                if (learnedWordIds.contains(word.getId())) {
                    learnedWords++;
                }
            }
        }

        int progressPercent = totalWords > 0
                ? Math.max(0, Math.min(100, (learnedWords * 100) / totalWords))
                : 0;

        boolean mastered = totalWords > 0 && learnedWords >= totalWords;

        return new ThemeProgressStats(
                theme,
                learnedWords,
                totalWords,
                progressPercent,
                mastered
        );
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

    private void calculateThemeProgressStatistics(List<Theme> themes,
                                                  List<Word> learnedWords,
                                                  DataCallback<List<ThemeProgressStats>> callback) {
        if (themes == null || themes.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        List<Long> learnedIds = new ArrayList<>();

        if (learnedWords != null) {
            for (Word word : learnedWords) {
                if (word != null && word.getId() != null) {
                    learnedIds.add(word.getId());
                }
            }
        }

        List<ThemeProgressStats> result = new ArrayList<>();
        final int[] completedRequests = {0};
        int totalRequests = themes.size();

        for (Theme theme : themes) {
            if (theme == null || theme.getId() == null) {
                completedRequests[0]++;

                if (completedRequests[0] >= totalRequests) {
                    mainHandler.post(() -> callback.onSuccess(result));
                }

                continue;
            }

            getWordsByTheme(theme.getId(), new DataCallback<List<Word>>() {
                @Override
                public void onSuccess(List<Word> words) {
                    ThemeProgressStats stats = buildThemeProgress(theme, words, learnedIds);
                    result.add(stats);

                    completedRequests[0]++;

                    if (completedRequests[0] >= totalRequests) {
                        mainHandler.post(() -> callback.onSuccess(result));
                    }
                }

                @Override
                public void onError(String error) {
                    ThemeProgressStats stats = buildThemeProgress(theme, new ArrayList<>(), learnedIds);
                    result.add(stats);

                    completedRequests[0]++;

                    if (completedRequests[0] >= totalRequests) {
                        mainHandler.post(() -> callback.onSuccess(result));
                    }
                }
            });
        }
    }

    private ThemeProgressStats buildThemeProgress(Theme theme,
                                                  List<Word> words,
                                                  List<Long> learnedIds) {
        int total = words != null ? words.size() : 0;
        int learned = 0;

        if (words != null && learnedIds != null) {
            for (Word word : words) {
                if (word != null && word.getId() != null && learnedIds.contains(word.getId())) {
                    learned++;
                }
            }
        }

        int percent = total > 0 ? Math.min(100, (learned * 100) / total) : 0;
        boolean mastered = total > 0 && learned >= total;

        return new ThemeProgressStats(theme, learned, total, percent, mastered);
    }

    public void getRecentLessonHistory(int limit, DataCallback<List<LessonHistory>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

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
                Log.e("Repository", "Ошибка загрузки истории тренировок: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить историю занятий"));
            }
        });
    }

    public void getLessonHistoryForStatistics(DataCallback<List<LessonHistory>> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

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
                Log.e("Repository", "Ошибка загрузки LessonHistory: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить историю занятий"));
            }
        });
    }

    private LeagueData getStatisticsLeagueData(int xp) {
        if (xp >= 2500) {
            return new LeagueData("Алмазная лига", "💎");
        }

        if (xp >= 1200) {
            return new LeagueData("Изумрудная лига", "💚");
        }

        if (xp >= 600) {
            return new LeagueData("Золотая лига", "🥇");
        }

        if (xp >= 250) {
            return new LeagueData("Серебряная лига", "🥈");
        }

        return new LeagueData("Бронзовая лига", "🥉");
    }

    private static class LeagueData {
        private final String title;
        private final String icon;

        private LeagueData(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }
    private void syncPersonalWordsFromServer(Integer userId, DataCallback<Void> callback) {
        // Используем правильное название метода из ApiService
        // getUserPersonalWordsFromServer принимает String userId и String order
        apiService.getUserPersonalWordsFromServer(
                        "eq." + userId,       // фильтр user_id
                        "date_added.desc"     // сортировка
                )
                .enqueue(new Callback<List<UserWord>>() {
                    @Override
                    public void onResponse(Call<List<UserWord>> call, Response<List<UserWord>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<UserWord> remoteWords = response.body();

                            executor.execute(() -> {
                                try {
                                    // Сначала удаляем старые локальные слова пользователя
                                    userWordDao.deleteAllUserWordsForUser(userId);

                                    // Затем вставляем актуальные данные с сервера
                                    if (!remoteWords.isEmpty()) {
                                        userWordDao.insertAll(remoteWords);
                                    }

                                    mainHandler.post(() -> callback.onSuccess(null));

                                } catch (Exception e) {
                                    Log.e("Repository", "Ошибка сохранения слов: " + e.getMessage(), e);
                                    mainHandler.post(() -> callback.onError("Ошибка БД: " + e.getMessage()));
                                }
                            });
                        } else {
                            String errorMsg = getErrorBody(response);
                            Log.e("Repository", "Ошибка сервера при синхронизации: " + errorMsg);
                            mainHandler.post(() -> callback.onError("Ошибка сервера: " + errorMsg));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<UserWord>> call, Throwable t) {
                        Log.e("Repository", "Сбой сети при синхронизации: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError("Нет соединения: " + t.getMessage()));
                    }
                });
    }
    public void syncPersonalWords(DataCallback<Void> callback) {
        getCurrentUserId(userId -> {
            if (userId == -1) {
                mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
                return;
            }

            syncPersonalWords(userId, callback);
        });
    }

    public void syncPersonalWords(Integer userId, DataCallback<Void> callback) {
        if (userId == null || userId == -1) {
            mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
            return;
        }

        executor.execute(() -> {
            try {
                // Ничего не удаляем и не перезаписываем с сервера.
                // Просто подтверждаем, что локальные данные готовы к загрузке.
                List<UserWord> localWords = userWordDao.getUserWords(userId);

                Log.d("Repository", "Личный словарь загружен локально. Кол-во слов: "
                        + (localWords != null ? localWords.size() : 0));

                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e("Repository", "Ошибка подготовки личного словаря: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить личный словарь"));
            }
        });
    }
}
