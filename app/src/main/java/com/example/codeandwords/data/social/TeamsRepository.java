package com.example.codeandwords.data.social;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.TeamChallengeDao;
import com.example.codeandwords.db.TeamChallengeProgressDao;
import com.example.codeandwords.db.TeamDao;
import com.example.codeandwords.db.TeamMemberDao;
import com.example.codeandwords.model.Team;
import com.example.codeandwords.model.TeamChallenge;
import com.example.codeandwords.model.TeamChallengeProgress;
import com.example.codeandwords.model.TeamMember;
import com.example.codeandwords.model.User;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Репозиторий командной функциональности: создание команд, управление челленджами и прогрессом
public class TeamsRepository {

    private static final String TAG = "TeamsRepository";

    // Поля выборки для запросов челленджей и прогресса
    private static final String CHALLENGE_SELECT =
            "id,team_id,title,condition_type,target_value," +
                    "xp_first,xp_second,xp_other,is_completed,created_at";

    private static final String PROGRESS_SELECT =
            "id,challenge_id,team_id,user_id,progress,is_completed," +
                    "completed_at,place,awarded_xp," +
                    "user:users!team_challenge_progress_user_id_fkey" +
                    "(id,username,email,current_level,total_xp,avatar_config)";

    private final TeamDao teamDao;
    private final TeamMemberDao teamMemberDao;
    private final TeamChallengeDao teamChallengeDao;
    private final TeamChallengeProgressDao teamChallengeProgressDao;
    private final ApiService apiService;
    private final ApiService fastApiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private TeamRewardListener rewardListener;

    // Флаг защиты от параллельных запросов челленджа
    private volatile boolean isFetchingChallenge = false;

