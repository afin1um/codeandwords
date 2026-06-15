package com.example.codeandwords.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.UserDao;
import com.example.codeandwords.model.User;
import com.example.codeandwords.ui.profile.AvatarConfig;
import com.example.codeandwords.ui.profile.AvatarPrefs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Репозиторий авторизации: вход, регистрация, управление сессией и данными профиля
public class AuthRepository {

    // Ключи SharedPreferences для хранения данных текущего пользователя
    private static final String PREFS_NAME = "codeandwords_prefs";
    private static final String KEY_USER_ID = "current_user_id";
    private static final String KEY_USERNAME = "current_username";
    private static final String KEY_EMAIL = "current_email";
    private static final String KEY_PASSWORD_HASH = "current_password_hash";
    private static final String KEY_CURRENT_LEVEL = "current_level";
    private static final String KEY_TOTAL_XP = "total_xp";
    private static final String KEY_ROLE = "role";
    private static final String KEY_AVATAR_CONFIG = "avatar_config";

    private final SharedPreferences prefs;
    private final UserDao userDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final Context appContext;

    // Текущий пользователь хранится статически для доступа из любого репозитория
    private static User currentUser;

    private AuthListener listener;

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    public interface OnUserIdRetrieved {
        void onRetrieved(Integer userId);
    }

    public interface AuthListener {
        void recordLoginEvent();
        String getErrorBody(Response<?> response);
    }

    public void setListener(AuthListener listener) {
        this.listener = listener;
    }

    public AuthRepository(Context context,
                          UserDao userDao,
                          ApiService apiService,
                          ExecutorService executor,
                          Handler mainHandler) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.userDao = userDao;
        this.apiService = apiService;
        this.executor = executor;
        this.mainHandler = mainHandler;

