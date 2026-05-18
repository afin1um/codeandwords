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

public interface ApiService {

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @GET("users")
    Call<List<JsonObject>> loginByEmail(@Query("email") String email);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("users")
    Call<List<JsonObject>> register(@Body User user);

    @PATCH("users")
    Call<Void> updateUserProgress(
            @Query("id") String idFilter,
            @Body JsonObject payload);

    @PATCH("users")
    Call<Void> updateAvatarConfig(
            @Query("id") String idFilter,
            @Body JsonObject payload);

    @GET("themes")
    Call<List<Theme>> getThemes(
            @Query("select") String select,
            @Query("order") String order);

    @GET("words")
    Call<List<Word>> getWordsByTheme(
            @Query("theme_id") String filter,
            @Query("select") String select,
            @Query("order") String order);

    @POST("words")
    Call<Word> addWord(@Body Word word);

    @GET("leaderboard?select=weekly_xp,user_id,users(username)&order=weekly_xp.desc")
    Call<List<LeaderboardEntry>> getLeaderboard();

    @GET("user_word_progress")
    Call<List<UserWordProgress>> getUserProgress(@Query("user_id") String userFilter);

    @GET("user_word_progress?select=word_id&is_learned=eq.true")
    Call<List<UserWordProgress>> getLearnedWordsIds(@Query("user_id") String userIdFilter);

    @Headers({
            "Prefer: resolution=merge-duplicates,return=representation",
            "Content-Type: application/json"
    })
    @POST("user_stats")
    Call<List<JsonObject>> upsertUserStatsRaw(
            @Query("on_conflict") String onConflict,
            @Body JsonObject payload);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("lesson_history")
    Call<List<JsonObject>> insertLessonRaw(@Body JsonObject payload);

    @GET("user_achievements")
    Call<List<JsonObject>> getUserAchievementRecordRaw(
            @Query("user_id") String userFilter,
            @Query("achievement_id") String achievementFilter);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("user_achievements")
    Call<List<JsonObject>> insertUserAchievementRaw(@Body JsonObject payload);

    @PATCH("user_achievements")
    Call<Void> updateUserAchievementRaw(
            @Query("user_id") String userFilter,
            @Query("achievement_id") String achievementFilter,
            @Body JsonObject payload);

    @Headers({
            "Prefer: resolution=merge-duplicates,return=minimal",
            "Content-Type: application/json"
    })
    @POST("user_achievements")
    Call<Void> upsertUserAchievementRaw(
            @Query("on_conflict") String onConflict,
            @Body JsonObject payload);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("study_schedule")
    Call<List<JsonObject>> insertStudyScheduleRaw(@Body JsonObject payload);

    @GET("study_schedule")
    Call<List<JsonObject>> getStudyScheduleRaw(
            @Query("user_id") String userFilter,
            @Query("schedule_date") String dateFilter,
            @Query("order") String order);

    @GET("study_schedule")
    Call<List<JsonObject>> getStudyScheduleRangeRaw(
            @Query("user_id") String userFilter,
            @Query("schedule_date") String startDateFilter,
            @Query("schedule_date") String endDateFilter,
            @Query("order") String order);

    @DELETE("study_schedule")
    Call<Void> deleteStudyScheduleRaw(@Query("id") String idFilter);

    @GET("users")
    Call<List<JsonObject>> findUserByUsername(
            @Query("username") String usernameFilter,
            @Query("select") String select,
            @Query("limit") int limit);

    @Headers({
            "Prefer: resolution=merge-duplicates,return=representation",
            "Content-Type: application/json"
    })
    @POST("user_friends")
    Call<List<JsonObject>> upsertFriendRaw(
            @Query("on_conflict") String onConflict,
            @Body JsonObject payload);

    @GET("user_friends")
    Call<List<JsonObject>> getFriendsRaw(
            @Query("user_id") String userFilter,
            @Query("select") String select);

    @GET("user_friends")
    Call<List<JsonObject>> getFriendsBothRaw(
            @Query("or") String orFilter,
            @Query("select") String select);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("teams")
    Call<List<JsonObject>> insertTeamRaw(@Body JsonObject payload);

    @GET("teams")
    Call<List<JsonObject>> getOwnedTeamsRaw(
            @Query("owner_id") String ownerFilter,
            @Query("select") String select,
            @Query("order") String order);

    @GET("teams")
    Call<List<JsonObject>> getTeamsByIdsRaw(
            @Query("id") String idsFilter,
            @Query("select") String select,
            @Query("order") String order);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("team_members")
    Call<List<JsonObject>> insertTeamMembersRaw(@Body List<JsonObject> payload);

    @Headers({
            "Prefer: return=minimal",
            "Content-Type: application/json"
    })
    @POST("team_members")
    Call<Void> insertTeamMembersMinimalRaw(@Body List<JsonObject> payload);

    @GET("team_members")
    Call<List<JsonObject>> getMyTeamMembersRaw(
            @Query("user_id") String userFilter,
            @Query("select") String select);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("team_challenges")
    Call<List<JsonObject>> insertTeamChallengeRaw(@Body JsonObject payload);

    @Headers({
            "Prefer: return=minimal",
            "Content-Type: application/json"
    })
    @POST("team_challenges")
    Call<Void> insertTeamChallengeMinimalRaw(@Body JsonObject payload);

