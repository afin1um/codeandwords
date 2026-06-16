package com.example.codeandwords.data;

import static com.example.codeandwords.utils.SecurityUtils.hashPassword;

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
import com.example.codeandwords.data.achievement.AchievementRepository;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import com.example.codeandwords.data.auth.AuthRepository;
import com.example.codeandwords.data.speech.TtsManager;
import com.example.codeandwords.data.personal.PersonalDictionaryRepository;
import com.example.codeandwords.data.theme.ThemeRepository;
import com.example.codeandwords.data.word.WordRepository;
import com.example.codeandwords.data.schedule.ScheduleRepository;
import com.example.codeandwords.data.social.FriendsRepository;
import com.example.codeandwords.data.social.TeamsRepository;
import com.example.codeandwords.data.stats.StatsRepository;
import com.example.codeandwords.data.progress.WordProgressRepository;
import com.example.codeandwords.data.admin.AdminRepository;

// Фасад (Singleton) над всеми специализированными репозиториями.
// Предоставляет единую точку доступа к данным для ViewModel и Activity.
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
    private final TtsManager ttsManager;
    private final FriendDao friendDao;
    private final TeamDao teamDao;
    private final TeamMemberDao teamMemberDao;
    private final ExecutorService networkExecutor;
    private static volatile Repository INSTANCE;

    // Специализированные репозитории
    private final AuthRepository authRepository;
    private final PersonalDictionaryRepository personalDictionaryRepository;
    private final ThemeRepository themeRepository;
    private final WordRepository wordRepository;
    private final ScheduleRepository scheduleRepository;
    private final FriendsRepository friendsRepository;
    private final TeamsRepository teamsRepository;
    private final StatsRepository statsRepository;
    private final AchievementRepository achievementRepository;
    private final WordProgressRepository wordProgressRepository;
    private final AdminRepository adminRepository;

    // Текущий авторизованный пользователь; static для доступа из вложенных репозиториев
    private static User currentUser;

    // Счётчик сессии для защиты от устаревших сетевых ответов при смене аккаунта
    private volatile int authSessionVersion = 0;

    public boolean isTtsReady() {
        return ttsManager.isReady();
    }

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

        // Инициализация DAO
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
        this.scheduleApiService = RetrofitClient.getFastApiService();

        this.executor = AppDatabase.databaseWriteExecutor;
        this.networkExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);

        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Инициализация репозитория авторизации с делегированием событий входа
        this.authRepository = new AuthRepository(
                appContext, userDao, apiService, executor, mainHandler);

        this.authRepository.setListener(new AuthRepository.AuthListener() {
            @Override
            public void recordLoginEvent() {
                Repository.this.recordLoginEvent();
            }

            @Override
            public String getErrorBody(Response<?> response) {
                return Repository.this.getErrorBody(response);
            }
        });

        this.ttsManager = new TtsManager(appContext);

        this.personalDictionaryRepository = new PersonalDictionaryRepository(
                userWordDao, wordDao, themeDao, apiService, executor, mainHandler);

        this.themeRepository = new ThemeRepository(themeDao, apiService, executor, mainHandler);
        this.wordRepository = new WordRepository(wordDao, apiService, executor, mainHandler);

        this.scheduleRepository = new ScheduleRepository(
                studyScheduleDao, scheduleApiService, executor, mainHandler);

        this.friendsRepository = new FriendsRepository(
                db, friendDao, userDao, apiService, scheduleApiService, executor, mainHandler);

        teamsRepository = new TeamsRepository(
                teamDao, teamMemberDao, teamChallengeDao,
                teamChallengeProgressDao, apiService, scheduleApiService,
                executor, mainHandler);

        // Делегируем выдачу XP и отметку победителя в Repository
        this.teamsRepository.setRewardListener(new TeamsRepository.TeamRewardListener() {
            @Override
            public void onGrantXp(int xpReward) {
                grantTeamChallengeXp(xpReward);
            }

            @Override
            public void onMarkWinner(int challengeId) {
                markTeamChallengeWinner(challengeId);
            }

            @Override
            public String toSqlTimestamp(long millis) {
                return Repository.this.toSqlTimestamp(millis);
            }
        });

        this.statsRepository = new StatsRepository(
                userDao, userStatsDao, lessonHistoryDao,
                achievementDao, dailyQuestDao, wordDao,
                apiService, executor, mainHandler);

        // Делегируем вспомогательные операции StatsRepository обратно в Repository
        this.statsRepository.setListener(new StatsRepository.StatsListener() {
            @Override
            public String toSqlTimestamp(long millis) {
                return Repository.this.toSqlTimestamp(millis);
            }

            @Override
            public int safeInt(Integer value) {
                return Repository.this.safeInt(value);
            }

            @Override
            public void saveCurrentUserToPrefs(User user) {
                Repository.this.saveCurrentUserToPrefs(user);
            }

            @Override
            public void refreshAchievementsSync(User user, UserStats stats) {
                Repository.this.refreshAchievementsSync(user, stats);
            }

            @Override
            public void updateQuestProgress(String type, int value) {
                statsRepository.updateQuestProgress(type, value);
            }

            @Override
            public void updateTeamChallengeProgressAfterLesson(int userId, int earnedXp) {
                Repository.this.updateTeamChallengeProgressAfterLesson(userId, earnedXp);
            }

            @Override
            public User getCurrentUser() {
                return currentUser;
            }

            @Override
            public void restoreCurrentUserFromPrefs() {
                Repository.this.restoreCurrentUserFromPrefs();
            }

            @Override
            public void addXp(int xpReward) {
                statsRepository.addXp(xpReward);
            }

            @Override
            public void getThemes(StatsRepository.DataCallback<List<Theme>> callback) {
                Repository.this.getThemes(new DataCallback<List<Theme>>() {
                    @Override
                    public void onSuccess(List<Theme> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
            }

            @Override
            public void getWordsByTheme(Long themeId,
                                        StatsRepository.DataCallback<List<Word>> callback) {
                Repository.this.loadWordsLocal(themeId, new DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
            }
        });

        this.achievementRepository = new AchievementRepository(
                db, achievementDao, userDao, apiService);

        // Делегируем вспомогательные операции AchievementRepository обратно в Repository
        this.achievementRepository.setListener(new AchievementRepository.AchievementListener() {
            @Override
            public int safeInt(Integer value) {
                return Repository.this.safeInt(value);
            }

            @Override
            public String toSqlTimestamp(long millis) {
                return Repository.this.toSqlTimestamp(millis);
            }

            @Override
            public void saveCurrentUserToPrefs(User user) {
                Repository.this.saveCurrentUserToPrefs(user);
            }

            @Override
            public void syncUserProgressToRemote(User user) {
                Repository.this.syncUserProgressToRemote(user);
            }

            @Override
            public User getCurrentUser() {
                return currentUser;
            }
        });

        this.wordProgressRepository = new WordProgressRepository(apiService, mainHandler);

        // Делегируем загрузку слов по ID и теме в локальные репозитории
        this.wordProgressRepository.setListener(new WordProgressRepository.WordProgressListener() {
            @Override
            public void loadWordsByIds(List<Long> ids,
                                       WordProgressRepository.DataCallback<List<Word>> callback) {
                Repository.this.loadWordsByIds(ids, new DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
            }

            @Override
            public void getWordsByTheme(Long themeId,
                                        WordProgressRepository.DataCallback<List<Word>> callback) {
                Repository.this.getWordsByTheme(themeId, new DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
            }
        });

        this.adminRepository = new AdminRepository(
                db, themeDao, wordDao, userWordDao, apiService, executor, mainHandler);

        // Делегируем вспомогательные операции AdminRepository обратно в Repository
        this.adminRepository.setListener(new AdminRepository.AdminListener() {
            @Override
            public String normalizeText(String value) {
                return Repository.this.normalizeText(value);
            }

            @Override
            public List<Long> extractWordIds(List<Word> words) {
                return Repository.this.extractWordIds(words);
            }

            @Override
            public String buildIdsFilter(List<Long> ids) {
                return Repository.this.buildIdsFilter(ids);
            }

            @Override
            public void getCurrentUserId(AdminRepository.AdminListener.OnUserIdRetrieved callback) {
                Repository.this.getCurrentUserId(callback::onRetrieved);
            }

            @Override
            public User getCurrentUser() {
                return currentUser;
            }

            @Override
            public void restoreCurrentUserFromPrefs() {
                Repository.this.restoreCurrentUserFromPrefs();
            }
        });

        restoreCurrentUserFromPrefs();
    }

    // Singleton с двойной проверкой блокировки
    public static Repository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (Repository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Repository(context);
                }
            }
        }
        return INSTANCE;
    }

    private void restoreCurrentUserFromPrefs() {
        authRepository.setCurrentUser(null);
        authRepository.restoreCurrentUserFromPrefs();
        currentUser = authRepository.getCurrentUserSync();
    }

    private void saveCurrentUserToPrefs(User user) {
        if (user == null || user.getId() == null) {
            return;
        }

        authRepository.saveCurrentUserToPrefs(user);
        authRepository.setCurrentUser(user);
        currentUser = user;
    }

    private void clearCurrentUserPrefs() {
        authRepository.clearCurrentUserPrefs();
        authRepository.setCurrentUser(null);
        currentUser = null;
    }

    private void cacheUserSafely(User user) {
        authRepository.cacheUserSafely(user);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    // Форматирует Unix-время в строку "yyyy-MM-dd HH:mm:ss" для Supabase
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

    // Сбрасывает кэши и флаги всех вложенных репозиториев при смене аккаунта.
    // Критично для singleton-репозитория: предотвращает утечку данных между аккаунтами.
    private void resetAllRepositoryStates() {
        try {
            wordProgressRepository.clearLearnedIdsCache();
        } catch (Exception e) {
            Log.e("Repository", "Ошибка сброса wordProgressRepository: " + e.getMessage(), e);
        }

        try {
            statsRepository.resetState();
        } catch (Exception e) {
            Log.e("Repository", "Ошибка сброса statsRepository: " + e.getMessage(), e);
        }

        try {
            achievementRepository.resetState();
        } catch (Exception e) {
            Log.e("Repository", "Ошибка сброса achievementRepository: " + e.getMessage(), e);
        }

        try {
            friendsRepository.resetState();
        } catch (Exception e) {
            Log.e("Repository", "Ошибка сброса friendsRepository: " + e.getMessage(), e);
        }

        try {
            teamsRepository.resetState();
        } catch (Exception e) {
            Log.e("Repository", "Ошибка сброса teamsRepository: " + e.getMessage(), e);
        }

        Log.d("Repository", "Все кэши и флаги репозиториев сброшены");
    }

    // Сбрасывает состояние всех репозиториев, очищает данные пользователя и вызывает callback
    public void logout(Runnable callback) {
        authSessionVersion++;

        resetAllRepositoryStates();

        authRepository.logout(() -> {
            currentUser = null;
            authRepository.setCurrentUser(null);

            if (callback != null) {
                callback.run();
            }
        });
    }

    public void getCurrentUser(DataCallback<User> callback) {
        if (currentUser != null && currentUser.getId() != null) {
            callback.onSuccess(currentUser);
            return;
        }

        restoreCurrentUserFromPrefs();

        if (currentUser != null && currentUser.getId() != null) {
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
        personalDictionaryRepository.deleteUserWord(word, onDone);
    }

    public void deleteUserWordByTerm(String term, DataCallback<Void> callback) {
        personalDictionaryRepository.deleteUserWordByTerm(
                term, currentUser,
                new PersonalDictionaryRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void addWordToPersonalDictionary(Word word, DataCallback<Void> callback) {
        personalDictionaryRepository.addWordToPersonalDictionary(
                word, currentUser,
                new PersonalDictionaryRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getThemes(DataCallback<List<Theme>> callback) {
        themeRepository.getThemes(
                new ThemeRepository.DataCallback<List<Theme>>() {
                    @Override
                    public void onSuccess(List<Theme> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    private void loadThemesLocal(DataCallback<List<Theme>> callback) {
        themeRepository.loadThemesLocal(
                new ThemeRepository.DataCallback<List<Theme>>() {
                    @Override
                    public void onSuccess(List<Theme> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getThemeById(long themeId, DataCallback<Theme> callback) {
        themeRepository.getThemeById(themeId,
                new ThemeRepository.DataCallback<Theme>() {
                    @Override
                    public void onSuccess(Theme data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    private void loadWordsLocal(Long themeId, DataCallback<List<Word>> callback) {
        wordRepository.loadWordsLocal(themeId,
                new WordRepository.DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    private void refreshAchievementsSync(User user, UserStats stats) {
        achievementRepository.refreshAchievementsSync(user, stats);
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
        ttsManager.speak(text, isSlow);
    }

    // Освобождает ресурсы TTS при уничтожении компонента
    public void onDestroy() {
        ttsManager.destroy();
    }

    public void findUserByUsername(String username, DataCallback<User> callback) {
        friendsRepository.findUserByUsername(username,
                new FriendsRepository.DataCallback<User>() {
                    @Override
                    public void onSuccess(User data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    // Добавляет друга с передачей объекта пользователя для локального кэширования
    public void addFriend(int friendId, User friendUser, DataCallback<Void> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        friendsRepository.addFriend(friendId, friendUser, currentUser,
                new FriendsRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getFriends(DataCallback<List<User>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        friendsRepository.getFriends(currentUser,
                new FriendsRepository.DataCallback<List<User>>() {
                    @Override
                    public void onSuccess(List<User> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    // Добавляет участников команды на сервере, затем создаёт задание
    private void createTeamMembersRemote(
            int teamId,
            List<Integer> memberIds,
            String challengeType,
            int targetValue,
            DataCallback<Integer> callback) {

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
                    mainHandler.post(() -> callback.onError(
                            "Команда создана, но участников добавить не удалось"));
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

    // Создаёт командное задание на сервере; при таймауте ищет созданное задание с задержкой
    private void createTeamChallengeRemote(
            int teamId,
            List<Integer> memberIds,
            String challengeType,
            int targetValue,
            DataCallback<Integer> callback) {

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

                // При таймауте задание могло всё-таки создаться — ищем с задержкой
                mainHandler.postDelayed(() -> {
                    findCreatedTeamChallengeAndCreateProgress(teamId, memberIds, callback);
                }, 1200);
            }
        });
    }

    // Создаёт записи прогресса для всех участников командного задания
    private void createTeamChallengeProgressRemote(
            int teamId,
            int challengeId,
            List<Integer> memberIds,
            DataCallback<Integer> callback) {

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
                    mainHandler.post(() -> callback.onError(
                            "Задание создано, но прогресс участников не создан"));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("Repository", "Ошибка сети createTeamChallengeProgressRemote: "
                        + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети при создании прогресса"));
            }
        });
    }

    // Разбирает JSON-объект командного задания в модель TeamChallenge
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

    // Обрабатывает одну запись прогресса: увеличивает счётчик или завершает задание при достижении цели
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
                    "eq." + progressId, payload
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

    // Завершает командное задание: проверяет дубли, определяет место, начисляет XP
    private void finishTeamChallengeProgress(
            int progressId,
            int challengeId,
            JsonObject challengeJson,
            int finalProgress) {

        // Проверяем, не завершена ли запись уже (защита от двойного начисления)
        scheduleApiService.getTeamProgressByIdRaw(
                "eq." + progressId, "id,is_completed,awarded_xp,place", 1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> checkCall,
                                   Response<List<JsonObject>> checkResponse) {
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
                    Log.d("Repository", "Командная награда уже была начислена. progressId="
                            + progressId);
                    return;
                }

                // Считаем завершивших участников для определения места
                scheduleApiService.getCompletedTeamProgressRaw(
                        "eq." + challengeId, "eq.true", "completed_at.asc"
                ).enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        int alreadyCompletedCount = 0;

                        if (response.isSuccessful() && response.body() != null) {
                            alreadyCompletedCount = response.body().size();
                        }

                        int place = alreadyCompletedCount + 1;
                        int rewardXp = getTeamChallengeRewardByPlace(challengeJson, place);

                        JsonObject payload = new JsonObject();
                        payload.addProperty("progress", finalProgress);
                        payload.addProperty("is_completed", true);
                        payload.addProperty("completed_at",
                                toSqlTimestamp(System.currentTimeMillis()));
                        payload.addProperty("place", place);
                        payload.addProperty("awarded_xp", rewardXp);

                        scheduleApiService.updateTeamChallengeProgressRaw(
                                "eq." + progressId, payload
                        ).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call,
                                                   Response<Void> updateResponse) {
                                if (updateResponse.isSuccessful()) {
                                    grantTeamChallengeXp(rewardXp);

                                    if (place == 1) {
                                        markTeamChallengeWinner(challengeId);
                                    }
                                } else {
                                    Log.e("Repository",
                                            "Не удалось завершить командный прогресс: "
                                                    + updateResponse.code() + " | "
                                                    + getErrorBody(updateResponse));
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                Log.e("Repository",
                                        "Ошибка сети finishTeamChallengeProgress update: "
                                                + t.getMessage(), t);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e("Repository", "Ошибка получения мест команды: "
                                + t.getMessage(), t);
                    }
                });
            }

            @Override
            public void onFailure(Call<List<JsonObject>> checkCall, Throwable t) {
                Log.e("Repository", "Ошибка проверки progressId перед начислением: "
                        + t.getMessage(), t);
            }
        });
    }

    // Возвращает XP-награду по занятому месту из JSON-объекта задания
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

    // Начисляет XP за командное задание текущему пользователю
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
                Log.e("Repository", "Ошибка начисления XP за командное задание: "
                        + e.getMessage(), e);
            }
        });
    }

    // Отмечает текущего пользователя победителем командного задания на сервере
    private void markTeamChallengeWinner(int challengeId) {
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("winner_user_id", currentUser.getId());
        payload.addProperty("completed_at", toSqlTimestamp(System.currentTimeMillis()));

        scheduleApiService.updateTeamChallengeRaw(
                "eq." + challengeId, payload
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
                Log.e("Repository", "Ошибка сети markTeamChallengeWinner: "
                        + t.getMessage(), t);
            }
        });
    }

    // Ищет последнее созданное задание команды и создаёт для него прогресс (устаревший метод)
    private void findCreatedChallengeAndCreateProgress(
            int teamId,
            List<Integer> memberIds,
            DataCallback<Integer> callback) {

        scheduleApiService.getTeamChallengeRaw(
                "eq." + teamId,
                "id,team_id,title,condition_type,target_value," +
                        "xp_first,xp_second,xp_other,is_completed,created_at",
                "id.desc",
                1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    int challengeId = response.body().get(0).get("id").getAsInt();
                    createTeamChallengeProgressRemote(teamId, challengeId, memberIds, callback);
                } else {
                    Log.e("Repository", "Задание команды не найдено после создания: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError(
                            "Команда создана, но задание не найдено. Откройте экран команды позже."));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Ошибка поиска задания команды после timeout: "
                        + t.getMessage(), t);
                mainHandler.post(() -> callback.onError(
                        "Команда создана, но задание пока не загрузилось"));
            }
        });
    }

    // Ищет последнее созданное задание команды и создаёт для него прогресс
    private void findCreatedTeamChallengeAndCreateProgress(
            int teamId,
            List<Integer> memberIds,
            DataCallback<Integer> callback) {

        scheduleApiService.getTeamChallengeRaw(
                "eq." + teamId,
                "id,team_id,title,condition_type,target_value," +
                        "xp_first,xp_second,xp_other,is_completed,created_at",
                "id.desc",
                1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    int challengeId = response.body().get(0).get("id").getAsInt();
                    createTeamChallengeProgressRemote(teamId, challengeId, memberIds, callback);
                } else {
                    Log.e("Repository", "Задание команды не найдено после создания: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError(
                            "Команда создана, но задание не найдено"));
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
        if (item == null) return null;

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

    // Добавляет команду в список, если она ещё не присутствует (по ID)
    private void addTeamIfNotExists(List<Team> teams, Team newTeam) {
        if (teams == null || newTeam == null || newTeam.id <= 0) return;

        for (Team team : teams) {
            if (team != null && team.id == newTeam.id) return;
        }

        teams.add(newTeam);
    }

    private void cacheTeamsLocal(List<Team> teams) {
        if (teams == null || teams.isEmpty()) return;

        executor.execute(() -> {
            for (Team team : teams) {
                try {
                    teamDao.insert(team);
                } catch (Exception e) {
                    Log.e("Repository", "Ошибка локального сохранения команды: "
                            + e.getMessage(), e);
                }
            }
        });
    }

    // Возвращает список команд пользователя из локальной БД
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

    private void loadWordsByIds(List<Long> ids, DataCallback<List<Word>> callback) {
        wordRepository.loadWordsByIds(ids,
                new WordRepository.DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    // Добавляет слово без темы в личный словарь текущего пользователя
    public void addUserWord(String word,
                            String translation,
                            String transcription,
                            String notes,
                            DataCallback<Void> callback) {
        if (currentUser == null) {
            restoreCurrentUserFromPrefs();
        }

        personalDictionaryRepository.addUserWord(
                word, translation, transcription, notes, currentUser,
                new PersonalDictionaryRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    // Добавляет слово с привязкой к теме в личный словарь текущего пользователя
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

        personalDictionaryRepository.addUserWord(
                themeId, themeTitle, word, translation, transcription, notes, currentUser,
                new PersonalDictionaryRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getUserPersonalWords(DataCallback<List<UserWord>> callback) {
        personalDictionaryRepository.getUserPersonalWords(
                currentUser,
                new PersonalDictionaryRepository.DataCallback<List<UserWord>>() {
                    @Override
                    public void onSuccess(List<UserWord> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getUserPersonalWordsByTheme(Long themeId, String themeTitle,
                                            DataCallback<List<UserWord>> callback) {
        personalDictionaryRepository.getUserPersonalWordsByTheme(
                themeId, themeTitle, currentUser,
                new PersonalDictionaryRepository.DataCallback<List<UserWord>>() {
                    @Override
                    public void onSuccess(List<UserWord> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getUserDictionaryThemeTitles(DataCallback<List<String>> callback) {
        personalDictionaryRepository.getUserDictionaryThemeTitles(
                currentUser,
                new PersonalDictionaryRepository.DataCallback<List<String>>() {
                    @Override
                    public void onSuccess(List<String> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    // Восстанавливает привязку к темам у слов личного словаря, у которых она утрачена
    public void repairPersonalDictionaryThemes(DataCallback<Void> callback) {
        personalDictionaryRepository.repairPersonalDictionaryThemes(
                currentUser,
                new PersonalDictionaryRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
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

    // Ищет тему слова локально по термину и переводу через wordDao
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
            Log.e("Repository", "Ошибка автопривязки темы: " + e.getMessage(), e);
            return null;
        }
    }

    // Строит PostgREST-фильтр "in.(...)" из списка ID
    private String buildIdsFilter(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "in.(-1)";
        }

        StringBuilder builder = new StringBuilder("in.(");

        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) builder.append(",");
            builder.append(ids.get(i));
        }

        builder.append(")");
        return builder.toString();
    }

    // Извлекает список ID из списка слов
    private List<Long> extractWordIds(List<Word> words) {
        List<Long> ids = new ArrayList<>();

        if (words == null) return ids;

        for (Word word : words) {
            if (word != null && word.getId() != null) {
                ids.add(word.getId());
            }
        }

        return ids;
    }

    public void isWordInPersonalDictionary(Word word, DataCallback<Boolean> callback) {
        personalDictionaryRepository.isWordInPersonalDictionary(
                word, currentUser,
                new PersonalDictionaryRepository.DataCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    // Подсчитывает количество полностью освоенных тем (все слова изучены)
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
                    public void onError(String error) { callback.onError(error); }
                });
            }

            @Override
            public void onError(String error) { callback.onError(error); }
        });
    }

    // Параллельно проверяет каждую тему на освоенность через AtomicInteger-счётчик
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

    // Возвращает true если все слова темы присутствуют в списке изученных
    private boolean isThemeMastered(List<Word> words, List<Long> learnedWordIds) {
        if (words == null || words.isEmpty()) return false;
        if (learnedWordIds == null || learnedWordIds.isEmpty()) return false;

        for (Word word : words) {
            if (word == null || word.getId() == null) return false;
            if (!learnedWordIds.contains(word.getId())) return false;
        }

        return true;
    }

    public void syncPersonalWords(DataCallback<Void> callback) {
        personalDictionaryRepository.syncPersonalWords(
                currentUser,
                new PersonalDictionaryRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void syncPersonalWords(Integer userId, DataCallback<Void> callback) {
        personalDictionaryRepository.syncPersonalWords(
                userId,
                new PersonalDictionaryRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void createStudySchedule(StudySchedule schedule, DataCallback<StudySchedule> callback) {
        scheduleRepository.createStudySchedule(schedule,
                new ScheduleRepository.DataCallback<StudySchedule>() {
                    @Override
                    public void onSuccess(StudySchedule data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void deleteStudySchedule(StudySchedule schedule, DataCallback<Void> callback) {
        scheduleRepository.deleteStudySchedule(schedule,
                new ScheduleRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getStudyScheduleForDate(int userId, String date,
                                        DataCallback<List<StudySchedule>> callback) {
        scheduleRepository.getStudyScheduleForDate(userId, date,
                new ScheduleRepository.DataCallback<List<StudySchedule>>() {
                    @Override
                    public void onSuccess(List<StudySchedule> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getStudyScheduleForRange(int userId, String startDate, String endDate,
                                         DataCallback<List<StudySchedule>> callback) {
        scheduleRepository.getStudyScheduleForRange(userId, startDate, endDate,
                new ScheduleRepository.DataCallback<List<StudySchedule>>() {
                    @Override
                    public void onSuccess(List<StudySchedule> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    private void loadLocalStudyScheduleForRange(int userId, String startDate, String endDate,
                                                DataCallback<List<StudySchedule>> callback) {
        scheduleRepository.loadLocalStudyScheduleForRange(userId, startDate, endDate,
                new ScheduleRepository.DataCallback<List<StudySchedule>>() {
                    @Override
                    public void onSuccess(List<StudySchedule> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void createTeam(String teamName, DataCallback<Integer> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        teamsRepository.createTeam(teamName, currentUser,
                new TeamsRepository.DataCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void addUserToTeam(int teamId, int userId, DataCallback<Void> callback) {
        teamsRepository.addUserToTeam(teamId, userId,
                new TeamsRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void createTeamWithFriends(String teamName,
                                      List<User> selectedFriends,
                                      String challengeType,
                                      int targetValue,
                                      DataCallback<Integer> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        teamsRepository.createTeamWithFriends(
                teamName, selectedFriends, challengeType, targetValue, currentUser,
                new TeamsRepository.DataCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getMyTeams(DataCallback<List<Team>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        teamsRepository.getMyTeams(currentUser,
                new TeamsRepository.DataCallback<List<Team>>() {
                    @Override
                    public void onSuccess(List<Team> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getTeamChallenge(int teamId, DataCallback<TeamChallenge> callback) {
        teamsRepository.getTeamChallenge(teamId,
                new TeamsRepository.DataCallback<TeamChallenge>() {
                    @Override
                    public void onSuccess(TeamChallenge data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getTeamProgress(int challengeId,
                                DataCallback<List<TeamChallengeProgress>> callback) {
        teamsRepository.getTeamProgress(challengeId,
                new TeamsRepository.DataCallback<List<TeamChallengeProgress>>() {
                    @Override
                    public void onSuccess(List<TeamChallengeProgress> data) {
                        callback.onSuccess(data);
                    }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void updateTeamChallengeProgressAfterLesson(int userId, int earnedXp) {
        teamsRepository.updateTeamChallengeProgressAfterLesson(userId, earnedXp);
    }

    public void addXp(int xpReward) {
        statsRepository.addXp(xpReward);
    }

    public void recordLessonCompletion(String lessonType,
                                       Long themeId,
                                       int earnedXp,
                                       int totalWords,
                                       int mistakesCount,
                                       int fixedErrorsCount,
                                       boolean isTimedMode) {
        statsRepository.recordLessonCompletion(
                lessonType, themeId, earnedXp, totalWords,
                mistakesCount, fixedErrorsCount, isTimedMode);
    }

    private void recordLoginEvent() {
        statsRepository.recordLoginEvent();
    }

    private void syncUserProgressToRemote(User user) {
        statsRepository.syncUserProgressToRemote(user);
    }

    private void syncUserStatsToRemote(UserStats stats) {
        statsRepository.syncUserStatsToRemote(stats);
    }

    private void syncLessonHistoryToRemote(LessonHistory history) {
        statsRepository.syncLessonHistoryToRemote(history);
    }

    public void getDailyQuests(DataCallback<List<DailyQuest>> callback) {
        statsRepository.getDailyQuests(
                new StatsRepository.DataCallback<List<DailyQuest>>() {
                    @Override
                    public void onSuccess(List<DailyQuest> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void updateQuestProgress(String type, int amount) {
        statsRepository.updateQuestProgress(type, amount);
    }

    public void getAchievements(DataCallback<List<AchievementWithProgress>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        statsRepository.getAchievements(currentUser,
                new StatsRepository.DataCallback<List<AchievementWithProgress>>() {
                    @Override
                    public void onSuccess(List<AchievementWithProgress> data) {
                        callback.onSuccess(data);
                    }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getUserOverallStatistics(DataCallback<UserOverallStats> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        statsRepository.getUserOverallStatistics(currentUser,
                new StatsRepository.DataCallback<UserOverallStats>() {
                    @Override
                    public void onSuccess(UserOverallStats data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getThemeProgressStatistics(DataCallback<List<ThemeProgressStats>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        statsRepository.getThemeProgressStatistics(currentUser,
                new StatsRepository.DataCallback<List<ThemeProgressStats>>() {
                    @Override
                    public void onSuccess(List<ThemeProgressStats> data) {
                        callback.onSuccess(data);
                    }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getRecentLessonHistory(int limit, DataCallback<List<LessonHistory>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        statsRepository.getRecentLessonHistory(limit, currentUser,
                new StatsRepository.DataCallback<List<LessonHistory>>() {
                    @Override
                    public void onSuccess(List<LessonHistory> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getLessonHistoryForStatistics(DataCallback<List<LessonHistory>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        statsRepository.getLessonHistoryForStatistics(currentUser,
                new StatsRepository.DataCallback<List<LessonHistory>>() {
                    @Override
                    public void onSuccess(List<LessonHistory> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void incrementWordProgress(Integer userId, Long wordId) {
        wordProgressRepository.incrementWordProgress(userId, wordId);
    }

    public void getLearnedWords(DataCallback<List<Word>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();
        wordProgressRepository.getLearnedWords(
                currentUser != null ? currentUser.getId() : null,
                new WordProgressRepository.DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getUnlearnedWordsByTheme(Long themeId, DataCallback<List<Word>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();
        wordProgressRepository.getUnlearnedWordsByTheme(
                themeId,
                currentUser != null ? currentUser.getId() : null,
                new WordProgressRepository.DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void markWordsAsLearned(List<Word> words, DataCallback<Void> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();
        wordProgressRepository.markWordsAsLearned(
                words,
                currentUser != null ? currentUser.getId() : null,
                new WordProgressRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void recordWordMistake(Word word) {
        if (currentUser == null) restoreCurrentUserFromPrefs();
        wordProgressRepository.recordWordMistake(
                word, currentUser != null ? currentUser.getId() : null);
    }

    // Обёртка для обратной совместимости: делегирует в incrementWordProgress и recordWordMistake
    private void upsertWordProgress(Long userId, Long wordId,
                                    boolean markLearned, boolean addMistake,
                                    DataCallback<Void> callback) {
        if (userId == null || wordId == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Некорректные параметры"));
            }
            return;
        }

        if (markLearned) {
            wordProgressRepository.incrementWordProgress(userId.intValue(), wordId);
        }
        if (addMistake) {
            Word tempWord = new Word();
            tempWord.setId(wordId);
            wordProgressRepository.recordWordMistake(tempWord, userId.intValue());
        }

        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(null));
        }
    }

    public void getLearnedWordsForTraining(DataCallback<List<Word>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();
        wordProgressRepository.getLearnedWordsForTraining(
                currentUser != null ? currentUser.getId() : null,
                new WordProgressRepository.DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getMistakeWordsForTraining(DataCallback<List<Word>> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();
        wordProgressRepository.getMistakeWordsForTraining(
                currentUser != null ? currentUser.getId() : null,
                new WordProgressRepository.DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getLearnedWordsCount(Integer userId, DataCallback<Integer> callback) {
        wordProgressRepository.getLearnedWordsCount(userId,
                new WordProgressRepository.DataCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void resolveWordMistake(Word word, DataCallback<Void> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();
        wordProgressRepository.resolveWordMistake(
                word,
                currentUser != null ? currentUser.getId() : null,
                new WordProgressRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void getWordsByTheme(Long themeId, DataCallback<List<Word>> callback) {
        wordRepository.getWordsByThemeCacheFirst(themeId,
                new WordRepository.DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }
    public boolean isCurrentUserAdmin() {
        return adminRepository.isCurrentUserAdmin();
    }

    public void adminCreateTheme(Theme theme, DataCallback<Theme> callback) {
        adminRepository.adminCreateTheme(theme,
                new AdminRepository.DataCallback<Theme>() {
                    @Override
                    public void onSuccess(Theme data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void adminUpdateTheme(Theme theme, DataCallback<Theme> callback) {
        adminRepository.adminUpdateTheme(theme,
                new AdminRepository.DataCallback<Theme>() {
                    @Override
                    public void onSuccess(Theme data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void adminDeleteTheme(Long themeId, DataCallback<Void> callback) {
        adminRepository.adminDeleteTheme(themeId,
                new AdminRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void adminCreateWord(Word word, DataCallback<Word> callback) {
        adminRepository.adminCreateWord(word,
                new AdminRepository.DataCallback<Word>() {
                    @Override
                    public void onSuccess(Word data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void adminUpdateWord(Word word, DataCallback<Word> callback) {
        adminRepository.adminUpdateWord(word,
                new AdminRepository.DataCallback<Word>() {
                    @Override
                    public void onSuccess(Word data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void adminDeleteWord(Long wordId, DataCallback<Void> callback) {
        adminRepository.adminDeleteWord(wordId,
                new AdminRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void adminCreateWordsBulk(Long themeId, List<Word> words,
                                     DataCallback<List<Word>> callback) {
        adminRepository.adminCreateWordsBulk(themeId, words,
                new AdminRepository.DataCallback<List<Word>>() {
                    @Override
                    public void onSuccess(List<Word> data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    // Выполняет вход: сбрасывает состояние, отправляет запрос, отпускает UI немедленно,
    // затем запускает фоновую синхронизацию данных нового аккаунта
    public void login(User user, DataCallback<User> callback) {
        if (user == null) {
            callback.onError("Введите данные для входа");
            return;
        }

        String cleanEmail = user.getEmail() == null
                ? "" : user.getEmail().trim().toLowerCase();
        String rawPassword = user.getPasswordHash() == null
                ? "" : user.getPasswordHash().trim();

        if (cleanEmail.isEmpty() || rawPassword.isEmpty()) {
            callback.onError("Введите email и пароль");
            return;
        }

        String hashedPassword = rawPassword.length() == 64
                ? rawPassword : hashPassword(rawPassword);

        // Сбрасываем кэши до входа — защита от утечки данных между аккаунтами
        resetAllRepositoryStates();

        final int loginSession = ++authSessionVersion;

        apiService.loginByEmail("eq." + cleanEmail)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        // Игнорируем ответ если сессия устарела (пользователь сменился)
                        if (loginSession != authSessionVersion) {
                            Log.w("RepositoryLogin", "Игнорируем устаревший ответ login");
                            return;
                        }

                        if (!response.isSuccessful()
                                || response.body() == null
                                || response.body().isEmpty()) {
                            mainHandler.post(() ->
                                    callback.onError("Пользователь с таким email не найден"));
                            return;
                        }

                        User serverUser = parseUserFromJson(response.body().get(0));

                        String serverPassword = serverUser.getPasswordHash() == null
                                ? "" : serverUser.getPasswordHash().trim();

                        boolean passwordMatches =
                                hashedPassword.equals(serverPassword)
                                        || rawPassword.equals(serverPassword);

                        if (!passwordMatches) {
                            Log.e("RepositoryLogin", "Пароль не совпал. serverPassword="
                                    + serverPassword);
                            mainHandler.post(() -> callback.onError("Неверный пароль"));
                            return;
                        }

                        if (serverUser.getId() == null) {
                            mainHandler.post(() -> callback.onError(
                                    "Некорректные данные пользователя"));
                            return;
                        }

                        currentUser = serverUser;
                        authRepository.setCurrentUser(serverUser);
                        saveCurrentUserToPrefs(serverUser);
                        cacheUserSafely(serverUser);

                        // Применяем аватар из ответа сервера
                        if (serverUser.getAvatarConfig() != null
                                && !serverUser.getAvatarConfig().trim().isEmpty()
                                && !"null".equals(serverUser.getAvatarConfig().trim())) {
                            try {
                                AvatarConfig serverAvatar =
                                        AvatarConfig.fromJson(serverUser.getAvatarConfig());
                                AvatarPrefs.save(appContext, serverAvatar);
                            } catch (Exception e) {
                                Log.e("RepositoryLogin", "Ошибка применения avatar_config: "
                                        + e.getMessage(), e);
                            }
                        }

                        Integer uid = serverUser.getId();

                        // Немедленно отпускаем UI; фоновая синхронизация запускается после
                        mainHandler.post(() -> callback.onSuccess(serverUser));

                        syncAllDataFromServerForUser(uid, loginSession);
                        recordLoginEvent();
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        if (loginSession != authSessionVersion) {
                            Log.w("RepositoryLogin", "Игнорируем устаревшую ошибку login");
                            return;
                        }

                        Log.e("RepositoryLogin", "Ошибка сети login: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
                    }
                });
    }

    // Запускает фоновую синхронизацию всех данных для конкретного пользователя и сессии.
    // Проверяет актуальность сессии перед каждым шагом.
    private void syncAllDataFromServerForUser(Integer uid, int sessionVersion) {
        if (uid == null || uid <= 0) {
            Log.d("RepositorySync", "syncAllDataFromServerForUser: некорректный uid");
            return;
        }

        if (sessionVersion != authSessionVersion) {
            Log.w("RepositorySync", "syncAllDataFromServerForUser: устаревшая сессия");
            return;
        }

        User userSnapshot = currentUser;

        if (userSnapshot == null
                || userSnapshot.getId() == null
                || !uid.equals(userSnapshot.getId())) {
            Log.w("RepositorySync",
                    "syncAllDataFromServerForUser: currentUser уже другой");
            return;
        }

        Log.d("RepositorySync", "Запуск синхронизации для userId=" + uid);

        // 1. Прогрев кэша изученных слов
        try {
            wordProgressRepository.warmupLearnedIdsCache(uid);
        } catch (Exception e) {
            Log.e("RepositorySync", "warmupLearnedIdsCache: " + e.getMessage(), e);
        }

        // 2. Личный словарь
        syncPersonalWords(uid, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                if (sessionVersion != authSessionVersion) return;
                Log.d("RepositorySync", "Личный словарь синхронизирован");
            }

            @Override
            public void onError(String error) {
                if (sessionVersion != authSessionVersion) return;
                Log.e("RepositorySync", "Личный словарь: " + error);
            }
        });

        // 3. Статистика
        statsRepository.syncStatsFromServer(uid, new StatsRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                if (sessionVersion != authSessionVersion) return;
                Log.d("RepositorySync", "user_stats синхронизированы");
            }

            @Override
            public void onError(String error) {
                if (sessionVersion != authSessionVersion) return;
                Log.e("RepositorySync", "user_stats: " + error);
            }
        });

        // 4. Достижения
        achievementRepository.syncAchievementsFromServer(uid,
                new AchievementRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        if (sessionVersion != authSessionVersion) return;
                        Log.d("RepositorySync", "Ачивки синхронизированы");
                    }

                    @Override
                    public void onError(String error) {
                        if (sessionVersion != authSessionVersion) return;
                        Log.e("RepositorySync", "Ачивки: " + error);
                    }
                });

        // 5. Друзья
        friendsRepository.getFriends(userSnapshot,
                new FriendsRepository.DataCallback<List<User>>() {
                    @Override
                    public void onSuccess(List<User> data) {
                        if (sessionVersion != authSessionVersion) return;
                        Log.d("RepositorySync",
                                "Друзья прогреты: " + (data != null ? data.size() : 0));
                    }

                    @Override
                    public void onError(String error) {
                        if (sessionVersion != authSessionVersion) return;
                        Log.e("RepositorySync", "Друзья: " + error);
                    }
                });

        // 6. Команды
        teamsRepository.getMyTeams(userSnapshot,
                new TeamsRepository.DataCallback<List<Team>>() {
                    @Override
                    public void onSuccess(List<Team> data) {
                        if (sessionVersion != authSessionVersion) return;
                        Log.d("RepositorySync",
                                "Команды прогреты: " + (data != null ? data.size() : 0));
                    }

                    @Override
                    public void onError(String error) {
                        if (sessionVersion != authSessionVersion) return;
                        Log.e("RepositorySync", "Команды: " + error);
                    }
                });
    }

    // Регистрирует пользователя: нормализует данные, проверяет уникальность email и создаёт аккаунт
    public void register(User user, DataCallback<User> callback) {
        if (user == null) {
            callback.onError("Введите данные для регистрации");
            return;
        }

        String rawPassword = user.getPasswordHash() == null
                ? "" : user.getPasswordHash().trim();
        String cleanEmail = user.getEmail() == null
                ? "" : user.getEmail().trim().toLowerCase();
        String cleanUsername = user.getUsername() == null
                ? "" : user.getUsername().trim();

        if (cleanUsername.isEmpty()) {
            callback.onError("Введите имя пользователя");
            return;
        }

        if (cleanEmail.isEmpty() || rawPassword.isEmpty()) {
            callback.onError("Введите email и пароль");
            return;
        }

        String hashedPassword = rawPassword.length() == 64
                ? rawPassword : hashPassword(rawPassword);

        user.setUsername(cleanUsername);
        user.setEmail(cleanEmail);
        user.setPasswordHash(hashedPassword);

        performActualRegistration(user, cleanEmail, callback);
    }

    // Отправляет запрос на создание аккаунта; при пустом ответе загружает пользователя по email
    private void performActualRegistration(User user, String cleanEmail,
                                           DataCallback<User> callback) {
        apiService.register(user).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (response.isSuccessful()) {
                    if (response.body() != null && !response.body().isEmpty()) {
                        User regUser = parseUserFromJson(response.body().get(0));
                        completeRegistrationSuccess(regUser, callback);
                        return;
                    }
                    loadRegisteredUserByEmail(cleanEmail, callback);
                    return;
                }

                String errorBody = getErrorBody(response);
                String safeError = errorBody == null ? "" : errorBody.toLowerCase();

                if (response.code() == 409
                        || safeError.contains("duplicate")
                        || safeError.contains("unique")
                        || safeError.contains("already exists")) {
                    mainHandler.post(() -> callback.onError("Этот аккаунт уже зарегистрирован"));
                    return;
                }

                Log.e("Repository", "Ошибка регистрации: "
                        + response.code() + " | " + errorBody);
                mainHandler.post(() -> callback.onError("Ошибка сервера: " + response.code()));
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository", "Сбой сети register: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Сбой сети: " + t.getMessage()));
            }
        });
    }

    // Загружает данные только что зарегистрированного пользователя по email
    private void loadRegisteredUserByEmail(String cleanEmail, DataCallback<User> callback) {
        apiService.loginByEmail("eq." + cleanEmail).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (!response.isSuccessful()
                        || response.body() == null
                        || response.body().isEmpty()) {
                    Log.e("Repository",
                            "Пользователь создан, но не удалось загрузить его: "
                                    + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError(
                            "Аккаунт создан, но не удалось завершить вход"));
                    return;
                }

                User regUser = parseUserFromJson(response.body().get(0));
                completeRegistrationSuccess(regUser, callback);
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("Repository",
                        "Ошибка загрузки пользователя после регистрации: "
                                + t.getMessage(), t);
                mainHandler.post(() -> callback.onError(
                        "Аккаунт создан, но не удалось завершить вход"));
            }
        });
    }

    // Завершает регистрацию: сбрасывает кэши, устанавливает нового пользователя, запускает синхронизацию
    private void completeRegistrationSuccess(User regUser, DataCallback<User> callback) {
        if (regUser == null || regUser.getId() == null) {
            mainHandler.post(() -> callback.onError("Не удалось завершить регистрацию"));
            return;
        }

        resetAllRepositoryStates();

        final int session = ++authSessionVersion;

        currentUser = regUser;
        authRepository.setCurrentUser(regUser);
        saveCurrentUserToPrefs(regUser);
        cacheUserSafely(regUser);

        if (regUser.getAvatarConfig() != null
                && !regUser.getAvatarConfig().trim().isEmpty()
                && !"null".equals(regUser.getAvatarConfig().trim())) {
            try {
                AvatarConfig serverAvatar = AvatarConfig.fromJson(regUser.getAvatarConfig());
                AvatarPrefs.save(appContext, serverAvatar);
            } catch (Exception e) {
                Log.e("Repository", "Ошибка применения avatar_config после регистрации: "
                        + e.getMessage(), e);
            }
        }

        mainHandler.post(() -> callback.onSuccess(regUser));

        syncAllDataFromServerForUser(regUser.getId(), session);
        recordLoginEvent();
    }

    public void updateUsername(String username, DataCallback<User> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        authRepository.updateUsername(username, currentUser,
                new AuthRepository.DataCallback<User>() {
                    @Override
                    public void onSuccess(User data) {
                        currentUser = data;
                        callback.onSuccess(data);
                    }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    public void updateAvatarConfig(AvatarConfig avatarConfig, DataCallback<Void> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        authRepository.updateAvatarConfig(avatarConfig, currentUser,
                new AuthRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }

    // Разбирает JSON-объект пользователя из ответа сервера в модель User
    private User parseUserFromJson(JsonObject userJson) {
        User user = new User();

        if (userJson == null) return user;

        if (userJson.has("id") && !userJson.get("id").isJsonNull())
            user.setId(userJson.get("id").getAsInt());
        if (userJson.has("username") && !userJson.get("username").isJsonNull())
            user.setUsername(userJson.get("username").getAsString());
        if (userJson.has("email") && !userJson.get("email").isJsonNull())
            user.setEmail(userJson.get("email").getAsString());
        if (userJson.has("password_hash") && !userJson.get("password_hash").isJsonNull())
            user.setPasswordHash(userJson.get("password_hash").getAsString());
        if (userJson.has("current_level") && !userJson.get("current_level").isJsonNull())
            user.setCurrentLevel(userJson.get("current_level").getAsInt());
        if (userJson.has("total_xp") && !userJson.get("total_xp").isJsonNull())
            user.setTotalXp(userJson.get("total_xp").getAsInt());
        if (userJson.has("role") && !userJson.get("role").isJsonNull())
            user.setRole(userJson.get("role").getAsString());
        if (userJson.has("created_at") && !userJson.get("created_at").isJsonNull())
            user.setCreatedAt(userJson.get("created_at").getAsString());
        if (userJson.has("avatar_config") && !userJson.get("avatar_config").isJsonNull())
            user.setAvatarConfig(userJson.get("avatar_config").toString());
        else
            user.setAvatarConfig(null);
        if (userJson.has("gender") && !userJson.get("gender").isJsonNull())
            user.setGender(userJson.get("gender").getAsString());

        return user;
    }

    // Полная фоновая синхронизация данных при запуске приложения для авторизованного пользователя
    public void syncAllDataFromServer() {
        if (currentUser == null || currentUser.getId() == null) {
            restoreCurrentUserFromPrefs();
        }

        if (currentUser == null || currentUser.getId() == null) {
            Log.d("RepositorySync",
                    "syncAllDataFromServer: пользователь не авторизован, пропускаем");
            return;
        }

        Integer uid = currentUser.getId();
        int session = authSessionVersion;

        syncAllDataFromServerForUser(uid, session);

        wordProgressRepository.warmupLearnedIdsCache(uid);
        Log.d("RepositorySync", "Запуск полной синхронизации для userId=" + uid);

        // Личный словарь
        syncPersonalWords(uid, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Log.d("RepositorySync", "Личный словарь синхронизирован");
            }
            @Override
            public void onError(String error) {
                Log.e("RepositorySync", "Личный словарь: " + error);
            }
        });

        // Статистика
        statsRepository.syncStatsFromServer(uid, new StatsRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Log.d("RepositorySync", "user_stats синхронизированы");
            }
            @Override
            public void onError(String error) {
                Log.e("RepositorySync", "user_stats: " + error);
            }
        });

        // Достижения
        achievementRepository.syncAchievementsFromServer(uid,
                new AchievementRepository.DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        Log.d("RepositorySync", "Ачивки синхронизированы");
                    }
                    @Override
                    public void onError(String error) {
                        Log.e("RepositorySync", "Ачивки: " + error);
                    }
                });

        // Друзья
        friendsRepository.getFriends(currentUser,
                new FriendsRepository.DataCallback<List<User>>() {
                    @Override
                    public void onSuccess(List<User> data) {
                        Log.d("RepositorySync",
                                "Друзья прогреты: " + (data != null ? data.size() : 0));
                    }
                    @Override
                    public void onError(String error) {
                        Log.e("RepositorySync", "Друзья: " + error);
                    }
                });

        // Команды
        teamsRepository.getMyTeams(currentUser,
                new TeamsRepository.DataCallback<List<Team>>() {
                    @Override
                    public void onSuccess(List<Team> data) {
                        Log.d("RepositorySync",
                                "Команды прогреты: " + (data != null ? data.size() : 0));
                    }
                    @Override
                    public void onError(String error) {
                        Log.e("RepositorySync", "Команды: " + error);
                    }
                });
    }

    // Методы для пометки слова как пройденного в конкретном режиме обучения
    public void markSprintPassed(Integer userId, Long wordId) {
        wordProgressRepository.markSprintPassed(userId, wordId);
    }

    public void markMatchingPassed(Integer userId, Long wordId) {
        wordProgressRepository.markMatchingPassed(userId, wordId);
    }

    public void markWritingPassed(Integer userId, Long wordId) {
        wordProgressRepository.markWritingPassed(userId, wordId);
    }

    public void warmupLearnedIdsCache(Integer userId) {
        wordProgressRepository.warmupLearnedIdsCache(userId);
    }

    public void clearLearnedIdsCache() {
        wordProgressRepository.clearLearnedIdsCache();
    }

    public void isAlreadyFriend(int friendId, DataCallback<Boolean> callback) {
        if (currentUser == null) restoreCurrentUserFromPrefs();

        friendsRepository.isAlreadyFriend(friendId, currentUser,
                new FriendsRepository.DataCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean data) { callback.onSuccess(data); }
                    @Override
                    public void onError(String error) { callback.onError(error); }
                });
    }


}