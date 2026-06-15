package com.example.codeandwords.data.achievement;

import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.AchievementDao;
import com.example.codeandwords.db.AppDatabase;
import com.example.codeandwords.db.UserDao;
import com.example.codeandwords.model.Achievement;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserAchievement;
import com.example.codeandwords.model.UserStats;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Репозиторий для управления достижениями: синхронизация с сервером, проверка условий, выдача XP
public class AchievementRepository {

    private static final String TAG = "AchievementRepository";

    private final AppDatabase database;
    private final AchievementDao achievementDao;
    private final UserDao userDao;
    private final ApiService apiService;

    // Однопоточный executor для последовательной работы с локальной БД
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private AchievementListener listener;

    // Кэш ID достижений, уже существующих на сервере — для разделения INSERT и PATCH
    private final Set<Long> serverExistingAchievementIds = new HashSet<>();
    private volatile boolean serverCacheLoaded = false;

    public AchievementRepository(AppDatabase database,
                                 AchievementDao achievementDao,
                                 UserDao userDao,
                                 ApiService apiService) {
        this.database = database;
        this.achievementDao = achievementDao;
        this.userDao = userDao;
        this.apiService = apiService;
    }

    // Интерфейс для делегирования вспомогательных операций во ViewModel или Activity
    public interface AchievementListener {
        int safeInt(Integer value);
        String toSqlTimestamp(long millis);
        void saveCurrentUserToPrefs(User user);
        void syncUserProgressToRemote(User user);
        User getCurrentUser();
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    public void setListener(AchievementListener listener) {
        this.listener = listener;
    }

    private int safeInt(Integer value) {
        return listener != null ? listener.safeInt(value) : (value != null ? value : 0);
    }

    private String toSqlTimestamp(long millis) {
        return listener != null ? listener.toSqlTimestamp(millis) : "";
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) return response.errorBody().string();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // Загружает достижения пользователя с сервера и сохраняет в локальную БД.
    // Заполняет кэш serverExistingAchievementIds для последующих операций.
    public void syncAchievementsFromServer(Integer userId, DataCallback<Void> callback) {
        if (userId == null || userId <= 0) {
            if (callback != null) callback.onError("Некорректный userId");
            return;
        }

        apiService.getUserAchievementsByUserRaw(
                "eq." + userId,
                "achievement_id,current_progress,is_unlocked,is_new,received_at"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "syncAchievements: пустой ответ " + response.code());
                    if (callback != null) callback.onSuccess(null);
                    return;
                }

                List<JsonObject> items = response.body();

                // Вся работа с БД выполняется в фоновом потоке
                dbExecutor.execute(() -> {
                    try {
                        synchronized (serverExistingAchievementIds) {
                            serverExistingAchievementIds.clear();
                            for (JsonObject item : items) {
                                if (item != null && item.has("achievement_id")
                                        && !item.get("achievement_id").isJsonNull()) {
                                    serverExistingAchievementIds.add(
                                            item.get("achievement_id").getAsLong());
                                }
                            }
                            serverCacheLoaded = true;
                        }

                        database.runInTransaction(() -> {
                            for (JsonObject item : items) {
                                if (item == null) continue;
                                if (!item.has("achievement_id")
                                        || item.get("achievement_id").isJsonNull()) continue;

                                long achId = item.get("achievement_id").getAsLong();
                                int progress = getInt(item, "current_progress");
                                boolean unlocked = getBool(item, "is_unlocked");
                                boolean isNew = getBool(item, "is_new");

                                UserAchievement existing = achievementDao.getUserAchievement(
                                        userId, (int) achId);

                                if (existing == null) {
                                    UserAchievement ua = new UserAchievement(
                                            userId.longValue(), achId,
                                            unlocked ? System.currentTimeMillis() : 0,
                                            progress, unlocked, isNew
                                    );
                                    achievementDao.insertUserAchievement(ua);
                                } else {
                                    // Прогресс не откатывается: берём максимальное значение
                                    existing.currentProgress = Math.max(
                                            existing.currentProgress, progress);
                                    if (unlocked) existing.isUnlocked = true;
                                    achievementDao.updateUserAchievement(existing);
                                }
                            }
                        });

                        Log.d(TAG, "syncAchievements: загружено " + items.size() + " ачивок");
                        if (callback != null) callback.onSuccess(null);

                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка сохранения ачивок: " + e.getMessage(), e);
                        if (callback != null) callback.onError(e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e(TAG, "Сеть syncAchievements: " + t.getMessage(), t);
                if (callback != null) callback.onError(t.getMessage());
            }
        });
    }