    public TeamsRepository(TeamDao teamDao,
                           TeamMemberDao teamMemberDao,
                           TeamChallengeDao teamChallengeDao,
                           TeamChallengeProgressDao teamChallengeProgressDao,
                           ApiService apiService,
                           ApiService fastApiService,
                           ExecutorService executor,
                           Handler mainHandler) {
        this.teamDao = teamDao;
        this.teamMemberDao = teamMemberDao;
        this.teamChallengeDao = teamChallengeDao;
        this.teamChallengeProgressDao = teamChallengeProgressDao;
        this.apiService = apiService;
        this.fastApiService = fastApiService;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    // Интерфейс для делегирования выдачи XP и отметки победителя во ViewModel
    public interface TeamRewardListener {
        void onGrantXp(int xpReward);
        void onMarkWinner(int challengeId);
        String toSqlTimestamp(long millis);
    }

    public void setRewardListener(TeamRewardListener listener) {
        this.rewardListener = listener;
    }

    // Разбирает JSON-объект команды в модель Team
    private Team parseTeamFromJson(JsonObject item) {
        if (item == null) return null;

        Team team = new Team();
        if (item.has("id") && !item.get("id").isJsonNull())
            team.id = item.get("id").getAsInt();
        if (item.has("team_name") && !item.get("team_name").isJsonNull())
            team.teamName = item.get("team_name").getAsString();
        if (item.has("owner_id") && !item.get("owner_id").isJsonNull())
            team.ownerId = item.get("owner_id").getAsInt();

        return team;
    }

    // Разбирает JSON-объект участника команды; возвращает null при невалидных данных
    private TeamMember parseTeamMemberFromJson(JsonObject item) {
        if (item == null) return null;

        TeamMember member = new TeamMember();

        if (item.has("team_id") && !item.get("team_id").isJsonNull()) {
            member.teamId = item.get("team_id").getAsInt();
        }

        if (item.has("user_id") && !item.get("user_id").isJsonNull()) {
            member.userId = item.get("user_id").getAsInt();
        }

        // На сервере поле называется joined_at, локально — createdAt
        if (item.has("joined_at") && !item.get("joined_at").isJsonNull()) {
            member.createdAt = item.get("joined_at").getAsString();
        }

        if (member.teamId <= 0 || member.userId <= 0) {
            return null;
        }

        return member;
    }

    // Разбирает JSON-объект командного задания в модель TeamChallenge
    private TeamChallenge parseTeamChallenge(JsonObject item) {
        TeamChallenge c = new TeamChallenge();

        if (item.has("id") && !item.get("id").isJsonNull())
            c.id = item.get("id").getAsInt();
        if (item.has("team_id") && !item.get("team_id").isJsonNull())
            c.teamId = item.get("team_id").getAsInt();
        if (item.has("title") && !item.get("title").isJsonNull())
            c.title = item.get("title").getAsString();
        if (item.has("condition_type") && !item.get("condition_type").isJsonNull())
            c.conditionType = item.get("condition_type").getAsString();
        if (item.has("target_value") && !item.get("target_value").isJsonNull())
            c.targetValue = item.get("target_value").getAsInt();
        if (item.has("xp_first") && !item.get("xp_first").isJsonNull())
            c.xpFirst = item.get("xp_first").getAsInt();
        if (item.has("xp_second") && !item.get("xp_second").isJsonNull())
            c.xpSecond = item.get("xp_second").getAsInt();
        if (item.has("xp_other") && !item.get("xp_other").isJsonNull())
            c.xpOther = item.get("xp_other").getAsInt();
        if (item.has("is_completed") && !item.get("is_completed").isJsonNull())
            c.isCompleted = item.get("is_completed").getAsBoolean();

        return c;
    }

    // Разбирает JSON-объект прогресса участника; включает username из вложенного объекта user
    private TeamChallengeProgress parseTeamProgress(JsonObject item) {
        TeamChallengeProgress p = new TeamChallengeProgress();

        if (item.has("id") && !item.get("id").isJsonNull())
            p.id = item.get("id").getAsInt();
        if (item.has("challenge_id") && !item.get("challenge_id").isJsonNull())
            p.challengeId = item.get("challenge_id").getAsInt();
        if (item.has("team_id") && !item.get("team_id").isJsonNull())
            p.teamId = item.get("team_id").getAsInt();
        if (item.has("user_id") && !item.get("user_id").isJsonNull())
            p.userId = item.get("user_id").getAsInt();
        if (item.has("progress") && !item.get("progress").isJsonNull())
            p.progress = item.get("progress").getAsInt();
        if (item.has("is_completed") && !item.get("is_completed").isJsonNull())
            p.isCompleted = item.get("is_completed").getAsBoolean();
        if (item.has("completed_at") && !item.get("completed_at").isJsonNull())
            p.completedAt = item.get("completed_at").getAsString();
        if (item.has("place") && !item.get("place").isJsonNull())
            p.place = item.get("place").getAsInt();
        if (item.has("awarded_xp") && !item.get("awarded_xp").isJsonNull())
            p.awardedXp = item.get("awarded_xp").getAsInt();

        if (item.has("user") && item.get("user").isJsonObject()) {
            JsonObject userJson = item.getAsJsonObject("user");
            if (userJson.has("username") && !userJson.get("username").isJsonNull()) {
                p.username = userJson.get("username").getAsString();
            }
        }

        return p;
    }

    // Добавляет команду в список только если команда с таким ID ещё не присутствует
    private void addTeamIfNotExists(List<Team> teams, Team newTeam) {
        if (teams == null || newTeam == null || newTeam.id <= 0) return;
        for (Team team : teams) {
            if (team != null && team.id == newTeam.id) return;
        }
        teams.add(newTeam);
    }

    // Асинхронно сохраняет список команд в локальную БД
    private void cacheTeamsLocal(List<Team> teams) {
        if (teams == null || teams.isEmpty()) return;
        executor.execute(() -> {
            for (Team team : teams) {
                try {
                    teamDao.insert(team);
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка локального сохранения команды: " + e.getMessage(), e);
                }
            }
        });
    }

    // Синхронизирует локальный кэш команд с данными сервера:
    // удаляет устаревшие записи, сохраняет актуальные команды и участия
    private void syncLocalTeamsWithServer(int userId,
                                          List<Team> serverTeams,
                                          List<TeamMember> serverMemberships) {
        executor.execute(() -> {
            try {
                List<Team> safeServerTeams =
                        serverTeams != null ? serverTeams : new ArrayList<>();

                List<TeamMember> safeServerMemberships =
                        serverMemberships != null ? serverMemberships : new ArrayList<>();

                // Собираем ID команд, актуальных для пользователя на сервере
                Set<Integer> serverTeamIds = new HashSet<>();

                for (Team t : safeServerTeams) {
                    if (t != null && t.id > 0) {
                        serverTeamIds.add(t.id);
                    }
                }

                for (TeamMember m : safeServerMemberships) {
                    if (m != null && m.teamId > 0) {
                        serverTeamIds.add(m.teamId);
                    }
                }

                // Удаляем локальные owned-команды, которых нет на сервере
                List<Team> localOwned = teamDao.getOwnedTeams(userId);
                if (localOwned != null) {
                    for (Team localTeam : localOwned) {
                        if (localTeam == null) continue;

                        if (!serverTeamIds.contains(localTeam.id)) {
                            try {
                                teamDao.deleteById(localTeam.id);
                                teamMemberDao.deleteByTeamId(localTeam.id);
                                Log.d(TAG, "Удалена устаревшая owned-команда: "
                                        + localTeam.id);
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка удаления owned-команды: "
                                        + e.getMessage(), e);
                            }
                        }
                    }
                }

                // Удаляем локальные участия, которых нет на сервере
                List<TeamMember> localMemberships = teamMemberDao.getByUserId(userId);
                if (localMemberships != null) {
                    for (TeamMember localMember : localMemberships) {
                        if (localMember == null) continue;

                        if (!serverTeamIds.contains(localMember.teamId)) {
                            try {
                                teamMemberDao.deleteByTeamAndUser(localMember.teamId, userId);
                                teamDao.deleteById(localMember.teamId);
                                Log.d(TAG, "Удалено устаревшее участие: teamId="
                                        + localMember.teamId + ", userId=" + userId);
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка удаления участия: "
                                        + e.getMessage(), e);
                            }
                        }
                    }
                }

                // Сохраняем актуальные команды
                for (Team team : safeServerTeams) {
                    if (team == null || team.id <= 0) continue;

                    try {
                        teamDao.insert(team);
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка сохранения команды: " + e.getMessage(), e);
                    }

                    // Гарантируем локальное членство владельца для стабильного cache-first
                    if (team.ownerId == userId) {
                        try {
                            teamMemberDao.insert(new TeamMember(team.id, userId));
                        } catch (Exception ignored) {}
                    }
                }

                // Сохраняем актуальные участия
                for (TeamMember member : safeServerMemberships) {
                    if (member == null || member.teamId <= 0 || member.userId <= 0) {
                        continue;
                    }

                    try {
                        teamMemberDao.insert(member);
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка сохранения участия: " + e.getMessage(), e);
                    }
                }

                Log.d(TAG, "Синхронизация команд завершена. Команд: "
                        + safeServerTeams.size()
                        + ", участий: "
                        + safeServerMemberships.size());

            } catch (Exception e) {
                Log.e(TAG, "Ошибка sync кэша команд: " + e.getMessage(), e);
            }
        });
    }

    // Возвращает XP-награду в зависимости от занятого места
    private int getTeamChallengeRewardByPlace(JsonObject challengeJson, int place) {
        int xpFirst = 120, xpSecond = 80, xpOther = 50;

        if (challengeJson.has("xp_first") && !challengeJson.get("xp_first").isJsonNull())
            xpFirst = challengeJson.get("xp_first").getAsInt();
        if (challengeJson.has("xp_second") && !challengeJson.get("xp_second").isJsonNull())
            xpSecond = challengeJson.get("xp_second").getAsInt();
        if (challengeJson.has("xp_other") && !challengeJson.get("xp_other").isJsonNull())
            xpOther = challengeJson.get("xp_other").getAsInt();

        if (place == 1) return xpFirst;
        if (place == 2) return xpSecond;
        return xpOther;
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // Создаёт команду без участников (простой сценарий)
    public void createTeam(String teamName, User currentUser,
                           DataCallback<Integer> callback) {
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

        fastApiService.insertTeamRaw(payload).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    int teamId = response.body().get(0).get("id").getAsInt();

                    executor.execute(() -> {
                        try {
                            Team t = new Team(teamName.trim(), currentUser.getId());
                            t.id = teamId;
                            teamDao.insert(t);
                            teamMemberDao.insert(new TeamMember(teamId, currentUser.getId()));
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка локального сохранения: " + e.getMessage(), e);
                        }
                        mainHandler.post(() -> callback.onSuccess(teamId));
                    });
                } else {
                    Log.e(TAG, "Ошибка создания команды: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Не удалось создать команду"));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e(TAG, "Ошибка сети createTeam: " + t.getMessage(), t);
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
                Log.e(TAG, "Ошибка добавления: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось добавить участника"));
            }
        });
    }

    // Создаёт команду с друзьями и заданием за три параллельных шага:
    // 1) создание команды, 2) параллельно: участники + задание, 3) прогресс в фоне
    public void createTeamWithFriends(String teamName,
                                      List<User> selectedFriends,
                                      String challengeType,
                                      int targetValue,
                                      User currentUser,
                                      DataCallback<Integer> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }
        if (teamName == null || teamName.trim().isEmpty()) {
            callback.onError("Введите название команды");
            return;
        }

        List<User> friends = selectedFriends != null ? selectedFriends : new ArrayList<>();
        if (friends.size() > 3) {
            callback.onError("В команду можно добавить максимум 3 друзей");
            return;
        }

        int ownerId = currentUser.getId();

        // Собираем ID всех участников заранее
        List<Integer> memberIds = new ArrayList<>();
        memberIds.add(ownerId);
        for (User friend : friends) {
            if (friend != null && friend.getId() != null
                    && !memberIds.contains(friend.getId())) {
                memberIds.add(friend.getId());
            }
        }

        // Шаг 1: создаём команду на сервере
        JsonObject teamPayload = new JsonObject();
        teamPayload.addProperty("team_name", teamName.trim());
        teamPayload.addProperty("owner_id", ownerId);

        fastApiService.insertTeamRaw(teamPayload).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().isEmpty()) {
                    Log.e(TAG, "Ошибка создания команды: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError(
                            "Не удалось создать команду: " + response.code()));
                    return;
                }

                int teamId = response.body().get(0).get("id").getAsInt();
                Log.d(TAG, "Команда создана, teamId=" + teamId);

                // Кэшируем команду локально, не блокируя следующие шаги
                executor.execute(() -> {
                    try {
                        Team createdTeam = new Team();
                        createdTeam.id = teamId;
                        createdTeam.teamName = teamName.trim();
                        createdTeam.ownerId = ownerId;
                        teamDao.insert(createdTeam);
                    } catch (Exception ignored) {}
                });

                // Шаг 2: параллельно создаём участников и задание
                launchParallelCreation(teamId, memberIds, challengeType, targetValue, callback);
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e(TAG, "Ошибка сети insertTeamRaw: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети при создании команды"));
            }
        });
    }

    // Запускает создание участников и задания параллельно;
    // когда оба завершились — создаёт прогресс и вызывает callback
    private void launchParallelCreation(int teamId,
                                        List<Integer> memberIds,
                                        String challengeType,
                                        int targetValue,
                                        DataCallback<Integer> callback) {

        AtomicInteger remaining = new AtomicInteger(2);
        AtomicReference<Integer> challengeIdRef = new AtomicReference<>(-1);
        AtomicBoolean errorReported = new AtomicBoolean(false);

        // После завершения обоих шагов — создаём прогресс и уведомляем UI
        Runnable onPartCompleted = () -> {
            if (remaining.decrementAndGet() == 0) {
                int challengeId = challengeIdRef.get();
                if (challengeId > 0) {
                    createProgressAndFinish(teamId, challengeId, memberIds,
                            callback, errorReported);
                } else {
                    Log.w(TAG, "Челлендж не создан, переходим без прогресса");
                    mainHandler.post(() -> callback.onSuccess(teamId));
                }
            }
        };

        createMembersParallel(teamId, memberIds, onPartCompleted,
                errorReported, callback);
        createChallengeParallel(teamId, memberIds, challengeType, targetValue,
                challengeIdRef, onPartCompleted, errorReported, callback);
    }

    // Создаёт записи прогресса для всех участников, затем уведомляет UI
    private void createProgressAndFinish(int teamId,
                                         int challengeId,
                                         List<Integer> memberIds,
                                         DataCallback<Integer> callback,
                                         AtomicBoolean errorReported) {
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

        fastApiService.insertTeamChallengeProgressMinimalRaw(progressPayload)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call,
                                           Response<Void> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Прогресс создан для challengeId="
                                    + challengeId + ", участников: "
                                    + memberIds.size());

                            // Кэшируем прогресс локально перед вызовом callback
                            executor.execute(() -> {
                                for (Integer userId : memberIds) {
                                    try {
                                        TeamChallengeProgress p =
                                                new TeamChallengeProgress();
                                        p.challengeId = challengeId;
                                        p.teamId = teamId;
                                        p.userId = userId;
                                        p.progress = 0;
                                        p.isCompleted = false;
                                        p.awardedXp = 0;
                                        teamChallengeProgressDao.insert(p);
                                    } catch (Exception ignored) {}
                                }

                                mainHandler.post(
                                        () -> callback.onSuccess(teamId));
                            });
                        } else {
                            Log.e(TAG, "Ошибка создания прогресса: "
                                    + response.code() + " | "
                                    + getErrorBody(response));
                            // TeamDetailActivity повторит запрос при открытии
                            mainHandler.post(
                                    () -> callback.onSuccess(teamId));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "Ошибка сети создания прогресса: "
                                + t.getMessage(), t);
                        mainHandler.post(() -> callback.onSuccess(teamId));
                    }
                });
    }

    // Создаёт участников команды (параллельный шаг 2a)
    private void createMembersParallel(int teamId,
                                       List<Integer> memberIds,
                                       Runnable onDone,
                                       AtomicBoolean errorReported,
                                       DataCallback<Integer> callback) {
        List<JsonObject> membersPayload = new ArrayList<>();
        for (Integer userId : memberIds) {
            JsonObject item = new JsonObject();
            item.addProperty("team_id", teamId);
            item.addProperty("user_id", userId);
            membersPayload.add(item);
        }

        fastApiService.insertTeamMembersMinimalRaw(membersPayload)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Участники созданы для teamId=" + teamId);

                            // Кэшируем участников локально перед вызовом onDone
                            executor.execute(() -> {
                                for (Integer userId : memberIds) {
                                    try {
                                        teamMemberDao.insert(
                                                new TeamMember(teamId, userId));
                                    } catch (Exception ignored) {}
                                }
                                onDone.run();
                            });
                        } else {
                            Log.e(TAG, "Ошибка добавления участников: "
                                    + response.code() + " | "
                                    + getErrorBody(response));
                            if (errorReported.compareAndSet(false, true)) {
                                mainHandler.post(() -> callback.onError(
                                        "Команда создана, но участников "
                                                + "добавить не удалось"));
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "Ошибка сети createMembers: "
                                + t.getMessage(), t);
                        if (errorReported.compareAndSet(false, true)) {
                            mainHandler.post(() -> callback.onError(
                                    "Ошибка сети при добавлении участников"));
                        }
                    }
                });
    }

    // Создаёт командное задание (параллельный шаг 2b)
    private void createChallengeParallel(int teamId,
                                         List<Integer> memberIds,
                                         String challengeType,
                                         int targetValue,
                                         AtomicReference<Integer> challengeIdRef,
                                         Runnable onDone,
                                         AtomicBoolean errorReported,
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

        fastApiService.insertTeamChallengeRaw(payload)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && !response.body().isEmpty()) {

                            JsonObject created = response.body().get(0);
                            if (created.has("id")
                                    && !created.get("id").isJsonNull()) {
                                int challengeId = created.get("id").getAsInt();
                                challengeIdRef.set(challengeId);
                                Log.d(TAG, "Челлендж создан, id=" + challengeId);

                                // Кэшируем задание локально перед вызовом onDone
                                executor.execute(() -> {
                                    try {
                                        TeamChallenge tc = new TeamChallenge();
                                        tc.id = challengeId;
                                        tc.teamId = teamId;
                                        tc.title = title;
                                        tc.conditionType = challengeType;
                                        tc.targetValue = targetValue;
                                        tc.xpFirst = 120;
                                        tc.xpSecond = 80;
                                        tc.xpOther = 50;
                                        tc.isCompleted = false;
                                        teamChallengeDao.insert(tc);
                                    } catch (Exception ignored) {}

                                    onDone.run();
                                });
                            } else {
                                // id не пришёл — продолжаем без задания
                                onDone.run();
                            }
                        } else {
                            Log.e(TAG, "Ошибка создания челленджа: "
                                    + response.code() + " | "
                                    + getErrorBody(response));
                            if (errorReported.compareAndSet(false, true)) {
                                mainHandler.post(() -> callback.onError(
                                        "Не удалось создать задание команды"));
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call,
                                          Throwable t) {
                        Log.e(TAG, "Ошибка сети createChallenge: "
                                + t.getMessage(), t);
                        if (errorReported.compareAndSet(false, true)) {
                            mainHandler.post(() -> callback.onError(
                                    "Ошибка сети при создании задания"));
                        }
                    }
                });
    }

    // Cache-first: сначала отдаёт локальный список команд, затем обновляет с сервера
    public void getMyTeams(User currentUser, DataCallback<List<Team>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        int userId = currentUser.getId();

        executor.execute(() -> {
            try {
                List<Team> localTeams = new ArrayList<>();

                List<Team> ownedTeams = teamDao.getOwnedTeams(userId);
                if (ownedTeams != null) {
                    for (Team team : ownedTeams) {
                        addTeamIfNotExists(localTeams, team);
                    }
                }

                List<TeamMember> memberships = teamMemberDao.getByUserId(userId);
                if (memberships != null) {
                    for (TeamMember member : memberships) {
                        if (member == null) continue;

                        Team team = teamDao.getById(member.teamId);
                        if (team != null) {
                            addTeamIfNotExists(localTeams, team);
                        }
                    }
                }

                // Мгновенно отдаём кэш, даже если пустой
                List<Team> cached = new ArrayList<>(localTeams);
                mainHandler.post(() -> callback.onSuccess(cached));

                // Фоновое обновление с сервера
                refreshTeamsFromServer(userId, localTeams, callback);

            } catch (Exception e) {
                Log.e(TAG, "Ошибка локальной загрузки команд: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                refreshTeamsFromServer(userId, new ArrayList<>(), callback);
            }
        });
    }

    // Загружает команды с сервера в три шага:
    // 1) owned-команды, 2) memberships, 3) команды по team_id из memberships
    private void refreshTeamsFromServer(int userId,
                                        List<Team> localTeams,
                                        DataCallback<List<Team>> callback) {
        fastApiService.getOwnedTeamsRaw(
                "eq." + userId,
                "id,team_name,owner_id",
                "id.desc"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> ownedCall,
                                   Response<List<JsonObject>> ownedResponse) {

                List<Team> result = new ArrayList<>();
                List<TeamMember> serverMemberships = new ArrayList<>();

                // Шаг 1: команды, где пользователь владелец
                if (ownedResponse.isSuccessful() && ownedResponse.body() != null) {
                    for (JsonObject item : ownedResponse.body()) {
                        Team team = parseTeamFromJson(item);

                        if (team != null && team.id > 0) {
                            addTeamIfNotExists(result, team);
                            serverMemberships.add(new TeamMember(team.id, userId));
                        }
                    }
                } else if (!ownedResponse.isSuccessful()) {
                    Log.e(TAG, "Ошибка getOwnedTeamsRaw: "
                            + ownedResponse.code() + " | " + getErrorBody(ownedResponse));

                    if (localTeams == null || localTeams.isEmpty()) {
                        mainHandler.post(() ->
                                callback.onError("Не удалось загрузить команды"));
                    }
                    return;
                }

                // Шаг 2: команды, где пользователь участник.
                // ВАЖНО: team_members не содержит колонку id — запрашиваем только team_id,user_id,joined_at
                fastApiService.getMyTeamMembersRaw(
                        "eq." + userId,
                        "team_id,user_id,joined_at"
                ).enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> membersCall,
                                           Response<List<JsonObject>> membersResponse) {

                        if (!membersResponse.isSuccessful()
                                || membersResponse.body() == null) {

                            Log.e(TAG, "Ошибка getMyTeamMembersRaw: "
                                    + membersResponse.code()
                                    + " | "
                                    + getErrorBody(membersResponse));

                            syncLocalTeamsWithServer(userId, result, serverMemberships);
                            mainHandler.post(() -> callback.onSuccess(result));
                            return;
                        }

                        List<Integer> teamIds = new ArrayList<>();

                        for (JsonObject item : membersResponse.body()) {
                            TeamMember member = parseTeamMemberFromJson(item);

                            if (member == null) continue;

                            serverMemberships.add(member);

                            if (!teamIds.contains(member.teamId)) {
                                teamIds.add(member.teamId);
                            }
                        }

                        // Нет участий — показываем только owned-команды
                        if (teamIds.isEmpty()) {
                            syncLocalTeamsWithServer(userId, result, serverMemberships);
                            mainHandler.post(() -> callback.onSuccess(result));
                            return;
                        }

                        StringBuilder idsFilter = new StringBuilder("in.(");

                        for (int i = 0; i < teamIds.size(); i++) {
                            if (i > 0) idsFilter.append(",");
                            idsFilter.append(teamIds.get(i));
                        }

                        idsFilter.append(")");

                        // Шаг 3: загружаем данные команд по собранным team_id
                        fastApiService.getTeamsByIdsRaw(
                                idsFilter.toString(),
                                "id,team_name,owner_id",
                                "id.desc"
                        ).enqueue(new Callback<List<JsonObject>>() {
                            @Override
                            public void onResponse(Call<List<JsonObject>> c,
                                                   Response<List<JsonObject>> r) {

                                if (r.isSuccessful() && r.body() != null) {
                                    for (JsonObject item : r.body()) {
                                        Team team = parseTeamFromJson(item);

                                        if (team != null && team.id > 0) {
                                            addTeamIfNotExists(result, team);
                                        }
                                    }
                                } else if (!r.isSuccessful()) {
                                    Log.e(TAG, "Ошибка getTeamsByIdsRaw: "
                                            + r.code() + " | " + getErrorBody(r));
                                }

                                syncLocalTeamsWithServer(
                                        userId, result, serverMemberships);

                                mainHandler.post(() -> callback.onSuccess(result));
                            }

                            @Override
                            public void onFailure(Call<List<JsonObject>> c,
                                                  Throwable t) {
                                Log.e(TAG, "Ошибка сети getTeamsByIdsRaw: "
                                        + t.getMessage(), t);

                                if (!result.isEmpty()) {
                                    syncLocalTeamsWithServer(
                                            userId, result, serverMemberships);
                                    mainHandler.post(() -> callback.onSuccess(result));
                                } else if (localTeams == null || localTeams.isEmpty()) {
                                    mainHandler.post(() ->
                                            callback.onError("Не удалось загрузить команды"));
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> membersCall,
                                          Throwable t) {
                        Log.e(TAG, "Ошибка сети getMyTeamMembersRaw: "
                                + t.getMessage(), t);

                        if (!result.isEmpty()) {
                            syncLocalTeamsWithServer(userId, result, serverMemberships);
                            mainHandler.post(() -> callback.onSuccess(result));
                        } else if (localTeams == null || localTeams.isEmpty()) {
                            mainHandler.post(() ->
                                    callback.onError("Не удалось загрузить команды"));
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<List<JsonObject>> ownedCall,
                                  Throwable t) {
                Log.e(TAG, "Ошибка сети getOwnedTeamsRaw: " + t.getMessage(), t);

                if (localTeams == null || localTeams.isEmpty()) {
                    mainHandler.post(() ->
                            callback.onError("Не удалось загрузить команды"));
                }
            }
        });
    }

    // Возвращает список команд только из локальной БД без сетевого запроса
    public void loadMyTeamsLocal(int userId, DataCallback<List<Team>> callback) {
        executor.execute(() -> {
            try {
                List<Team> result = new ArrayList<>();
                List<Team> ownedTeams = teamDao.getOwnedTeams(userId);
                if (ownedTeams != null) {
                    for (Team team : ownedTeams) addTeamIfNotExists(result, team);
                }

                List<TeamMember> memberships = teamMemberDao.getByUserId(userId);
                if (memberships != null) {
                    for (TeamMember member : memberships) {
                        if (member == null) continue;
                        Team team = teamDao.getById(member.teamId);
                        if (team != null) addTeamIfNotExists(result, team);
                    }
                }

                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локальной загрузки: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить команды"));
            }
        });
    }

    // Cache-first: отдаёт кэш задания, затем тихо обновляет с сервера
    public void getTeamChallenge(int teamId, DataCallback<TeamChallenge> callback) {
        executor.execute(() -> {
            try {
                TeamChallenge cached = teamChallengeDao.getLatestByTeamId(teamId);

                if (cached != null) {
                    mainHandler.post(() -> callback.onSuccess(cached));
                    if (!isFetchingChallenge) {
                        silentRefreshTeamChallenge(teamId);
                    }
                } else {
                    mainHandler.post(() ->
                            refreshTeamChallengeFromServer(teamId, false, callback));
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка чтения кэша челленджа: " + e.getMessage(), e);
                mainHandler.post(() ->
                        refreshTeamChallengeFromServer(teamId, false, callback));
            }
        });
    }

    // Загружает актуальное задание с сервера; при ошибке уведомляет UI только если кэша не было
    private void refreshTeamChallengeFromServer(int teamId,
                                                boolean hasCachedData,
                                                DataCallback<TeamChallenge> callback) {
        if (isFetchingChallenge) {
            Log.d(TAG, "Запрос челленджа уже выполняется, пропускаем");
            return;
        }

        isFetchingChallenge = true;

        fastApiService.getTeamChallengeRaw(
                "eq." + teamId,
                CHALLENGE_SELECT,
                "id.desc",
                1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                isFetchingChallenge = false;

                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {

                    TeamChallenge challenge = parseTeamChallenge(response.body().get(0));

                    executor.execute(() -> {
                        try {
                            teamChallengeDao.insert(challenge);
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка кэширования челленджа: " + e.getMessage(), e);
                        }
                    });

                    mainHandler.post(() -> callback.onSuccess(challenge));

                } else if (response.isSuccessful()) {
                    if (!hasCachedData) {
                        mainHandler.post(() ->
                                callback.onError("Задание команды ещё не создано"));
                    }

                } else {
                    String err = getErrorBody(response);
                    Log.e(TAG, "Ошибка загрузки челленджа: "
                            + response.code() + " | " + err);
                    if (!hasCachedData) {
                        mainHandler.post(() ->
                                callback.onError("Ошибка сервера: " + response.code()));
                    }
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                isFetchingChallenge = false;
                Log.e(TAG, "Ошибка сети getTeamChallenge: " + t.getMessage(), t);
                if (!hasCachedData) {
                    mainHandler.post(() ->
                            callback.onError("Ошибка загрузки задания команды"));
                }
            }
        });
    }

    // Тихое фоновое обновление кэша задания без уведомления UI
    private void silentRefreshTeamChallenge(int teamId) {
        if (isFetchingChallenge) return;
        isFetchingChallenge = true;

        fastApiService.getTeamChallengeRaw(
                "eq." + teamId,
                CHALLENGE_SELECT,
                "id.desc",
                1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                isFetchingChallenge = false;
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {

                    TeamChallenge fresh = parseTeamChallenge(response.body().get(0));

                    executor.execute(() -> {
                        try {
                            teamChallengeDao.insert(fresh);
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка кэширования: " + e.getMessage(), e);
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                isFetchingChallenge = false;
            }
        });
    }

    private void silentRefreshTeamProgress(int challengeId) {
        silentRefreshTeamProgress(challengeId, null);
    }

    // Загружает свежий прогресс с сервера, обновляет кэш и уведомляет UI через callback если задан
    private void silentRefreshTeamProgress(int challengeId,
                                           DataCallback<List<TeamChallengeProgress>> callback) {
        fastApiService.getTeamProgressRaw(
                "eq." + challengeId,
                PROGRESS_SELECT,
                "progress.desc"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<TeamChallengeProgress> fresh = new ArrayList<>();

                    for (JsonObject item : response.body()) {
                        TeamChallengeProgress progress = parseTeamProgress(item);
                        if (progress != null) {
                            fresh.add(progress);
                        }
                    }

                    executor.execute(() -> {
                        try {
                            teamChallengeProgressDao.deleteByChallengeId(challengeId);

                            for (TeamChallengeProgress p : fresh) {
                                teamChallengeProgressDao.insert(p);
                            }

                            Log.d(TAG, "Кэш прогресса обновлён, challengeId="
                                    + challengeId + ", записей: " + fresh.size());

                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка обновления кэша прогресса: "
                                    + e.getMessage(), e);
                        }

                        // Обновляем UI свежими данными если callback передан
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess(fresh));
                        }
                    });

                } else {
                    Log.e(TAG, "Ошибка загрузки прогресса: "
                            + response.code() + " | " + getErrorBody(response));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.w(TAG, "Фоновое обновление прогресса не удалось: "
                        + t.getMessage(), t);
            }
        });
    }

    // Удаляет команду на сервере, затем очищает локальные данные
    public void deleteTeam(int teamId, int userId, DataCallback<Void> callback) {
        fastApiService.deleteTeamRaw("eq." + teamId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    executor.execute(() -> {
                        try {
                            teamDao.deleteById(teamId);
                            teamMemberDao.deleteByTeamId(teamId);
                            teamChallengeDao.deleteByTeamId(teamId);
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка локального удаления: " + e.getMessage(), e);
                        }
                        mainHandler.post(() -> callback.onSuccess(null));
                    });
                } else {
                    Log.e(TAG, "Ошибка удаления команды: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Ошибка сервера"));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Ошибка сети deleteTeam: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Нет интернета"));
            }
        });
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    // Cache-first: сначала отдаёт кэш прогресса, затем загружает актуальные данные с сервера
    public void getTeamProgress(int challengeId,
                                DataCallback<List<TeamChallengeProgress>> callback) {
        executor.execute(() -> {
            boolean hasCachedData = false;
            try {
                List<TeamChallengeProgress> cached =
                        teamChallengeProgressDao.getByChallengeId(challengeId);

                if (cached != null && !cached.isEmpty()) {
                    hasCachedData = true;
                    mainHandler.post(() -> callback.onSuccess(cached));
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка чтения кэша прогресса: " + e.getMessage(), e);
            }

            // Загружаем с сервера и снова уведомляем UI актуальными данными
            fetchProgressFromServerAndNotify(challengeId, hasCachedData, callback);
        });
    }

    // Загружает прогресс с сервера, обновляет локальный кэш и уведомляет UI
    private void fetchProgressFromServerAndNotify(int challengeId,
                                                  boolean hasCachedData,
                                                  DataCallback<List<TeamChallengeProgress>> callback) {
        fastApiService.getTeamProgressRaw(
                "eq." + challengeId,
                PROGRESS_SELECT,
                "progress.desc"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<TeamChallengeProgress> fresh = new ArrayList<>();

                    for (JsonObject item : response.body()) {
                        TeamChallengeProgress progress = parseTeamProgress(item);
                        if (progress != null) {
                            fresh.add(progress);
                        }
                    }

                    executor.execute(() -> {
                        try {
                            // Полная замена кэша прогресса
                            teamChallengeProgressDao.deleteByChallengeId(challengeId);
                            for (TeamChallengeProgress p : fresh) {
                                teamChallengeProgressDao.insert(p);
                            }
                            Log.d(TAG, "Кэш прогресса обновлён (challengeId=" + challengeId + ")");
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка обновления кэша прогресса: " + e.getMessage(), e);
                        }

                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess(fresh));
                        }
                    });

                } else {
                    Log.e(TAG, "Ошибка загрузки прогресса: "
                            + response.code() + " | " + getErrorBody(response));
                    if (!hasCachedData && callback != null) {
                        mainHandler.post(() -> callback.onError("Не удалось загрузить прогресс"));
                    }
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e(TAG, "Сбой сети при загрузке прогресса: " + t.getMessage(), t);
                if (!hasCachedData && callback != null) {
                    mainHandler.post(() -> callback.onError("Ошибка сети"));
                }
            }
        });
    }

    // Обновляет прогресс по всем активным командным заданиям пользователя после завершения урока
    public void updateTeamChallengeProgressAfterLesson(int userId, int earnedXp) {
        String select =
                "id,challenge_id,team_id,user_id,progress,is_completed,awarded_xp,place," +
                        "challenge:team_challenges!team_challenge_progress_challenge_id_fkey(" +
                        "id,team_id,title,condition_type,target_value,xp_first,xp_second," +
                        "xp_other,is_completed,winner_user_id)";

        fastApiService.getMyActiveTeamProgressRaw("eq." + userId, "eq.false", select)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        for (JsonObject progressJson : response.body()) {
                            try {
                                processSingleTeamProgress(progressJson, earnedXp);
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка обработки прогресса: " + e.getMessage(), e);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e(TAG, "Ошибка сети updateTeamChallengeProgress: " + t.getMessage(), t);
                    }
                });
    }

    // Вычисляет новый прогресс по заданию и либо обновляет его, либо завершает выполнение
    private void processSingleTeamProgress(JsonObject progressJson, int earnedXp) {
        if (progressJson == null) return;
        if (!progressJson.has("id") || progressJson.get("id").isJsonNull()) return;
        if (!progressJson.has("progress") || progressJson.get("progress").isJsonNull()) return;
        if (!progressJson.has("is_completed") || progressJson.get("is_completed").isJsonNull()) return;
        if (!progressJson.has("challenge") || !progressJson.get("challenge").isJsonObject()) return;
        if (progressJson.get("is_completed").getAsBoolean()) return;

        int progressId = progressJson.get("id").getAsInt();
        int oldProgress = progressJson.get("progress").getAsInt();
        JsonObject challengeJson = progressJson.getAsJsonObject("challenge");

        if (!challengeJson.has("id") || challengeJson.get("id").isJsonNull()) return;
        if (!challengeJson.has("condition_type") || challengeJson.get("condition_type").isJsonNull()) return;
        if (!challengeJson.has("target_value") || challengeJson.get("target_value").isJsonNull()) return;

        int challengeId = challengeJson.get("id").getAsInt();
        String conditionType = challengeJson.get("condition_type").getAsString();
        int targetValue = challengeJson.get("target_value").getAsInt();

        // XP-задание увеличивает прогресс на заработанный XP; LESSONS — на 1 за урок
        int addProgress;
        if ("XP".equals(conditionType)) addProgress = earnedXp;
        else if ("LESSONS".equals(conditionType)) addProgress = 1;
        else return;

        int newProgress = oldProgress + addProgress;

        if (newProgress >= targetValue) {
            // Цель достигнута — завершаем задание
            finishTeamChallengeProgress(progressId, challengeId, challengeJson, newProgress);
        } else {
            JsonObject payload = new JsonObject();
            payload.addProperty("progress", newProgress);

            fastApiService.updateTeamChallengeProgressRaw("eq." + progressId, payload)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                Log.d(TAG, "Прогресс команды обновлён (progressId=" + progressId + ")");
                                // Обновляем кэш, чтобы UI подтянул свежие данные быстрее
                                fetchProgressFromServerAndNotify(challengeId, true, null);
                            } else {
                                Log.e(TAG, "Не удалось обновить прогресс: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Log.e(TAG, "Ошибка сети updateProgress: " + t.getMessage(), t);
                        }
                    });
        }
    }

    // Завершает выполнение задания: определяет место, начисляет XP, обновляет запись на сервере
    private void finishTeamChallengeProgress(int progressId, int challengeId,
                                             JsonObject challengeJson, int finalProgress) {
        // Проверяем, не завершено ли задание уже (защита от двойного завершения)
        fastApiService.getTeamProgressByIdRaw("eq." + progressId, "id,is_completed,awarded_xp,place", 1)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> checkCall, Response<List<JsonObject>> checkResponse) {
                        if (!checkResponse.isSuccessful() || checkResponse.body() == null || checkResponse.body().isEmpty()) return;

                        JsonObject currentProgress = checkResponse.body().get(0);
                        boolean alreadyCompleted = currentProgress.has("is_completed")
                                && !currentProgress.get("is_completed").isJsonNull()
                                && currentProgress.get("is_completed").getAsBoolean();
                        if (alreadyCompleted) return;

                        // Считаем уже завершивших участников для определения места
                        fastApiService.getCompletedTeamProgressRaw("eq." + challengeId, "eq.true", "completed_at.asc")
                                .enqueue(new Callback<List<JsonObject>>() {
                                    @Override
                                    public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                                        int completedCount = 0;
                                        if (response.isSuccessful() && response.body() != null)
                                            completedCount = response.body().size();

                                        int place = completedCount + 1;
                                        int rewardXp = getTeamChallengeRewardByPlace(challengeJson, place);

                                        String timestamp = rewardListener != null
                                                ? rewardListener.toSqlTimestamp(System.currentTimeMillis()) : "";

                                        JsonObject payload = new JsonObject();
                                        payload.addProperty("progress", finalProgress);
                                        payload.addProperty("is_completed", true);
                                        payload.addProperty("completed_at", timestamp);
                                        payload.addProperty("place", place);
                                        payload.addProperty("awarded_xp", rewardXp);

                                        fastApiService.updateTeamChallengeProgressRaw("eq." + progressId, payload)
                                                .enqueue(new Callback<Void>() {
                                                    @Override
                                                    public void onResponse(Call<Void> call, Response<Void> r) {
                                                        if (r.isSuccessful()) {
                                                            Log.d(TAG, "Командный прогресс завершён (progressId=" + progressId + ")");

                                                            // Обновляем кэш и выдаём награду
                                                            fetchProgressFromServerAndNotify(challengeId, true, null);

                                                            if (rewardListener != null) {
                                                                rewardListener.onGrantXp(rewardXp);
                                                                if (place == 1) {
                                                                    rewardListener.onMarkWinner(challengeId);
                                                                }
                                                            }
                                                        }
                                                    }

                                                    @Override
                                                    public void onFailure(Call<Void> call, Throwable t) {
                                                        Log.e(TAG, "Ошибка finish: " + t.getMessage(), t);
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                                        Log.e(TAG, "Ошибка получения мест: " + t.getMessage(), t);
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> checkCall, Throwable t) {
                        Log.e(TAG, "Ошибка проверки: " + t.getMessage(), t);
                    }
                });
    }

    // Сбрасывает состояние репозитория при выходе из аккаунта
    public void resetState() {
        isFetchingChallenge = false;
        Log.d(TAG, "resetState: флаги TeamsRepository сброшены");
    }
}