package com.example.codeandwords.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final String BASE_URL = "https://jmdolczqymuvpxyvhfgy.supabase.co/rest/v1/";

    private static final String SUPABASE_ANON_KEY =
            "sb_publishable_DzqZslXoYvCnq4FOdfqOZQ_r0ZSCcbZ";

    private static Retrofit retrofit;
    private static Retrofit fastRetrofit;

    private RetrofitClient() {
    }

    public static ApiService getApiService() {
        if (retrofit == null) {
            retrofit = buildRetrofit(30, 30, 30, 45, 64, 20);
        }
        return retrofit.create(ApiService.class);
    }

    public static ApiService getFastApiService() {
        if (fastRetrofit == null) {
            fastRetrofit = buildRetrofit(45, 45, 45, 60, 64, 64);
        }
        return fastRetrofit.create(ApiService.class);
    }

    private static Retrofit buildRetrofit(
            int connectTimeout,
            int readTimeout,
            int writeTimeout,
            int callTimeout,
            int maxRequests,
            int maxRequestsPerHost
    ) {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(maxRequests);
        dispatcher.setMaxRequestsPerHost(maxRequestsPerHost);

        OkHttpClient client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .callTimeout(callTimeout, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();

                    Request request = original.newBuilder()
                            .header("apikey", SUPABASE_ANON_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
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