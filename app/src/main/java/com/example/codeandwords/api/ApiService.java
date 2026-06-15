package com.example.codeandwords.api;

import com.example.codeandwords.model.LeaderboardEntry;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserWord;
import com.example.codeandwords.model.UserWordProgress;
import com.example.codeandwords.model.Word;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

// Интерфейс Retrofit для взаимодействия с Supabase REST API
public interface ApiService {

    // Авторизация по email
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @GET("users")
    Call<List<JsonObject>> loginByEmail(@Query("email") String email);

    // Регистрация нового пользователя
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("users")
    Call<List<JsonObject>> register(@Body User user);

    // Обновление XP и уровня пользователя
    @PATCH("users")
    Call<Void> updateUserProgress(
            @Query("id") String idFilter,
            @Body JsonObject payload);

    // Обновление конфигурации аватара
    @PATCH("users")
    Call<Void> updateAvatarConfig(
            @Query("id") String idFilter,
            @Body JsonObject payload);

    // Получение списка тем
    @GET("themes")
    Call<List<Theme>> getThemes(
            @Query("select") String select,
            @Query("order") String order);

    // Получение слов по теме
    @GET("words")
    Call<List<Word>> getWordsByTheme(
            @Query("theme_id") String filter,
            @Query("select") String select,
            @Query("order") String order);

    @POST("words")
    Call<Word> addWord(@Body Word word);

    // Получение таблицы лидеров, отсортированной по недельному XP
    @GET("leaderboard?select=weekly_xp,user_id,users(username)&order=weekly_xp.desc")
    Call<List<LeaderboardEntry>> getLeaderboard();

    // Получение прогресса пользователя по словам
    @GET("user_word_progress")
    Call<List<UserWordProgress>> getUserProgress(@Query("user_id") String userFilter);

    // Получение ID изученных слов пользователя
    @GET("user_word_progress?select=word_id&is_learned=eq.true")
    Call<List<UserWordProgress>> getLearnedWordsIds(@Query("user_id") String userIdFilter);

    // Upsert статистики пользователя (вставка или обновление при конфликте)
    @Headers({
            "Prefer: resolution=merge-duplicates,return=representation",
            "Content-Type: application/json"
    })
    @POST("user_stats")
    Call<List<JsonObject>> upsertUserStatsRaw(
            @Query("on_conflict") String onConflict,
            @Body JsonObject payload);

    // Запись истории урока
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("lesson_history")
    Call<List<JsonObject>> insertLessonRaw(@Body JsonObject payload);

    // Получение записи достижения пользователя
    @GET("user_achievements")
    Call<List<JsonObject>> getUserAchievementRecordRaw(
            @Query("user_id") String userFilter,
            @Query("achievement_id") String achievementFilter);

    // Создание новой записи достижения
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("user_achievements")
    Call<List<JsonObject>> insertUserAchievementRaw(@Body JsonObject payload);

    // Обновление прогресса достижения
    @PATCH("user_achievements")
    Call<Void> updateUserAchievementRaw(
            @Query("user_id") String userFilter,
            @Query("achievement_id") String achievementFilter,
            @Body JsonObject payload);

    // Upsert достижения пользователя
    @Headers({
            "Prefer: resolution=merge-duplicates,return=minimal",
            "Content-Type: application/json"
    })
    @POST("user_achievements")
    Call<Void> upsertUserAchievementRaw(
            @Query("on_conflict") String onConflict,
            @Body JsonObject payload);

    // Запись расписания занятий
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("study_schedule")
    Call<List<JsonObject>> insertStudyScheduleRaw(@Body JsonObject payload);

    // Получение расписания на конкретную дату
    @GET("study_schedule")
    Call<List<JsonObject>> getStudyScheduleRaw(
            @Query("user_id") String userFilter,
            @Query("schedule_date") String dateFilter,
            @Query("order") String order);

    // Получение расписания за диапазон дат
    @GET("study_schedule")
    Call<List<JsonObject>> getStudyScheduleRangeRaw(
            @Query("user_id") String userFilter,
            @Query("schedule_date") String startDateFilter,
            @Query("schedule_date") String endDateFilter,
            @Query("order") String order);

    // Удаление записи расписания
    @DELETE("study_schedule")
    Call<Void> deleteStudyScheduleRaw(@Query("id") String idFilter);

    // Поиск пользователя по username
    @GET("users")
    Call<List<JsonObject>> findUserByUsername(
            @Query("username") String usernameFilter,
            @Query("select") String select,
            @Query("limit") int limit);

