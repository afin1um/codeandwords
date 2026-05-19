package com.example.codeandwords.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final String BASE_URL = "https://jmdolczqymuvpxyvhfgy.supabase.co/rest/v1/";
    private static final String SUPABASE_ANON_KEY = "sb_publishable_DzqZslXoYvCnq4FOdfqOZQ_r0ZSCcbZ";

    private static Retrofit retrofit;
    private static Retrofit fastRetrofit;

    private RetrofitClient() {}

    public static synchronized ApiService getApiService() {
        if (retrofit == null) {
            retrofit = buildRetrofit(30, 60, 60, 90, 64, 20, new RetryInterceptor(1, 1000));
        }
        return retrofit.create(ApiService.class);
    }

    public static synchronized ApiService getFastApiService() {
        if (fastRetrofit == null) {
            fastRetrofit = buildRetrofit(15, 30, 30, 45, 64, 20, new RetryInterceptor(1, 1000));
        }
        return fastRetrofit.create(ApiService.class);
    }

    private static Retrofit buildRetrofit(int connectTimeout, int readTimeout, int writeTimeout,
                                          int callTimeout, int maxRequests, int maxRequestsPerHost,
                                          RetryInterceptor retryInterceptor) {

        // ✅ 1. Dispatcher с фиксированным пулом потоков
        Dispatcher dispatcher = new Dispatcher(Executors.newFixedThreadPool(4));
        dispatcher.setMaxRequests(maxRequests);
        dispatcher.setMaxRequestsPerHost(maxRequestsPerHost);

        // ✅ 2. Агрессивный пул соединений: 30 секунд вместо 5 минут
        // Если соединение простаивает 30 секунд, оно закрывается.
        // Это предотвращает "зависшие" сокеты.
        ConnectionPool connectionPool = new ConnectionPool(5, 30, TimeUnit.SECONDS);

        OkHttpClient client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .callTimeout(callTimeout, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .addInterceptor(retryInterceptor)
                .addInterceptor(chain -> {
                    Request request = chain.request().newBuilder()
                            .header("apikey", SUPABASE_ANON_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("Prefer", "return=representation")
                            .build();
                    return chain.proceed(request);
                })
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                .build();
    }
}