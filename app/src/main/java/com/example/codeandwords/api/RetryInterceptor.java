package com.example.codeandwords.api;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RetryInterceptor implements Interceptor {

    private final int maxRetries;
    private final long retryDelayMillis;

    public RetryInterceptor(int maxRetries, long retryDelayMillis) {
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        IOException lastException = null;

        int tryCount = 0;
        Response response = null;

        // Цикл попыток
        while (tryCount < maxRetries) {
            try {
                response = chain.proceed(request);

                // Если ответ успешен (код 200-299), возвращаем его
                if (response.isSuccessful()) {
                    return response;
                }

                // Если сервер вернул ошибку (например, 500 или 504), пытаемся повторить
                // Но если это ошибка клиента (401, 404), лучше не повторять
                if (response.code() >= 500 || response.code() == 408) {
                    tryCount++;
                    if (tryCount == maxRetries) {
                        return response; // Последняя попытка не удалась, возвращаем ошибку
                    }

                    // 🔹 ИСПРАВЛЕНИЕ: Добавлен try-catch для Thread.sleep
                    try {
                        Thread.sleep(retryDelayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", e);
                    }

                    continue;
                }

                // Для остальных ошибок (404, 401 и т.д.) возвращаем ответ без повтора
                return response;

            } catch (IOException e) {
                // Ловим IOException, в которую входит SocketTimeoutException
                lastException = e;
                tryCount++;

                if (tryCount == maxRetries) {
                    throw e; // Закончились попытки, выбрасываем ошибку
                }

                // 🔹 ИСПРАВЛЕНИЕ: Добавлен try-catch для Thread.sleep
                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", ie);
                }
            }
        }

        // Если мы здесь, значит что-то пошло не так
        if (lastException != null) {
            throw lastException;
        }
        return response;
    }
}