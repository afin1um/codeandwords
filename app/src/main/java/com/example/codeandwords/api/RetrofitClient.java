package com.example.codeandwords.api;

import com.google.gson.GsonBuilder;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

// Singleton-клиент Retrofit для работы с Supabase REST API
public class RetrofitClient {

    private static final String BASE_URL =
            "https://jmdolczqymuvpxyvhfgy.supabase.co/rest/v1/";
    private static final String SUPABASE_ANON_KEY =
            "sb_publishable_DzqZslXoYvCnq4FOdfqOZQ_r0ZSCcbZ";

    private static Retrofit retrofit;
    private static Retrofit fastRetrofit;

    private RetrofitClient() {}

    // Стандартный клиент: увеличенный connectTimeout для холодного старта TLS,
    // умеренный readTimeout и одна попытка повтора
    public static synchronized ApiService getApiService() {
        if (retrofit == null) {
            retrofit = buildRetrofit(
                    25, 30, 30, 60, 32, 8,
                    new RetryInterceptor(2, 800)
            );
        }
        return retrofit.create(ApiService.class);
    }

    // Быстрый клиент для приоритетных запросов
    public static synchronized ApiService getFastApiService() {
        if (fastRetrofit == null) {
            fastRetrofit = buildRetrofit(
                    20, 20, 20, 40, 32, 8,
                    new RetryInterceptor(1, 500)
            );
        }
        return fastRetrofit.create(ApiService.class);
    }

    // Сборка экземпляра Retrofit с заданными параметрами соединения.
    // Dispatcher использует свой внутренний пул потоков — без передачи кастомного executor.
    private static Retrofit buildRetrofit(
            int connectTimeout, int readTimeout, int writeTimeout,
            int callTimeout, int maxRequests, int maxRequestsPerHost,
            RetryInterceptor retryInterceptor) {

        // Dispatcher без кастомного executor — OkHttp сам создаст пул потоков по числу запросов.
        // Раньше был FixedThreadPool(4) — это блокировало все запросы при 4+ зависших.
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(maxRequests);
        dispatcher.setMaxRequestsPerHost(maxRequestsPerHost);

        // Пул соединений: до 8 соединений, время жизни 30 секунд
        ConnectionPool connectionPool = new ConnectionPool(8, 30, TimeUnit.SECONDS);

        OkHttpClient client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .callTimeout(callTimeout, TimeUnit.SECONDS)
                // Отключаем встроенный ретрай OkHttp — используем только наш RetryInterceptor
                .retryOnConnectionFailure(false)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .addInterceptor(retryInterceptor)
                // Добавление обязательных заголовков Supabase к каждому запросу
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("apikey", SUPABASE_ANON_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("Prefer", "return=representation")
                            .header("Connection", "keep-alive")
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