    private int getInt(JsonObject json, String key) {
        if (json == null || !json.has(key)) return 0;
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return 0;
        try { return e.getAsInt(); } catch (Exception ex) { return 0; }
    }

    private boolean getBool(JsonObject json, String key) {
        if (json == null || !json.has(key)) return false;
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return false;
        try { return e.getAsBoolean(); } catch (Exception ex) { return false; }
    }

    // Определяет текущий прогресс пользователя для конкретного типа достижения
    private int getAchievementProgress(Achievement achievement, User user, UserStats stats) {
        if (achievement.getConditionType() == null) return 0;

        switch (achievement.getConditionType()) {
            case "LOGIN_STREAK":   return stats.loginStreak;
            case "MAX_XP_DAY":     return stats.maxXpInDay;
            case "PERFECT_STREAK": return stats.perfectLessonsStreak;
            case "EARLY_BIRD":     return stats.lessonsBefore9;
            case "ERROR_FIXER":    return stats.fixedErrorsTotal;
            case "TASK_MASTER":    return stats.completedTasksTotal;
            case "NIGHT_OWL":      return stats.lessonsAfter22;
            case "TOTAL_XP":       return safeInt(user.getTotalXp());
            case "PERFECT_TOTAL":  return stats.perfectLessonsTotal;
            case "SPRINT_XP":      return stats.sprintXpTotal;
            default:               return 0;
        }
    }

    // Формирует JSON-объект достижения для отправки на сервер
    private JsonObject buildAchievementPayload(UserAchievement ua, boolean includeUserId) {
        JsonObject payload = new JsonObject();
        if (includeUserId) {
            payload.addProperty("user_id", ua.userId);
            payload.addProperty("achievement_id", ua.achievementId);
        }
        payload.addProperty("current_progress", safeInt(ua.currentProgress));
        payload.addProperty("is_unlocked", ua.isUnlocked);
        payload.addProperty("is_new", ua.isNew);

        long ts = ua.dateReceived > 0 ? ua.dateReceived : System.currentTimeMillis();
        payload.addProperty("received_at", toSqlTimestamp(ts));
        payload.addProperty("unlocked_at", toSqlTimestamp(ts));
        payload.addProperty("last_updated_at", toSqlTimestamp(System.currentTimeMillis()));

        return payload;
    }

