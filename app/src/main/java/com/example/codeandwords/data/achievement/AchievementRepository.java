package com.example.codeandwords.data.achievement;

import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.AchievementDao;
import com.example.codeandwords.db.UserDao;
import com.example.codeandwords.model.Achievement;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserAchievement;
import com.example.codeandwords.model.UserStats;
import com.google.gson.JsonObject;

import java.util.List;

import retrofit2.Response;

public class AchievementRepository {

    private final AchievementDao achievementDao;
    private final UserDao userDao;
    private final ApiService apiService;

    private AchievementListener listener;

    public AchievementRepository(AchievementDao achievementDao,
                                 UserDao userDao,
                                 ApiService apiService) {
        this.achievementDao = achievementDao;
        this.userDao = userDao;
        this.apiService = apiService;
    }

    // ===== ИНТЕРФЕЙС СЛУШАТЕЛЯ =====

    public interface AchievementListener {
        int safeInt(Integer value);
        String toSqlTimestamp(long millis);
        void saveCurrentUserToPrefs(User user);
        void syncUserProgressToRemote(User user);
        User getCurrentUser();
    }

    public void setListener(AchievementListener listener) {
        this.listener = listener;
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private int safeInt(Integer value) {
        return listener != null
                ? listener.safeInt(value)
                : (value != null ? value : 0);
    }

    private String toSqlTimestamp(long millis) {
        return listener != null ? listener.toSqlTimestamp(millis) : "";
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e("AchievementRepository",
                    "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    private int getAchievementProgress(Achievement achievement,
                                       User user,
                                       UserStats stats) {
        if (achievement.getConditionType() == null) return 0;

        switch (achievement.getConditionType()) {
            case "LOGIN_STREAK":   return stats.loginStreak;
            case "MAX_XP_DAY":    return stats.maxXpInDay;
            case "PERFECT_STREAK": return stats.perfectLessonsStreak;
            case "EARLY_BIRD":    return stats.lessonsBefore9;
            case "ERROR_FIXER":   return stats.fixedErrorsTotal;
            case "TASK_MASTER":   return stats.completedTasksTotal;
            case "NIGHT_OWL":     return stats.lessonsAfter22;
            case "TOTAL_XP":      return safeInt(user.getTotalXp());
            case "PERFECT_TOTAL": return stats.perfectLessonsTotal;
            case "SPRINT_XP":     return stats.sprintXpTotal;
            default:              return 0;
        }
    }

    private JsonObject buildUpsertPayload(UserAchievement ua) {
        JsonObject payload = new JsonObject();
        payload.addProperty("user_id", ua.userId);
        payload.addProperty("achievement_id", ua.achievementId);
        payload.addProperty("current_progress", safeInt(ua.currentProgress));
        payload.addProperty("is_unlocked", ua.isUnlocked);
        payload.addProperty("is_new", ua.isNew);

        long ts = ua.dateReceived > 0
                ? ua.dateReceived
                : System.currentTimeMillis();

        payload.addProperty("received_at", toSqlTimestamp(ts));
        payload.addProperty("unlocked_at", toSqlTimestamp(ts));
        payload.addProperty("last_updated_at",
                toSqlTimestamp(System.currentTimeMillis()));

        return payload;
    }

    /**
     * Единственный сетевой вызов — синхронный upsert.
     * Выполняется уже внутри executor, поэтому .execute() безопасен.
     */
    private void upsertAchievementRemote(UserAchievement ua) {
        try {
            JsonObject payload = buildUpsertPayload(ua);

            Response<Void> response = apiService
                    .upsertUserAchievementRaw("user_id,achievement_id", payload)
                    .execute();

            if (response.isSuccessful()) {
                Log.d("AchievementRepository",
                        "Upsert OK: achievementId=" + ua.achievementId
                                + " isUnlocked=" + ua.isUnlocked
                                + " progress=" + ua.currentProgress);
            } else {
                Log.e("AchievementRepository",
                        "Upsert FAILED: " + response.code()
                                + " | " + getErrorBody(response));
            }
        } catch (Exception e) {
            Log.e("AchievementRepository",
                    "Ошибка upsert achievement: " + e.getMessage(), e);
        }
    }

    /**
     * Синхронная проверка на сервере — уже разблокировано или нет.
     */
    private boolean isAlreadyUnlockedRemote(long userId, long achievementId) {
        try {
            Response<List<JsonObject>> response = apiService
                    .getUserAchievementRecordRaw(
                            "eq." + userId,
                            "eq." + achievementId)
                    .execute();

            if (!response.isSuccessful()
                    || response.body() == null
                    || response.body().isEmpty()) {
                return false;
            }

            JsonObject item = response.body().get(0);
            return item.has("is_unlocked")
                    && !item.get("is_unlocked").isJsonNull()
                    && item.get("is_unlocked").getAsBoolean();

        } catch (Exception e) {
            Log.e("AchievementRepository",
                    "Ошибка проверки на сервере: " + e.getMessage(), e);
            return false;
        }
    }

    private void grantAchievementXp(int xpReward) {
        if (listener == null) return;

        User currentUser = listener.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) return;

        int newTotalXp = safeInt(currentUser.getTotalXp()) + xpReward;
        int newLevel   = (newTotalXp / 100) + 1;

        userDao.updateProgress(currentUser.getId(), newTotalXp, newLevel);
        currentUser.setTotalXp(newTotalXp);
        currentUser.setCurrentLevel(newLevel);

        listener.saveCurrentUserToPrefs(currentUser);
        listener.syncUserProgressToRemote(currentUser);
    }

    // ===== ОСНОВНОЙ МЕТОД =====

    public void refreshAchievementsSync(User user, UserStats stats) {
        try {
            if (user == null || user.getId() == null || stats == null) return;

            List<Achievement> all = achievementDao.getAllAchievements();
            int rewardXp = 0;
            long now = System.currentTimeMillis();

            for (Achievement achievement : all) {
                if (achievement == null || achievement.getId() == null) continue;

                // 1. Считаем прогресс
                int progress = getAchievementProgress(achievement, user, stats);
                int maxProgress = safeInt(achievement.getMaxProgress());
                int normalized = Math.min(
                        progress, maxProgress > 0 ? maxProgress : progress);

                int conditionValue = safeInt(achievement.getConditionValue());
                boolean shouldUnlock = conditionValue > 0
                        && progress >= conditionValue;

                // 2. Читаем локальную запись
                UserAchievement ua = achievementDao.getUserAchievement(
                        user.getId(),
                        achievement.getId().intValue()
                );

                boolean wasUnlockedLocal = ua != null && ua.isUnlocked;

                // 3. Если нужно разблокировать — проверяем сервер
                boolean alreadyRemote = false;
                if (shouldUnlock && !wasUnlockedLocal) {
                    alreadyRemote = isAlreadyUnlockedRemote(
                            user.getId(), achievement.getId());
                }

                // 4. Обновляем или создаём локальную запись
                if (ua == null) {
                    ua = new UserAchievement(
                            user.getId().longValue(),
                            achievement.getId(),
                            shouldUnlock ? now : 0,
                            normalized,
                            shouldUnlock,
                            shouldUnlock && !alreadyRemote
                    );
                    achievementDao.insertUserAchievement(ua);

                    if (shouldUnlock && !alreadyRemote) {
                        rewardXp += safeInt(achievement.getXpReward());
                    }
                } else {
                    ua.currentProgress = normalized;

                    if (!wasUnlockedLocal && shouldUnlock) {
                        ua.isUnlocked = true;
                        ua.isNew = !alreadyRemote;
                        ua.dateReceived = now;

                        if (!alreadyRemote) {
                            rewardXp += safeInt(achievement.getXpReward());
                        }
                    }

                    achievementDao.updateUserAchievement(ua);
                }

                // 5. Один upsert на сервер
                upsertAchievementRemote(ua);
            }

            if (rewardXp > 0) {
                grantAchievementXp(rewardXp);
            }

        } catch (Exception e) {
            Log.e("AchievementRepository",
                    "Ошибка refreshAchievementsSync: " + e.getMessage(), e);
        }
    }
}