package com.example.codeandwords.api;

import com.google.gson.Gson;
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
            /*
             * Обычный клиент.
             * Делаем таймауты больше, потому что первый запрос к Supabase
             * или тяжёлый login-запрос может отвечать дольше 30 секунд.
             */
            retrofit = buildRetrofit(
                    30,     // connectTimeout
                    120,    // readTimeout
                    120,    // writeTimeout
                    180,    // callTimeout
                    64,     // maxRequests
                    20,     // maxRequestsPerHost
                    new RetryInterceptor(1, 1000)
            );
        }
        return retrofit.create(ApiService.class);
    }

    public static synchronized ApiService getFastApiService() {
        if (fastRetrofit == null) {
            /*
             * Быстрый клиент.
             * Но readTimeout всё равно лучше не делать слишком маленьким,
             * иначе login/первый запрос может падать по timeout.
             */
            fastRetrofit = buildRetrofit(
                    20,     // connectTimeout
                    60,     // readTimeout
                    60,     // writeTimeout
                    90,     // callTimeout
                    64,     // maxRequests
                    20,     // maxRequestsPerHost
                    new RetryInterceptor(1, 1000)
            );
        }
        return fastRetrofit.create(ApiService.class);
    }

    private static Retrofit buildRetrofit(
            int connectTimeout,
            int readTimeout,
            int writeTimeout,
            int callTimeout,
            int maxRequests,
            int maxRequestsPerHost,
            RetryInterceptor retryInterceptor
    ) {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(maxRequests);
        dispatcher.setMaxRequestsPerHost(maxRequestsPerHost);

        ConnectionPool connectionPool = new ConnectionPool(
                10,
                5,
                TimeUnit.MINUTES
        );

        OkHttpClient client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)

                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .callTimeout(callTimeout, TimeUnit.SECONDS)

                .retryOnConnectionFailure(true)

                /*
                 * ВАЖНО:
                 * У тебя ошибка была:
                 * okhttp3.internal.http2.Http2Stream$StreamTimeout
                 *
                 * Поэтому временно отключаем HTTP/2 и заставляем OkHttp
                 * использовать HTTP/1.1.
                 */
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))

                .addInterceptor(retryInterceptor)

                .addInterceptor(chain -> {
                    Request original = chain.request();

                    Request request = original.newBuilder()
                            .header("apikey", SUPABASE_ANON_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("Prefer", "return=representation")
                            .build();

                    return chain.proceed(request);
                })

                .build();

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }
}