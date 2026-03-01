package com.example.codeandwords.api;

import com.example.codeandwords.model.LeaderboardEntry;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserWordProgress;
import com.example.codeandwords.model.Word;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface ApiService {

    // 1. ДОБАВЛЕН ЗАГОЛОВОК Prefer: return=representation
    // Это заставит Supabase вернуть созданного пользователя в теле ответа
    @Headers({
            "Prefer: return=representation",
            "Content-Type: application/json"
    })
    @GET("users")
    Call<List<User>> login(
            @Query("email") String email,
            @Query("password_hash") String passwordHash
    );

    @POST("users")
    Call<List<User>> register(@Body User user);

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

    // 2. ТАКЖЕ ДОБАВЛЕН ЗАГОЛОВОК для создания прогресса слов
    // И изменен тип возвращаемого значения на List<UserWordProgress> для стабильности
    @Headers("Prefer: return=representation")
    @POST("user_word_progress")
    Call<List<UserWordProgress>> createWordProgress(@Body UserWordProgress progress);

    @GET("user_word_progress?select=word_id&is_learned=eq.true")
    Call<List<UserWordProgress>> getLearnedWordsIds(@Query("user_id") String userIdFilter);
}