    // Добавление друга (upsert)
    @Headers({
            "Prefer: resolution=merge-duplicates,return=representation",
            "Content-Type: application/json"
    })
    @POST("user_friends")
    Call<List<JsonObject>> upsertFriendRaw(
            @Query("on_conflict") String onConflict,
            @Body JsonObject payload);

    // Получение списка друзей пользователя (односторонняя связь)
    @GET("user_friends")
    Call<List<JsonObject>> getFriendsRaw(
            @Query("user_id") String userFilter,
            @Query("select") String select);

    // Получение всех связей дружбы (двусторонняя выборка через OR)
    @GET("user_friends")
    Call<List<JsonObject>> getFriendsBothRaw(
            @Query("or") String orFilter,
            @Query("select") String select);

    // Создание команды
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("teams")
    Call<List<JsonObject>> insertTeamRaw(@Body JsonObject payload);

    // Получение команд, созданных пользователем
    @GET("teams")
    Call<List<JsonObject>> getOwnedTeamsRaw(
            @Query("owner_id") String ownerFilter,
            @Query("select") String select,
            @Query("order") String order);

    // Получение команд по списку ID
    @GET("teams")
    Call<List<JsonObject>> getTeamsByIdsRaw(
            @Query("id") String idsFilter,
            @Query("select") String select,
            @Query("order") String order);

    // Добавление участников команды с возвратом данных
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("team_members")
    Call<List<JsonObject>> insertTeamMembersRaw(@Body List<JsonObject> payload);

    // Добавление участников команды без возврата данных
    @Headers({
            "Prefer: return=minimal",
            "Content-Type: application/json"
    })
    @POST("team_members")
    Call<Void> insertTeamMembersMinimalRaw(@Body List<JsonObject> payload);

    // Получение членства пользователя в командах
    @GET("team_members")
    Call<List<JsonObject>> getMyTeamMembersRaw(
            @Query("user_id") String userFilter,
            @Query("select") String select);

    // Создание командного задания с возвратом данных
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("team_challenges")
    Call<List<JsonObject>> insertTeamChallengeRaw(@Body JsonObject payload);

    // Создание командного задания без возврата данных
    @Headers({
            "Prefer: return=minimal",
            "Content-Type: application/json"
    })
    @POST("team_challenges")
    Call<Void> insertTeamChallengeMinimalRaw(@Body JsonObject payload);