        restoreCurrentUserFromPrefs();
    }

    // Возвращает текущего пользователя; при необходимости восстанавливает из SharedPreferences
    public User getCurrentUserSync() {
        if (currentUser == null) restoreCurrentUserFromPrefs();
        return currentUser;
    }

    public void setCurrentUser(User user) {
        currentUser = user;
    }

    public void setCurrentUserAndSave(User user) {
        currentUser = user;
        saveCurrentUserToPrefs(user);
    }

    public void restoreCurrentUserFromPrefs() {
        restoreCurrentUserFromPrefs(false);
    }

    // Восстанавливает данные пользователя из SharedPreferences; force=true игнорирует кэш
    public void restoreCurrentUserFromPrefs(boolean force) {
        if (!force && currentUser != null) return;

        int savedUserId = prefs.getInt(KEY_USER_ID, -1);
        if (savedUserId == -1) {
            currentUser = null;
            return;
        }

        User restoredUser = new User();
        restoredUser.setId(savedUserId);
        restoredUser.setUsername(prefs.getString(KEY_USERNAME, ""));
        restoredUser.setEmail(prefs.getString(KEY_EMAIL, ""));
        restoredUser.setPasswordHash(prefs.getString(KEY_PASSWORD_HASH, ""));
        restoredUser.setCurrentLevel(prefs.getInt(KEY_CURRENT_LEVEL, 1));
        restoredUser.setTotalXp(prefs.getInt(KEY_TOTAL_XP, 0));
        restoredUser.setRole(prefs.getString(KEY_ROLE, "user"));
        restoredUser.setAvatarConfig(prefs.getString(KEY_AVATAR_CONFIG, null));

        currentUser = restoredUser;
    }

    // Сохраняет данные пользователя в SharedPreferences и обновляет статическую ссылку
    public void saveCurrentUserToPrefs(User user) {
        if (user == null || user.getId() == null) return;

        currentUser = user;

        prefs.edit()
                .putInt(KEY_USER_ID, user.getId())
                .putString(KEY_USERNAME, user.getUsername())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_PASSWORD_HASH, user.getPasswordHash())
                .putInt(KEY_CURRENT_LEVEL, user.getCurrentLevel() != null ? user.getCurrentLevel() : 1)
                .putInt(KEY_TOTAL_XP, user.getTotalXp() != null ? user.getTotalXp() : 0)
                .putString(KEY_ROLE, user.getRole() != null ? user.getRole() : "user")
                .putString(KEY_AVATAR_CONFIG, user.getAvatarConfig())
                .apply();
    }

    // Очищает данные сессии при выходе из аккаунта
    public void clearCurrentUserPrefs() {
        prefs.edit().clear().apply();
        currentUser = null;
    }

    // Асинхронно кэширует пользователя в локальной БД
    public void cacheUserSafely(User user) {
        if (user == null) return;
        executor.execute(() -> {
            try {
                userDao.insertUser(user);
            } catch (Exception e) {
                Log.e("AuthRepository", "Ошибка кэширования пользователя: " + e.getMessage(), e);
            }
        });
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

    public void logout(Runnable callback) {
        currentUser = null;
        clearCurrentUserPrefs();
        mainHandler.post(callback);
    }

    // Хеширует пароль алгоритмом SHA-256; при ошибке возвращает исходную строку
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
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

    private String getErrorBody(Response<?> response) {
        if (listener != null) return listener.getErrorBody(response);
        try {
            if (response.errorBody() != null) return response.errorBody().string();
        } catch (Exception e) {
            Log.e("AuthRepository", "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    // Выполняет вход: запрашивает пользователя по email и сверяет хеш пароля
    public void login(User user, DataCallback<User> callback) {
        if (user == null) {
            callback.onError("Введите данные для входа");
            return;
        }

        String cleanEmail = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase();
        String rawPassword = user.getPasswordHash() == null ? "" : user.getPasswordHash().trim();

        if (cleanEmail.isEmpty() || rawPassword.isEmpty()) {
            callback.onError("Введите email и пароль");
            return;
        }

        // Если длина 64 — уже хеш SHA-256, повторное хеширование не нужно
        String hashedPassword = rawPassword.length() == 64 ? rawPassword : hashPassword(rawPassword);

        apiService.loginByEmail("eq." + cleanEmail).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (!response.isSuccessful()
                        || response.body() == null
                        || response.body().isEmpty()) {
                    mainHandler.post(() -> callback.onError("Пользователь с таким email не найден"));
                    return;
                }

                User serverUser = parseUserFromJson(response.body().get(0));
                String serverPassword = serverUser.getPasswordHash() == null
                        ? "" : serverUser.getPasswordHash().trim();

                boolean passwordMatches = hashedPassword.equals(serverPassword)
                        || rawPassword.equals(serverPassword);

                if (!passwordMatches) {
                    Log.e("AuthRepository", "Пароль не совпал: serverPassword=" + serverPassword);
                    mainHandler.post(() -> callback.onError("Неверный пароль"));
                    return;
                }

                currentUser = serverUser;

                // Синхронизируем аватар из ответа сервера с локальными настройками
                if (currentUser.getAvatarConfig() != null
                        && !currentUser.getAvatarConfig().trim().isEmpty()
                        && !currentUser.getAvatarConfig().equals("null")) {
                    AvatarConfig serverAvatar = AvatarConfig.fromJson(currentUser.getAvatarConfig());
                    AvatarPrefs.save(appContext, serverAvatar);
                }

                saveCurrentUserToPrefs(currentUser);
                cacheUserSafely(currentUser);

                if (listener != null) listener.recordLoginEvent();

                mainHandler.post(() -> callback.onSuccess(currentUser));
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("AuthRepository", "Ошибка сети login: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
            }
        });
    }

    // Выполняет регистрацию: проверяет уникальность email, затем создаёт аккаунт
    public void register(User user, DataCallback<User> callback) {
        if (user == null) {
            callback.onError("Введите данные для регистрации");
            return;
        }

        String rawPassword = user.getPasswordHash() == null ? "" : user.getPasswordHash().trim();
        String cleanEmail = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase();

        if (cleanEmail.isEmpty() || rawPassword.isEmpty()) {
            callback.onError("Введите email и пароль");
            return;
        }

        String hashedPassword = rawPassword.length() == 64 ? rawPassword : hashPassword(rawPassword);

        user.setEmail(cleanEmail);
        user.setPasswordHash(hashedPassword);

        // Проверяем, не занят ли email, прежде чем регистрировать
        apiService.loginByEmail("eq." + cleanEmail).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {
                    mainHandler.post(() -> callback.onError(
                            "Пользователь с таким Email уже существует"));
                } else {
                    performActualRegistration(user, callback);
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("AuthRepository", "Ошибка проверки почты: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка проверки почты: " + t.getMessage()));
            }
        });
    }

    // Отправляет запрос на создание аккаунта после прохождения проверки email
    private void performActualRegistration(User user, DataCallback<User> callback) {
        apiService.register(user).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {
                    User regUser = parseUserFromJson(response.body().get(0));
                    currentUser = regUser;
                    saveCurrentUserToPrefs(regUser);
                    cacheUserSafely(regUser);
                    if (listener != null) listener.recordLoginEvent();
                    mainHandler.post(() -> callback.onSuccess(regUser));
                } else if (response.code() == 409) {
                    mainHandler.post(() -> callback.onError("Этот аккаунт уже зарегистрирован"));
                } else {
                    Log.e("AuthRepository", "Ошибка регистрации: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError("Ошибка сервера: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("AuthRepository", "Сбой сети register: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Сбой сети: " + t.getMessage()));
            }
        });
    }

    // Обновляет имя пользователя на сервере и в локальной БД
    public void updateUsername(String username, User currentUserRef, DataCallback<User> callback) {
        if (currentUserRef == null || currentUserRef.getId() == null) {
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

        apiService.updateUserProfileRaw("eq." + currentUserRef.getId(), payload)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            currentUserRef.setUsername(safeUsername);
                            currentUser = currentUserRef;
                            saveCurrentUserToPrefs(currentUserRef);
                            executor.execute(() -> {
                                try {
                                    userDao.insertUser(currentUserRef);
                                } catch (Exception e) {
                                    Log.e("AuthRepository",
                                            "Ошибка локального обновления username: "
                                                    + e.getMessage(), e);
                                }
                                mainHandler.post(() -> callback.onSuccess(currentUserRef));
                            });
                        } else {
                            Log.e("AuthRepository", "Ошибка обновления username: "
                                    + response.code() + " | " + getErrorBody(response));
                            mainHandler.post(() -> callback.onError("Не удалось обновить профиль"));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e("AuthRepository", "Ошибка сети updateUsername: " + t.getMessage(), t);
                        mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
                    }
                });
    }

    // Сохраняет аватар локально немедленно, затем асинхронно синхронизирует с сервером
    public void updateAvatarConfig(AvatarConfig avatarConfig,
                                   User currentUserRef,
                                   DataCallback<Void> callback) {
        if (currentUserRef == null || currentUserRef.getId() == null) {
            callback.onError("Пользователь не авторизован");
            return;
        }

        if (avatarConfig == null) {
            callback.onError("Аватар не заполнен");
            return;
        }

        String json = avatarConfig.toJson();

        JsonObject payload = new JsonObject();
        try {
            payload.add("avatar_config", JsonParser.parseString(json));
        } catch (Exception e) {
            Log.e("AuthRepository", "Ошибка подготовки avatar_config JSON: " + e.getMessage(), e);
            callback.onError("Не удалось подготовить аватар");
            return;
        }

        // Мгновенное локальное сохранение для отзывчивого UI
        currentUserRef.setAvatarConfig(json);
        currentUser = currentUserRef;
        saveCurrentUserToPrefs(currentUserRef);
        AvatarPrefs.save(appContext, avatarConfig);

        executor.execute(() -> {
            try {
                userDao.insertUser(currentUserRef);
            } catch (Exception e) {
                Log.e("AuthRepository", "Ошибка локального сохранения avatar_config: "
                        + e.getMessage(), e);
            }
        });

        // UI уведомляется сразу, не дожидаясь ответа сервера
        mainHandler.post(() -> callback.onSuccess(null));

        // Фоновая синхронизация с одним повтором при неудаче
        syncAvatarConfigToRemote(currentUserRef.getId(), payload, 0);
    }

    private void syncAvatarConfigToRemote(Integer userId, JsonObject payload, int attempt) {
        if (userId == null || payload == null) return;

        apiService.updateAvatarConfig("eq." + userId, payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("AuthRepository", "avatar_config успешно синхронизирован с сервером");
                    return;
                }
                Log.e("AuthRepository", "Ошибка обновления avatar_config: "
                        + response.code() + " | " + getErrorBody(response));
                if (attempt < 1) {
                    mainHandler.postDelayed(
                            () -> syncAvatarConfigToRemote(userId, payload, attempt + 1), 1500);
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("AuthRepository", "Ошибка сети updateAvatarConfig: " + t.getMessage(), t);
                if (attempt < 1) {
                    mainHandler.postDelayed(
                            () -> syncAvatarConfigToRemote(userId, payload, attempt + 1), 1500);
                }
            }
        });
    }
}