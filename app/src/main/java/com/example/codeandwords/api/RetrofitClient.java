package com.example.codeandwords.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // Убедитесь, что URL заканчивается на /rest/v1/
    private static final String BASE_URL = "https://jmdolczqymuvpxyvhfgy.supabase.co/rest/v1/";

    // Ваш ключ API (Anon Key)
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImptZG9sY3pxeW11dnB4eXZoZmd5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzIzODczMTQsImV4cCI6MjA4Nzk2MzMxNH0.frnlLVZJWtPyPb-e09orCGLvg45NDgQDgdlX2eTybVA";

    private static Retrofit retrofit = null;

    public static ApiService getApiService() {
        if (retrofit == null) {
            // Логирование для отладки — поможет увидеть 401 или 400 ошибки в Logcat
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        // Добавляем заголовки, необходимые для Supabase
                        Request request = original.newBuilder()
                                .header("apikey", SUPABASE_KEY)
                                .header("Authorization", "Bearer " + SUPABASE_KEY)
                                .header("Content-Type", "application/json")
                                // "Prefer" заставляет Supabase возвращать созданный объект, а не пустоту
                                .header("Prefer", "return=representation")
                                .build();
                        return chain.proceed(request);
                    })
                    .addInterceptor(logging)
                    .build();

            // Мы убираем excludeFieldsWithoutExposeAnnotation, чтобы GSON видел все поля моделей
            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}