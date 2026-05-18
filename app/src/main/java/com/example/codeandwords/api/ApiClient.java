package com.example.codeandwords.api;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.io.IOException;

public class ApiClient {

    private static final String SUPABASE_KEY="sb_secret_hw9h5S3M-A1vkWDcNCn-jA_ZVjBsbUJ";
    private static final String BASE_URL= "https://jmdolczqymuvpxyvhfgy.supabase.co/rest/v1/";

    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            // Создаем клиент, который будет добавлять API-ключ в каждый заголовок
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request original = chain.request();
                            Request request = original.newBuilder()
                                    .header("apikey", SUPABASE_KEY)
                                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                                    .header("Content-Type", "application/json")
                                    .header("Prefer", "return=representation") // Чтобы Supabase возвращал созданный объект
                                    .method(original.method(), original.body())
                                    .build();
                            return chain.proceed(request);
                        }
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}