    // Единственный метод getTeamChallengeRaw — с select (4 параметра)
    @GET("team_challenges")
    Call<List<JsonObject>> getTeamChallengeRaw(
            @Query("team_id") String teamFilter,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") int limit);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("team_challenge_progress")
    Call<List<JsonObject>> insertTeamChallengeProgressRaw(@Body List<JsonObject> payload);

    @Headers({
            "Prefer: return=minimal",
            "Content-Type: application/json"
    })
    @POST("team_challenge_progress")
    Call<Void> insertTeamChallengeProgressMinimalRaw(@Body List<JsonObject> payload);

    @GET("team_challenge_progress")
    Call<List<JsonObject>> getTeamProgressRaw(
            @Query("challenge_id") String challengeFilter,
            @Query("select") String select,
            @Query("order") String order);

    @GET("team_challenge_progress")
    Call<List<JsonObject>> getMyActiveTeamProgressRaw(
            @Query("user_id") String userFilter,
            @Query("is_completed") String completedFilter,
            @Query("select") String select);

    @GET("team_challenge_progress")
    Call<List<JsonObject>> getCompletedTeamProgressRaw(
            @Query("challenge_id") String challengeFilter,
            @Query("is_completed") String completedFilter,
            @Query("order") String order);

    @PATCH("team_challenge_progress")
    Call<Void> updateTeamChallengeProgressRaw(
            @Query("id") String idFilter,
            @Body JsonObject payload);

    @PATCH("team_challenges")
    Call<Void> updateTeamChallengeRaw(
            @Query("id") String idFilter,
            @Body JsonObject payload);

    @GET("team_challenge_progress")
    Call<List<JsonObject>> getTeamProgressByIdRaw(
            @Query("id") String idFilter,
            @Query("select") String select,
            @Query("limit") int limit);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("themes")
    Call<List<Theme>> adminCreateTheme(@Body Theme theme);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("words")
    Call<List<Word>> adminCreateWord(@Body Word word);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @PATCH("themes")
    Call<List<Theme>> adminUpdateTheme(
            @Query("id") String idFilter,
            @Body Theme theme);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @PATCH("words")
    Call<List<Word>> adminUpdateWord(
            @Query("id") String idFilter,
            @Body Word word);

    @DELETE("themes")
    Call<Void> adminDeleteTheme(@Query("id") String idFilter);

    @DELETE("words")
    Call<Void> adminDeleteWord(@Query("id") String idFilter);

    @GET("user_word_progress")
    Call<List<UserWordProgress>> getUserProgressByUser(
            @Query("user_id") String userFilter,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") Integer limit);

    @GET("user_word_progress")
    Call<List<UserWordProgress>> getUserWordProgress(
            @Query("user_id") String userFilter,
            @Query("word_id") String wordFilter,
            @Query("select") String select);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("user_word_progress")
    Call<List<UserWordProgress>> createWordProgress(@Body UserWordProgress progress);

    @PATCH("user_word_progress")
    Call<Void> updateWordProgress(
            @Query("id") String idFilter,
            @Body UserWordProgress progress);

    @GET("user_word_progress")
    Call<List<UserWordProgress>> getLearnedProgress(
            @Query("user_id") String userFilter,
            @Query("is_learned") String learnedFilter,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") Integer limit
    );

    @GET("user_word_progress")
    Call<List<UserWordProgress>> getMistakeProgress(
            @Query("user_id") String userFilter,
            @Query("mistakes_count") String mistakesFilter,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") Integer limit
    );

    @GET("words")
    Call<List<Word>> getWordsByIds(
            @Query("id") String idsFilter,
            @Query("select") String select,
            @Query("order") String order);

    @POST("words")
    Call<List<Word>> adminCreateWords(@Body List<Word> words);

    @DELETE("user_word_progress")
    Call<Void> adminDeleteProgressByWordIds(@Query("word_id") String wordIdsFilter);

    @DELETE("user_word_progress")
    Call<Void> adminDeleteProgressByWordId(@Query("word_id") String wordIdFilter);

    @DELETE("words")
    Call<Void> adminDeleteWordsByThemeId(@Query("theme_id") String themeIdFilter);

    @PATCH("users")
    Call<Void> updateUserProfileRaw(
            @Query("id") String userIdFilter,
            @Body JsonObject payload);

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @GET("user_personal_words")
    Call<List<UserWord>> getUserPersonalWordsFromServer(
            @Query("user_id") String userId,
            @Query("order") String order);

    @Headers({
            "Prefer: resolution=merge-duplicates,return=representation",
            "Content-Type: application/json"
    })
    @POST("user_personal_words")
    Call<List<UserWord>> upsertUserPersonalWord(
            @Query("on_conflict") String onConflictColumns,
            @Body UserWord userWord);

    @Headers({
            "Prefer: return=minimal",
            "Content-Type: application/json"
    })
    @DELETE("user_personal_words")
    Call<Void> deleteUserPersonalWordFromServer(
            @Query("user_id") String userId,
            @Query("id") String serverId);

    // ДОБАВЛЕНО: удаление команды по id
    @DELETE("teams")
    Call<Void> deleteTeamRaw(@Query("id") String idFilter);

    /**
     * Получить ВСЕ ачивки пользователя одним запросом (вместо проверки каждой)
     */
    @GET("user_achievements")
    Call<List<JsonObject>> getUserAchievementsByUserRaw(
            @Query("user_id") String userIdFilter,
            @Query("select") String select
    );

    /**
     * Батчевый upsert ачивок одним запросом
     */
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