    // Отправляет изменённые достижения на сервер:
    // существующие обновляются через PATCH, новые вставляются пакетом через POST
    private void sendChangedAchievementsToServer(List<UserAchievement> changedAchievements) {
        if (changedAchievements == null || changedAchievements.isEmpty()) return;

        List<UserAchievement> toInsert = new ArrayList<>();
        List<UserAchievement> toUpdate = new ArrayList<>();

        synchronized (serverExistingAchievementIds) {
            for (UserAchievement ua : changedAchievements) {
                if (serverExistingAchievementIds.contains(ua.achievementId)) {
                    toUpdate.add(ua);
                } else {
                    toInsert.add(ua);
                }
            }
        }

        for (UserAchievement ua : toUpdate) {
            JsonObject payload = buildAchievementPayload(ua, false);
            apiService.updateUserAchievementRaw(
                    "eq." + ua.userId, "eq." + ua.achievementId, payload
            ).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "PATCH ачивки FAILED: " + response.code()
                                + " | achId=" + ua.achievementId);
                    }
                }
                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Log.e(TAG, "Сеть PATCH ачивки: " + t.getMessage());
                }
            });
        }

        if (!toInsert.isEmpty()) {
            JsonArray batch = new JsonArray();
            for (UserAchievement ua : toInsert) {
                batch.add(buildAchievementPayload(ua, true));
            }

            apiService.upsertUserAchievementsBatchRaw("user_id,achievement_id", batch)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                synchronized (serverExistingAchievementIds) {
                                    for (UserAchievement ua : toInsert) {
                                        serverExistingAchievementIds.add(ua.achievementId);
                                    }
                                }
                                Log.d(TAG, "INSERT batch ачивок OK: " + toInsert.size());
                            } else {
                                Log.e(TAG, "INSERT batch ачивок FAILED: "
                                        + response.code() + " | " + getErrorBody(response));
                            }
                        }
                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Log.e(TAG, "Сеть INSERT batch: " + t.getMessage());
                        }
                    });
        }
    }

    // Начисляет XP за полученные достижения и синхронизирует прогресс с сервером
    private void grantAchievementXp(int xpReward) {
        if (listener == null) return;

        User currentUser = listener.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) return;

        int newTotalXp = safeInt(currentUser.getTotalXp()) + xpReward;
        int newLevel = (newTotalXp / 100) + 1;

        userDao.updateProgress(currentUser.getId(), newTotalXp, newLevel);
        currentUser.setTotalXp(newTotalXp);
        currentUser.setCurrentLevel(newLevel);

        listener.saveCurrentUserToPrefs(currentUser);
        listener.syncUserProgressToRemote(currentUser);
    }

    // Проверяет и обновляет все достижения пользователя после завершения урока.
    // Прогресс серий никогда не откатывается — сохраняется максимальное достигнутое значение.
    public void refreshAchievementsSync(User user, UserStats stats) {
        dbExecutor.execute(() -> {
            try {
                if (user == null || user.getId() == null || stats == null) return;

                List<Achievement> all = achievementDao.getAllAchievements();
                if (all == null || all.isEmpty()) return;

                List<UserAchievement> changedAchievements = new ArrayList<>();
                int[] totalRewardXp = {0};
                long now = System.currentTimeMillis();

                database.runInTransaction(() -> {
                    for (Achievement achievement : all) {
                        if (achievement == null || achievement.getId() == null) continue;

                        int progress = getAchievementProgress(achievement, user, stats);
                        int maxProgress = safeInt(achievement.getMaxProgress());
                        int normalized = Math.min(progress, maxProgress > 0 ? maxProgress : progress);

                        int conditionValue = safeInt(achievement.getConditionValue());
                        boolean shouldUnlock = conditionValue > 0 && progress >= conditionValue;

                        UserAchievement ua = achievementDao.getUserAchievement(
                                user.getId(), achievement.getId().intValue());

                        boolean wasUnlockedLocal = ua != null && ua.isUnlocked;
                        boolean hasChanged = false;

                        if (ua == null) {
                            ua = new UserAchievement(
                                    user.getId().longValue(), achievement.getId(),
                                    shouldUnlock ? now : 0, normalized, shouldUnlock, shouldUnlock
                            );
                            achievementDao.insertUserAchievement(ua);
                            hasChanged = true;

                            if (shouldUnlock) {
                                totalRewardXp[0] += safeInt(achievement.getXpReward());
                            }
                        } else {
                            int oldProgress = ua.currentProgress;

                            // Прогресс не откатывается: фиксируем максимум
                            int bestProgress = Math.max(oldProgress, normalized);
                            ua.currentProgress = bestProgress;

                            if (!wasUnlockedLocal && shouldUnlock) {
                                ua.isUnlocked = true;
                                ua.isNew = true;
                                ua.dateReceived = now;
                                totalRewardXp[0] += safeInt(achievement.getXpReward());
                                hasChanged = true;
                            } else if (oldProgress != bestProgress) {
                                hasChanged = true;
                            }

                            achievementDao.updateUserAchievement(ua);
                        }

                        if (hasChanged) {
                            changedAchievements.add(ua);
                        }
                    }

                    if (totalRewardXp[0] > 0) {
                        grantAchievementXp(totalRewardXp[0]);
                    }
                });

                if (!changedAchievements.isEmpty() && serverCacheLoaded) {
                    sendChangedAchievementsToServer(changedAchievements);
                } else if (!changedAchievements.isEmpty()) {
                    Log.d(TAG, "Серверный кэш ещё не загружен — пропускаем отправку");
                }

                Log.d(TAG, "refreshAchievements: изменено "
                        + changedAchievements.size() + " ачивок, награда "
                        + totalRewardXp[0] + " XP");

            } catch (Exception e) {
                Log.e(TAG, "Ошибка refreshAchievementsSync: " + e.getMessage(), e);
            }
        });
    }

    // Сбрасывает локальный кэш при выходе из аккаунта
    public void resetState() {
        synchronized (serverExistingAchievementIds) {
            serverExistingAchievementIds.clear();
            serverCacheLoaded = false;
        }
        Log.d(TAG, "resetState: кэши AchievementRepository сброшены");
    }
}