    // Получение заданий команды
    @GET("team_challenges")
    Call<List<JsonObject>> getTeamChallengeRaw(
            @Query("team_id") String teamFilter,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") int limit);

    // Запись прогресса по заданию с возвратом данных
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("team_challenge_progress")
    Call<List<JsonObject>> insertTeamChallengeProgressRaw(@Body List<JsonObject> payload);

    // Запись прогресса по заданию без возврата данных
    @Headers({
            "Prefer: return=minimal",
            "Content-Type: application/json"
    })
    @POST("team_challenge_progress")
    Call<Void> insertTeamChallengeProgressMinimalRaw(@Body List<JsonObject> payload);

    // Получение прогресса команды по заданию
    @GET("team_challenge_progress")
    Call<List<JsonObject>> getTeamProgressRaw(
            @Query("challenge_id") String challengeFilter,
            @Query("select") String select,
            @Query("order") String order);

    // Получение активного прогресса пользователя по командным заданиям
    @GET("team_challenge_progress")
    Call<List<JsonObject>> getMyActiveTeamProgressRaw(
            @Query("user_id") String userFilter,
            @Query("is_completed") String completedFilter,
            @Query("select") String select);

    // Получение завершённых записей прогресса по заданию
    @GET("team_challenge_progress")
    Call<List<JsonObject>> getCompletedTeamProgressRaw(
            @Query("challenge_id") String challengeFilter,
            @Query("is_completed") String completedFilter,
            @Query("order") String order);

    // Обновление прогресса по заданию
    @PATCH("team_challenge_progress")
    Call<Void> updateTeamChallengeProgressRaw(
            @Query("id") String idFilter,
            @Body JsonObject payload);

    // Обновление статуса командного задания
    @PATCH("team_challenges")
    Call<Void> updateTeamChallengeRaw(
            @Query("id") String idFilter,
            @Body JsonObject payload);

    // Получение прогресса по ID записи
    @GET("team_challenge_progress")
    Call<List<JsonObject>> getTeamProgressByIdRaw(
            @Query("id") String idFilter,
            @Query("select") String select,
            @Query("limit") int limit);

    // Административное создание темы
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("themes")
    Call<List<Theme>> adminCreateTheme(@Body Theme theme);

    // Административное создание слова
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("words")
    Call<List<Word>> adminCreateWord(@Body Word word);

    // Административное обновление темы
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @PATCH("themes")
    Call<List<Theme>> adminUpdateTheme(
            @Query("id") String idFilter,
            @Body Theme theme);

    // Административное обновление слова
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @PATCH("words")
    Call<List<Word>> adminUpdateWord(
            @Query("id") String idFilter,
            @Body Word word);

    // Административное удаление темы
    @DELETE("themes")
    Call<Void> adminDeleteTheme(@Query("id") String idFilter);

    // Административное удаление слова
    @DELETE("words")
    Call<Void> adminDeleteWord(@Query("id") String idFilter);

    // Получение прогресса пользователя с фильтрацией и лимитом
    @GET("user_word_progress")
    Call<List<UserWordProgress>> getUserProgressByUser(
            @Query("user_id") String userFilter,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") Integer limit);

    // Получение прогресса по конкретному слову пользователя
    @GET("user_word_progress")
    Call<List<UserWordProgress>> getUserWordProgress(
            @Query("user_id") String userFilter,
            @Query("word_id") String wordFilter,
            @Query("select") String select);

    // Создание записи прогресса по слову
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("user_word_progress")
    Call<List<UserWordProgress>> createWordProgress(@Body UserWordProgress progress);

    // Обновление записи прогресса по слову
    @PATCH("user_word_progress")
    Call<Void> updateWordProgress(
            @Query("id") String idFilter,
            @Body UserWordProgress progress);

    // Получение изученных слов пользователя
    @GET("user_word_progress")
    Call<List<UserWordProgress>> getLearnedProgress(
            @Query("user_id") String userFilter,
            @Query("is_learned") String learnedFilter,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") Integer limit
    );

    // Получение слов с ошибками пользователя
    @GET("user_word_progress")
    Call<List<UserWordProgress>> getMistakeProgress(
            @Query("user_id") String userFilter,
            @Query("mistakes_count") String mistakesFilter,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") Integer limit
    );

    // Получение слов по списку ID
    @GET("words")
    Call<List<Word>> getWordsByIds(
            @Query("id") String idsFilter,
            @Query("select") String select,
            @Query("order") String order);

    // Массовое создание слов администратором
    @POST("words")
    Call<List<Word>> adminCreateWords(@Body List<Word> words);

    // Удаление прогресса по нескольким словам (пакетное)
    @DELETE("user_word_progress")
    Call<Void> adminDeleteProgressByWordIds(@Query("word_id") String wordIdsFilter);

    // Удаление прогресса по одному слову
    @DELETE("user_word_progress")
    Call<Void> adminDeleteProgressByWordId(@Query("word_id") String wordIdFilter);

    // Удаление всех слов темы
    @DELETE("words")
    Call<Void> adminDeleteWordsByThemeId(@Query("theme_id") String themeIdFilter);

    // Обновление профиля пользователя
    @PATCH("users")
    Call<Void> updateUserProfileRaw(
            @Query("id") String userIdFilter,
            @Body JsonObject payload);

    // Получение личных слов пользователя
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @GET("user_personal_words")
    Call<List<JsonObject>> getUserPersonalWordsFromServer(
            @Query("user_id") String userId,
            @Query("order") String order);

    // Добавление слова в личный словарь без риска конфликта ключей
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("user_personal_words")
    Call<List<JsonObject>> insertUserPersonalWordRaw(@Body JsonObject payload);

    // Upsert личного слова пользователя
    @Headers({
            "Prefer: resolution=merge-duplicates,return=representation",
            "Content-Type: application/json"
    })
    @POST("user_personal_words")
    Call<List<UserWord>> upsertUserPersonalWord(
            @Query("on_conflict") String onConflictColumns,
            @Body UserWord userWord);

    // Удаление слова из личного словаря на сервере
    @Headers({
            "Prefer: return=minimal",
            "Content-Type: application/json"
    })
    @DELETE("user_personal_words")
    Call<Void> deleteUserPersonalWordFromServer(
            @Query("user_id") String userId,
            @Query("id") String serverId);

    // Удаление команды
    @DELETE("teams")
    Call<Void> deleteTeamRaw(@Query("id") String idFilter);

    // Получение достижений пользователя
    @GET("user_achievements")
    Call<List<JsonObject>> getUserAchievementsByUserRaw(
            @Query("user_id") String userIdFilter,
            @Query("select") String select
    );

    // Пакетный upsert достижений пользователя
    @Headers({
            "Content-Type: application/json",
            "Prefer: resolution=merge-duplicates"
    })
    @POST("user_achievements")
    Call<Void> upsertUserAchievementsBatchRaw(
            @Query("on_conflict") String onConflict,
            @Body JsonArray batch
    );
}