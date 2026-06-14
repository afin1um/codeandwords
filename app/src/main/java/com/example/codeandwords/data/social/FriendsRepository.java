package com.example.codeandwords.data.social;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.AppDatabase;
import com.example.codeandwords.db.FriendDao;
import com.example.codeandwords.db.UserDao;
import com.example.codeandwords.model.Friend;
import com.example.codeandwords.model.User;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FriendsRepository {

    private static final String TAG = "FriendsRepository";

    private final AppDatabase database;
    private final FriendDao friendDao;
    private final UserDao userDao;
    private final ApiService apiService;
    private final ApiService fastApiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // Защита от параллельных запросов
    private volatile boolean isFetchingFriends = false;

    public FriendsRepository(AppDatabase database,
                             FriendDao friendDao,
                             UserDao userDao,
                             ApiService apiService,
                             ApiService fastApiService,
                             ExecutorService executor,
                             Handler mainHandler) {
        this.database = database;
        this.friendDao = friendDao;
        this.userDao = userDao;
        this.apiService = apiService;
        this.fastApiService = fastApiService;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    // ===== ИНТЕРФЕЙС CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private JsonObject buildFriendPayload(int userId, int friendId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("user_id", userId);
        payload.addProperty("friend_id", friendId);
        return payload;
    }

    private User parseUserFromJson(JsonObject userJson) {
        User user = new User();

        if (userJson == null) return user;

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

    // ===== ДОБАВЛЕНИЕ ДРУГА =====

    public void addFriend(int friendId,
                          User currentUser,
                          DataCallback<Void> callback) {
        addFriend(friendId, null, currentUser, callback);
    }

    public void addFriend(int friendId,
                          User friendUser,
                          User currentUser,
                          DataCallback<Void> callback) {
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

        fastApiService.upsertFriendRaw("user_id,friend_id", payload)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        boolean isSuccess = response.isSuccessful();
                        boolean isAlreadyFriends = false;

                        if (!isSuccess && response.code() == 409) {
                            String errorBody = getErrorBody(response);
                            if (errorBody != null
                                    && (errorBody.contains("already exists")
                                    || errorBody.contains("duplicate")
                                    || errorBody.contains("23505"))) {
                                isAlreadyFriends = true;
                                Log.d(TAG, "Пользователи уже друзья (409) — считаем успехом");
                            }
                        }

                        if (isSuccess || isAlreadyFriends) {
                            executor.execute(() -> {
                                try {
                                    if (friendUser != null && friendUser.getId() != null) {
                                        userDao.insertUser(friendUser);
                                        Log.d(TAG, "Пользователь " + friendUser.getId()
                                                + " сохранён в локальной БД");
                                    }

                                    friendDao.insert(new Friend(currentUserId, friendId));
                                } catch (Exception e) {
                                    Log.e(TAG,
                                            "Ошибка локального сохранения друга: "
                                                    + e.getMessage(), e);
                                }
                                mainHandler.post(() -> callback.onSuccess(null));
                            });
                        } else {
                            Log.e(TAG, "Ошибка добавления друга: "
                                    + response.code() + " | " + getErrorBody(response));
                            mainHandler.post(() -> callback.onError(
                                    "Не удалось добавить друга"));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e(TAG, "Ошибка сети addFriend: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError(
                                "Ошибка сети при добавлении друга"));
                    }
                });
    }

    // ===== ЗАГРУЗКА ДРУЗЕЙ: CACHE-FIRST =====

    /**
     * ✅ ИСПРАВЛЕНО:
     * 1. ВСЕГДА вызываем callback — даже если кэш пуст (пустой список).
     *    Раньше при пустом кэше callback не вызывался, и если одновременно
     *    isFetchingFriends == true, UI оставался пустым навсегда.
     * 2. ВСЕГДА запускаем обновление с сервера (убрали внешнюю проверку
     *    isFetchingFriends — она осталась только внутри refreshFriendsFromServer
     *    для защиты от дублирующих сетевых запросов).
     */
    public void getFriends(User currentUser,
                           DataCallback<List<User>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        int currentUserId = currentUser.getId();

        // 1. СРАЗУ показываем кэш (даже если пустой — чтобы UI не зависал)
        executor.execute(() -> {
            List<User> localFriends = null;
            try {
                localFriends = friendDao.getFriends(currentUserId);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локальной загрузки друзей: " + e.getMessage(), e);
            }

            // ✅ КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: вызываем callback ВСЕГДА
            final List<User> cached =
                    localFriends != null ? localFriends : new ArrayList<>();
            mainHandler.post(() -> callback.onSuccess(cached));
        });

        // 2. ВСЕГДА обновляем с сервера (независимо от кэша)
        refreshFriendsFromServer(currentUserId, callback);
    }

    /**
     * ✅ ИСПРАВЛЕНО:
     * - Убран параметр hasCachedData — кэш УЖЕ показан в getFriends,
     *   поэтому при ошибке сервера просто логируем и не трогаем UI.
     * - При успехе ВСЕГДА вызываем callback.onSuccess с актуальным списком.
     */
    private void refreshFriendsFromServer(int currentUserId,
                                          DataCallback<List<User>> callback) {
        if (isFetchingFriends) {
            Log.d(TAG, "Запрос друзей уже выполняется — пропускаем дублирующий");
            return;
        }

        isFetchingFriends = true;

        String select =
                "id,user_id,friend_id," +
                        "user:users!user_friends_user_id_fkey(" +
                        "id,username,email,password_hash,current_level," +
                        "total_xp,role,created_at,avatar_config)," +
                        "friend:users!user_friends_friend_id_fkey(" +
                        "id,username,email,password_hash,current_level," +
                        "total_xp,role,created_at,avatar_config)";

        fastApiService.getFriendsBothRaw(
                "(user_id.eq." + currentUserId
                        + ",friend_id.eq." + currentUserId + ")",
                select
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                isFetchingFriends = false;

                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        try {
                            List<User> friends = parseAndSaveFriends(
                                    currentUserId, response.body());

                            // ✅ Обновляем UI актуальным списком с сервера
                            mainHandler.post(() -> callback.onSuccess(friends));
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка обработки друзей: "
                                    + e.getMessage(), e);
                            // Кэш уже показан — не ломаем UI
                        }
                    });
                } else {
                    Log.e(TAG, "Ошибка загрузки друзей: "
                            + response.code() + " | " + getErrorBody(response));
                    // Кэш уже показан — не ломаем UI
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                isFetchingFriends = false;
                Log.e(TAG, "Ошибка сети getFriends: " + t.getMessage(), t);
                // Кэш уже показан — не ломаем UI
            }
        });
    }

    private List<User> parseAndSaveFriends(int currentUserId,
                                           List<JsonObject> jsonList) {
        List<User> friends = new ArrayList<>();
        Set<Integer> addedIds = new HashSet<>();

        for (JsonObject item : jsonList) {
            if (item == null) continue;

            int userId = item.has("user_id") && !item.get("user_id").isJsonNull()
                    ? item.get("user_id").getAsInt() : -1;

            int friendId = item.has("friend_id") && !item.get("friend_id").isJsonNull()
                    ? item.get("friend_id").getAsInt() : -1;

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

            if (targetUserId <= 0 || targetUserJson == null) continue;
            if (addedIds.contains(targetUserId)) continue;

            User friendUser = parseUserFromJson(targetUserJson);

            if (friendUser.getId() == null) {
                friendUser.setId(targetUserId);
            }

            addedIds.add(friendUser.getId());
            friends.add(friendUser);
        }

        try {
            database.runInTransaction(() -> {
                friendDao.deleteFriendsByUser(currentUserId);
                for (User friendUser : friends) {
                    userDao.insertUser(friendUser);
                    friendDao.insert(new Friend(currentUserId, friendUser.getId()));
                }
            });

            Log.d(TAG, "Сохранено " + friends.size() + " друзей в транзакции");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка сохранения друзей в транзакции: " + e.getMessage(), e);
        }

        return friends;
    }

    // ===== ПОИСК ПОЛЬЗОВАТЕЛЯ =====

    public void findUserByUsername(String username,
                                   DataCallback<User> callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onError("Введите ник пользователя");
            return;
        }

        String cleanUsername = username.trim();

        executor.execute(() -> {
            try {
                User cachedUser = userDao.getUserByUsername(cleanUsername);
                if (cachedUser != null) {
                    mainHandler.post(() -> callback.onSuccess(cachedUser));
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального поиска: " + e.getMessage(), e);
            }
        });

        fastApiService.findUserByUsername(
                "eq." + cleanUsername,
                "id,username,email,current_level,total_xp,role,created_at,avatar_config,gender",
                1
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call,
                                   Response<List<JsonObject>> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {

                    User foundUser = parseUserFromJson(response.body().get(0));

                    executor.execute(() -> {
                        try {
                            userDao.insertUser(foundUser);
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка кэширования пользователя: "
                                    + e.getMessage(), e);
                        }
                        mainHandler.post(() -> callback.onSuccess(foundUser));
                    });

                } else if (response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError(
                            "Пользователь с таким ником не найден"));

                } else {
                    String errorMsg = getErrorBody(response);
                    Log.e(TAG, "Ошибка поиска: "
                            + response.code() + " | " + errorMsg);
                    mainHandler.post(() -> callback.onError(
                            "Ошибка сервера: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e(TAG, "Ошибка поиска пользователя: " + t.getMessage(), t);

                executor.execute(() -> {
                    try {
                        User cachedUser = userDao.getUserByUsername(cleanUsername);
                        if (cachedUser != null) {
                            mainHandler.post(() -> callback.onSuccess(cachedUser));
                        } else {
                            mainHandler.post(() -> callback.onError(
                                    "Нет сети. Пользователь не найден в кэше"));
                        }
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError(
                                "Ошибка сети при поиске пользователя"));
                    }
                });
            }
        });
    }

    public void isAlreadyFriend(int friendId,
                                User currentUser,
                                DataCallback<Boolean> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        int currentUserId = currentUser.getId();

        if (currentUserId == friendId) {
            callback.onSuccess(false);
            return;
        }

        executor.execute(() -> {
            try {
                List<User> localFriends = friendDao.getFriends(currentUserId);
                boolean foundLocally = false;

                if (localFriends != null) {
                    for (User f : localFriends) {
                        if (f != null && f.getId() != null && f.getId() == friendId) {
                            foundLocally = true;
                            break;
                        }
                    }
                }

                final boolean foundLocallyFinal = foundLocally;
                mainHandler.post(() -> callback.onSuccess(foundLocallyFinal));

            } catch (Exception e) {
                Log.e(TAG, "Ошибка проверки локального друга: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onSuccess(false));
            }
        });
    }

    public void resetState() {
        isFetchingFriends = false;
        Log.d(TAG, "resetState: флаги FriendsRepository сброшены");
    }
}