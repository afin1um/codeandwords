package com.example.codeandwords.api;

import com.example.codeandwords.model.LeaderboardEntry;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserWordProgress;
import com.example.codeandwords.model.Word;
import com.google.gson.JsonObject;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
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
    Call<List<User>> login(
            @Query("email") String email,
            @Query("password_hash") String passwordHash
    );

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("users")
    Call<List<User>> register(@Body User user);

    @PATCH("users")
    Call<Void> updateUserProgress(
            @Query("id") String idFilter,
            @Body User user
    );

    @GET("themes?select=*")
    Call<List<Theme>> getThemes();

    @GET("words")
    Call<List<Word>> getWordsByTheme(@Query("theme_id") String filter);

    @POST("words")
    Call<Word> addWord(@Body Word word);

    @GET("leaderboard?select=weekly_xp,user_id,users(username)&order=weekly_xp.desc")
    Call<List<LeaderboardEntry>> getLeaderboard();

    @GET("user_word_progress")
    Call<List<UserWordProgress>> getUserProgress(@Query("user_id") String userFilter);

    @PATCH("user_word_progress")
    Call<Void> updateWordProgress(@Query("id") String idFilter, @Body UserWordProgress progress);

    @Headers("Prefer: return=representation")
    @POST("user_word_progress")
    Call<List<UserWordProgress>> createWordProgress(@Body UserWordProgress progress);

    @GET("user_word_progress?select=word_id&is_learned=eq.true")
    Call<List<UserWordProgress>> getLearnedWordsIds(@Query("user_id") String userIdFilter);

    @Headers({
            "Prefer: resolution=merge-duplicates,return=representation",
            "Content-Type: application/json"
    })
    @POST("user_stats")
    Call<List<JsonObject>> upsertUserStatsRaw(
            @Query("on_conflict") String onConflict,
            @Body JsonObject payload
    );

    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @POST("lesson_history")
    Call<List<JsonObject>> insertLessonRaw(@Body JsonObject payload);

    @GET("user_achievements")
    Call<List<JsonObject>> getUserAchievementRecordRaw(
            @Query("user_id") String userFilter,
            @Query("achievement_id") String achievementFilter
    );

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
            @Body JsonObject payload
    );
}