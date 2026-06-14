package com.example.codeandwords.api;

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

    private static final String BASE_URL =
            "https://jmdolczqymuvpxyvhfgy.supabase.co/rest/v1/";
    private static final String SUPABASE_ANON_KEY =
            "sb_publishable_DzqZslXoYvCnq4FOdfqOZQ_r0ZSCcbZ";

    private static Retrofit retrofit;
    private static Retrofit fastRetrofit;

    private RetrofitClient() {}

    public static synchronized ApiService getApiService() {
        if (retrofit == null) {
            retrofit = buildRetrofit(
                    30,   // connectTimeout
                    90,   // readTimeout    ✅ увеличен с 60 до 90
                    60,   // writeTimeout
                    120,  // callTimeout    ✅ увеличен с 90 до 120
                    64,
                    20,
                    new RetryInterceptor(3, 2000) // ✅ 3 попытки вместо 1, пауза 2с
            );
        }
        return retrofit.create(ApiService.class);
    }

    public static synchronized ApiService getFastApiService() {
        if (fastRetrofit == null) {
            fastRetrofit = buildRetrofit(
                    15,   // connectTimeout
                    45,   // readTimeout    ✅ увеличен с 30 до 45
                    30,   // writeTimeout
                    60,   // callTimeout    ✅ увеличен с 45 до 60
                    64,
                    20,
                    new RetryInterceptor(2, 1500) // ✅ 2 попытки вместо 1
            );
        }
        return fastRetrofit.create(ApiService.class);
    }

    private static Retrofit buildRetrofit(
            int connectTimeout, int readTimeout, int writeTimeout,
            int callTimeout, int maxRequests, int maxRequestsPerHost,
            RetryInterceptor retryInterceptor) {

        Dispatcher dispatcher = new Dispatcher(Executors.newFixedThreadPool(4));
        dispatcher.setMaxRequests(maxRequests);
        dispatcher.setMaxRequestsPerHost(maxRequestsPerHost);

        // ✅ Увеличено время жизни соединения с 30 до 60 секунд
        // Это уменьшает вероятность "Socket closed" при медленном сервере
        ConnectionPool connectionPool = new ConnectionPool(
                5, 60, TimeUnit.SECONDS
        );

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
                    Request original = chain.request();

                    // ✅ Добавляем Keep-Alive чтобы соединение не закрывалось
                    Request request = original.newBuilder()
                            .header("apikey", SUPABASE_ANON_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("Prefer", "return=representation")
                            .header("Connection", "keep-alive") // ✅ НОВОЕ
                            .build();
                    return chain.proceed(request);
                })
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(
                        new GsonBuilder().setLenient().create()))
                .build();
    }
}