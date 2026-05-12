package com.example.codeandwords.data.social;

import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.FriendDao;
import com.example.codeandwords.db.UserDao;
import com.example.codeandwords.model.Friend;
import com.example.codeandwords.model.User;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FriendsRepository {

    private final FriendDao friendDao;
    private final UserDao userDao;
    private final ApiService apiService;
    private final ApiService fastApiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public FriendsRepository(FriendDao friendDao,
                             UserDao userDao,
                             ApiService apiService,
                             ApiService fastApiService,
                             ExecutorService executor,
                             Handler mainHandler) {
        this.friendDao = friendDao;
        this.userDao = userDao;
        this.apiService = apiService;
        this.fastApiService = fastApiService;
        this.executor = executor;
        this.mainHandler = mainHandler;
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
            Log.e("FriendsRepository",
                    "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // ===== ОСНОВНЫЕ МЕТОДЫ =====

    public void addFriend(int friendId,
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

        apiService.upsertFriendRaw("user_id,friend_id", payload)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call,
                                           Response<List<JsonObject>> response) {
                        if (response.isSuccessful()) {
                            executor.execute(() -> {
                                try {
                                    friendDao.insert(new Friend(currentUserId, friendId));
                                } catch (Exception e) {
                                    Log.e("FriendsRepository",
                                            "Ошибка локального сохранения друга: "
                                                    + e.getMessage(), e);
                                }

                                mainHandler.post(() -> callback.onSuccess(null));
                            });
                        } else {
                            Log.e("FriendsRepository",
                                    "Ошибка добавления друга: "
                                            + response.code() + " | "
                                            + getErrorBody(response));
                            mainHandler.post(() -> callback.onError(
                                    "Не удалось добавить друга"));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        Log.e("FriendsRepository",
                                "Ошибка сети addFriend: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError(
                                "Ошибка сети при добавлении друга"));
                    }
                });
    }

    public void getFriends(User currentUser,
                           DataCallback<List<User>> callback) {
        if (currentUser == null || currentUser.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        int currentUserId = currentUser.getId();

        // Сначала отдаём локальные данные
        executor.execute(() -> {
            try {
                List<User> localFriends = friendDao.getFriends(currentUserId);

                if (localFriends != null && !localFriends.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(localFriends));
                }
            } catch (Exception e) {
                Log.e("FriendsRepository",
                        "Ошибка локальной загрузки друзей: " + e.getMessage(), e);
            }
        });

        // Затем загружаем с сервера
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
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        try {
                            friendDao.deleteFriendsByUser(currentUserId);

                            List<User> friends = new ArrayList<>();
                            List<Integer> addedIds = new ArrayList<>();

                            for (JsonObject item : response.body()) {
                                if (item == null) continue;

                                int userId = item.has("user_id")
                                        && !item.get("user_id").isJsonNull()
                                        ? item.get("user_id").getAsInt() : -1;

                                int friendId = item.has("friend_id")
                                        && !item.get("friend_id").isJsonNull()
                                        ? item.get("friend_id").getAsInt() : -1;

                                JsonObject targetUserJson = null;
                                int targetUserId = -1;

                                if (userId == currentUserId) {
                                    targetUserId = friendId;
                                    if (item.has("friend")
                                            && item.get("friend").isJsonObject()) {
                                        targetUserJson =
                                                item.getAsJsonObject("friend");
                                    }
                                } else if (friendId == currentUserId) {
                                    targetUserId = userId;
                                    if (item.has("user")
                                            && item.get("user").isJsonObject()) {
                                        targetUserJson =
                                                item.getAsJsonObject("user");
                                    }
                                }

                                if (targetUserId <= 0
                                        || targetUserJson == null) continue;

                                if (addedIds.contains(targetUserId)) continue;

                                User friendUser =
                                        parseUserFromJson(targetUserJson);

                                if (friendUser.getId() == null) {
                                    friendUser.setId(targetUserId);
                                }

                                addedIds.add(friendUser.getId());

                                userDao.insertUser(friendUser);
                                friendDao.insert(new Friend(
                                        currentUserId, friendUser.getId()));

                                friends.add(friendUser);
                            }

                            mainHandler.post(() -> callback.onSuccess(friends));

                        } catch (Exception e) {
                            Log.e("FriendsRepository",
                                    "Ошибка обработки друзей: "
                                            + e.getMessage(), e);
                            mainHandler.post(() -> callback.onError(
                                    "Не удалось обработать список друзей"));
                        }
                    });
                } else {
                    Log.e("FriendsRepository",
                            "Ошибка загрузки друзей: "
                                    + response.code() + " | "
                                    + getErrorBody(response));

                    executor.execute(() -> {
                        try {
                            List<User> localFriends =
                                    friendDao.getFriends(currentUserId);
                            mainHandler.post(() ->
                                    callback.onSuccess(localFriends));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError(
                                    "Не удалось загрузить друзей"));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("FriendsRepository",
                        "Ошибка сети getFriends: " + t.getMessage(), t);

                executor.execute(() -> {
                    try {
                        List<User> localFriends =
                                friendDao.getFriends(currentUserId);
                        mainHandler.post(() ->
                                callback.onSuccess(localFriends));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError(
                                "Ошибка загрузки друзей"));
                    }
                });
            }
        });
    }

    // СТАЛО:
    public void findUserByUsername(String username,
                                   DataCallback<User> callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onError("Введите ник пользователя");
            return;
        }

        String cleanUsername = username.trim();

        // Сначала пробуем найти локально в кэше
        executor.execute(() -> {
            try {
                User cachedUser = userDao.getUserByUsername(cleanUsername);
                if (cachedUser != null) {
                    mainHandler.post(() -> callback.onSuccess(cachedUser));
                }
            } catch (Exception e) {
                Log.e("FriendsRepository",
                        "Ошибка локального поиска: " + e.getMessage(), e);
            }
        });

        // Правильный запрос к Supabase:
        // GET /users?username=eq.Alex&select=id,username,email,...&limit=1
        // Используем fastApiService для ускорения
        fastApiService.findUserByUsername(
                "eq." + cleanUsername,                          // ← фильтр PostgREST
                "id,username,email,current_level,total_xp,role,created_at,avatar_config,gender",
                1                                               // ← лимит 1 запись
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
                            Log.e("FriendsRepository",
                                    "Ошибка кэширования пользователя: "
                                            + e.getMessage(), e);
                        }
                        mainHandler.post(() -> callback.onSuccess(foundUser));
                    });

                } else if (response.isSuccessful()) {
                    // Запрос прошёл успешно, но пользователь не найден
                    mainHandler.post(() -> callback.onError(
                            "Пользователь с таким ником не найден"));

                } else {
                    // HTTP ошибка
                    String errorMsg = getErrorBody(response);
                    Log.e("FriendsRepository",
                            "Ошибка поиска: " + response.code() + " | " + errorMsg);
                    mainHandler.post(() -> callback.onError(
                            "Ошибка сервера: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("FriendsRepository",
                        "Ошибка поиска пользователя: " + t.getMessage(), t);

                // Пробуем fallback из локального кэша
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

    // ===== ИНТЕРФЕЙС CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}