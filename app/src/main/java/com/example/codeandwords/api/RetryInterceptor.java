package com.example.codeandwords.api;

import java.io.IOException;
import java.net.SocketTimeoutException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

// Перехватчик OkHttp для повтора запросов при сбоях сети.
// SocketTimeoutException повторяется только один раз — для решения проблемы
// холодного старта HTTPS-соединения с Supabase.
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
        boolean timeoutRetried = false;

        while (tryCount < maxRetries) {
            try {
                response = chain.proceed(request);

                // Успешный ответ — возвращаем немедленно
                if (response.isSuccessful()) {
                    return response;
                }

                // Серверные ошибки (5xx) и 408 допускают повтор;
                // ошибки клиента (4xx) повторно не отправляются
                if (response.code() >= 500 || response.code() == 408) {
                    tryCount++;
                    if (tryCount >= maxRetries) {
                        return response;
                    }

                    // Закрываем ответ перед повтором, чтобы освободить соединение
                    response.close();

                    sleepUninterruptibly(retryDelayMillis);
                    continue;
                }

                return response;

            } catch (SocketTimeoutException e) {
                // Таймаут повторяем только один раз — решает проблему холодного старта TLS.
                // Дальше повторять бесполезно, только удерживаем поток впустую
                if (!timeoutRetried) {
                    timeoutRetried = true;
                    lastException = e;
                    sleepUninterruptibly(300);
                    continue;
                }
                throw e;

            } catch (IOException e) {
                lastException = e;
                tryCount++;

                if (tryCount >= maxRetries) {
                    throw e;
                }

                sleepUninterruptibly(retryDelayMillis);
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        return response;
    }

    // Засыпает поток, корректно обрабатывая прерывание
    private void sleepUninterruptibly(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Retry interrupted", e);
        }
    }
}