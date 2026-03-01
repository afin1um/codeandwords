package com.example.codeandwords.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.api.RetrofitClient;
import com.example.codeandwords.db.AchievementDao;
import com.example.codeandwords.db.AppDatabase;
import com.example.codeandwords.db.DailyQuestDao;
import com.example.codeandwords.db.ThemeDao;
import com.example.codeandwords.db.UserDao;
import com.example.codeandwords.db.WordDao;
import com.example.codeandwords.model.Achievement;
import com.example.codeandwords.model.AchievementWithProgress;
import com.example.codeandwords.model.DailyQuest;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserAchievement;
import com.example.codeandwords.model.UserWordProgress;
import com.example.codeandwords.model.Word;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Repository {

    private final UserDao userDao;
    private final ThemeDao themeDao;
    private final WordDao wordDao;
    private final AchievementDao achievementDao;
    private final DailyQuestDao dailyQuestDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private static User currentUser;

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    public Repository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.userDao = db.userDao();
        this.themeDao = db.themeDao();
        this.wordDao = db.wordDao();
        this.achievementDao = db.achievementDao();
        this.dailyQuestDao = db.dailyQuestDao();
        this.apiService = RetrofitClient.getApiService();
        this.executor = AppDatabase.databaseWriteExecutor;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // Вспомогательный метод хеширования для совместимости с Supabase
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }

    // --- ОСТАЛЬНЫЕ МЕТОДЫ (БЕЗ ИЗМЕНЕНИЙ) ---
    public void getThemes(DataCallback<List<Theme>> callback) {
        executor.execute(() -> {
            List<Theme> themes = themeDao.getAllThemes();
            mainHandler.post(() -> {
                if (!themes.isEmpty()) callback.onSuccess(themes);
                else callback.onError("Темы не найдены");
            });
        });
    }

    public void getThemeById(long themeId, DataCallback<Theme> callback) {
        executor.execute(() -> {
            Theme theme = themeDao.getThemeById(themeId);
            mainHandler.post(() -> {
                if (theme != null) callback.onSuccess(theme);
                else callback.onError("Тема не найдена");
            });
        });
    }

    public void getWordsByTheme(Long themeId, DataCallback<List<Word>> callback) {
        executor.execute(() -> {
            List<Word> words = wordDao.getWordsByTheme(themeId);
            mainHandler.post(() -> {
                if (!words.isEmpty()) callback.onSuccess(words);
                else callback.onError("Слова не найдены");
            });
        });
    }

    public void getCurrentUser(DataCallback<User> callback) {
        if (currentUser != null) callback.onSuccess(currentUser);
        else callback.onError("Ошибка авторизации");
    }

    public void getCurrentUserId(OnUserIdRetrieved callback) {
        if (currentUser != null) {
            callback.onRetrieved(currentUser.getId());
        } else {
            callback.onRetrieved(-1);
        }
    }

    public interface OnUserIdRetrieved {
        void onRetrieved(Integer userId);
    }

    public void logout(Runnable callback) {
        currentUser = null;
        mainHandler.post(callback);
    }

    public void addXp(int xpReward) {
        if (currentUser == null) return;
        updateQuestProgress("XP", xpReward);
        updateQuestProgress("GAME_PLAYED", 1);
        updateAchievementProgress("XP", xpReward);

        executor.execute(() -> {
            int oldTotalXp = currentUser.getTotalXp();
            int newTotalXp = oldTotalXp + xpReward;
            int newLevel = (newTotalXp / 100) + 1;
            userDao.updateProgress(currentUser.getId(), newTotalXp, newLevel);
            currentUser.setTotalXp(newTotalXp);
            currentUser.setCurrentLevel(newLevel);
            checkAchievements(currentUser);
        });
    }

    public void getDailyQuests(DataCallback<List<DailyQuest>> callback) {
        executor.execute(() -> {
            List<DailyQuest> quests = dailyQuestDao.getAllQuests();
            long today = System.currentTimeMillis();
            if (quests.isEmpty() || !isSameDay(quests.get(0).getDateCreated(), today)) {
                dailyQuestDao.deleteAll();
                quests = generateNewQuests();
                dailyQuestDao.insertAll(quests);
            }
            List<DailyQuest> finalQuests = quests;
            mainHandler.post(() -> callback.onSuccess(finalQuests));
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
        return cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
    }

    public void updateQuestProgress(String type, int amount) {
        executor.execute(() -> {
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
        });
    }

    public void getAchievements(DataCallback<List<AchievementWithProgress>> callback) {
        if (currentUser == null) return;
        executor.execute(() -> {
            List<AchievementWithProgress> list = achievementDao.getAchievementsWithProgress(currentUser.getId());
            mainHandler.post(() -> callback.onSuccess(list));
        });
    }

    private void checkAchievements(User user) {
        executor.execute(() -> {
            List<Achievement> allAchievements = achievementDao.getAllAchievements();
            for (Achievement a : allAchievements) {
                if (achievementDao.hasAchievement(user.getId(), a.getId().intValue()) > 0) continue;
                boolean unlocked = false;
                if (a.getConditionType().equals("XP") && user.getTotalXp() >= a.getConditionValue()) unlocked = true;
                else if (a.getConditionType().equals("LEVEL") && user.getCurrentLevel() >= a.getConditionValue()) unlocked = true;
                if (unlocked) {
                    achievementDao.insertUserAchievement(new UserAchievement(
                            user.getId().longValue(),
                            a.getId(),
                            System.currentTimeMillis(),
                            a.getMaxProgress()
                    ));
                }
            }
        });
    }

    public void updateAchievementProgress(String type, int amount) {
        if (currentUser == null) return;
        executor.execute(() -> {
            List<Achievement> all = achievementDao.getAllAchievements();
            for (Achievement a : all) {
                if (a.getConditionType().equals(type)) {
                    UserAchievement ua = achievementDao.getUserAchievement(currentUser.getId(), a.getId().intValue());
                    int current = (ua == null) ? 0 : ua.currentProgress;
                    int newVal = Math.min(current + amount, a.getMaxProgress());
                    if (ua == null) {
                        ua = new UserAchievement(
                                currentUser.getId().longValue(),
                                a.getId(),
                                System.currentTimeMillis(),
                                newVal
                        );
                        achievementDao.insertUserAchievement(ua);
                    } else {
                        ua.currentProgress = newVal;
                        achievementDao.updateUserAchievement(ua);
                    }
                    if (newVal >= a.getConditionValue() && current < a.getConditionValue()) addXp(a.getXpReward());
                }
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
                        UserWordProgress newProgress = new UserWordProgress();
                        newProgress.setUserId(userId.longValue());
                        newProgress.setWordId(wordId);
                        newProgress.setCorrectAnswersCount(1);
                        newProgress.setIsLearned(false);

                        apiService.createWordProgress(newProgress).enqueue(new Callback<List<UserWordProgress>>() {
                            @Override
                            public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> r) {
                                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                                    Log.d("Repository", "Word progress created");
                                }
                            }
                            @Override
                            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                                Log.e("Repository", "Error creating word progress: " + t.getMessage());
                            }
                        });
                    } else {
                        UserWordProgress existing = response.body().get(0);
                        int newCount = existing.getCorrectAnswersCount() + 1;
                        existing.setCorrectAnswersCount(newCount);
                        if (newCount >= 3) existing.setIsLearned(true);
                        apiService.updateWordProgress("eq." + existing.getId(), existing).enqueue(new Callback<Void>() {
                            @Override public void onResponse(Call<Void> call, Response<Void> r) {}
                            @Override public void onFailure(Call<Void> call, Throwable t) {}
                        });
                    }
                }
            }
            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                Log.e("Repository", "Network error: " + t.getMessage());
            }
        });
    }

    public void getLearnedWordsCount(Integer userId, DataCallback<Integer> callback) {
        String filter = "user_id=eq." + userId + "&is_learned=eq.true";
        apiService.getUserProgress(filter).enqueue(new Callback<List<UserWordProgress>>() {
            @Override
            public void onResponse(Call<List<UserWordProgress>> call, Response<List<UserWordProgress>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().size());
                } else {
                    callback.onSuccess(0);
                }
            }
            @Override
            public void onFailure(Call<List<UserWordProgress>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getLeaderboard(DataCallback<List<User>> callback) {
        executor.execute(() -> {
            List<User> users = userDao.getLeaderboard();
            mainHandler.post(() -> {
                if (users != null) {
                    callback.onSuccess(users);
                } else {
                    callback.onError("Список пуст");
                }
            });
        });
    }

    // В самом низу класса Repository.java добавь этот метод (и больше нигде его не дублируй!)
    // 1. Метод хеширования (добавь в конец класса Repository)
    private String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return password;
        }
    }

    public void register(User user, DataCallback<User> callback) {
        String rawPassword = user.getPasswordHash().trim();
        String cleanEmail = user.getEmail().trim().toLowerCase();

        // Хешируем только если это сырой пароль (длина не 64)
        String hashedPassword = (rawPassword.length() == 64) ? rawPassword : hashPassword(rawPassword);

        user.setEmail(cleanEmail);
        user.setPasswordHash(hashedPassword);

        Log.d("AUTH_DEBUG", "Регистрация. Email: " + cleanEmail + " Hash: " + hashedPassword);

        apiService.register(user).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    User regUser = response.body().get(0);
                    currentUser = regUser;
                    executor.execute(() -> userDao.insertUser(regUser));
                    mainHandler.post(() -> callback.onSuccess(regUser));
                } else {
                    mainHandler.post(() -> callback.onError("Ошибка: " + response.code()));
                }
            }
            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                mainHandler.post(() -> callback.onError(t.getMessage()));
            }
        });
    }

    public void login(User user, DataCallback<User> callback) {
        String cleanEmail = user.getEmail().trim().toLowerCase();
        String rawPassword = user.getPasswordHash().trim();

        // Хешируем пароль один раз перед отправкой
        String hashedPassword = (rawPassword.length() == 64) ? rawPassword : hashPassword(rawPassword);

        Log.d("AUTH_DEBUG", "Логин. Email: " + cleanEmail + " Hash: " + hashedPassword);

        apiService.login("eq." + cleanEmail, "eq." + hashedPassword).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    currentUser = response.body().get(0);
                    executor.execute(() -> userDao.insertUser(currentUser));
                    mainHandler.post(() -> callback.onSuccess(currentUser));
                } else {
                    mainHandler.post(() -> callback.onError("Неверный логин или пароль"));
                }
            }
            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Ошибка сети"));
            }
        });
    }
}