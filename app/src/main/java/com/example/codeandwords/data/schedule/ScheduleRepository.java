package com.example.codeandwords.data.schedule;

import android.os.Handler;
import android.util.Log;

import androidx.room.RoomDatabase;

import com.example.codeandwords.api.ApiService;
import com.example.codeandwords.db.StudyScheduleDao;
import com.example.codeandwords.model.StudySchedule;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Репозиторий расписания занятий: создание, удаление и загрузка с поддержкой cache-first
public class ScheduleRepository {

    private static final String TAG = "ScheduleRepository";

    private final StudyScheduleDao studyScheduleDao;
    private final ApiService scheduleApiService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public ScheduleRepository(StudyScheduleDao studyScheduleDao,
                              ApiService scheduleApiService,
                              ExecutorService executor,
                              Handler mainHandler) {
        this.studyScheduleDao = studyScheduleDao;
        this.scheduleApiService = scheduleApiService;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    // Создаёт запись расписания на сервере, затем сохраняет локально с полученным ID
    public void createStudySchedule(StudySchedule schedule, DataCallback<StudySchedule> callback) {
        if (schedule == null) {
            callback.onError("Данные занятия не заполнены");
            return;
        }

        JsonObject payload = buildStudySchedulePayload(schedule);

        scheduleApiService.insertStudyScheduleRaw(payload).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    try {
                        JsonObject saved = response.body().get(0);
                        if (saved.has("id") && !saved.get("id").isJsonNull()) {
                            schedule.id = saved.get("id").getAsInt();
                        }

                        executor.execute(() -> {
                            try {
                                studyScheduleDao.insert(schedule);
                            } catch (Exception e) {
                                Log.e(TAG, "Локально занятие не сохранено: " + e.getMessage(), e);
                            }
                            mainHandler.post(() -> callback.onSuccess(schedule));
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка обработки study_schedule: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Ошибка обработки ответа сервера"));
                    }
                } else {
                    Log.e(TAG, "Сервер не сохранил study_schedule: "
                            + response.code() + " | " + getErrorBody(response));
                    mainHandler.post(() -> callback.onError(
                            "Не удалось сохранить занятие. Код: " + response.code()
                    ));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e(TAG, "Ошибка сети study_schedule: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
            }
        });
    }

    // Удаляет запись расписания: сначала локально для мгновенного отклика UI, затем на сервере
    public void deleteStudySchedule(StudySchedule schedule, DataCallback<Void> callback) {
        if (schedule == null) {
            callback.onError("Занятие не найдено");
            return;
        }

        executor.execute(() -> {
            try {
                studyScheduleDao.delete(schedule);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локального удаления: " + e.getMessage(), e);
            }
            mainHandler.post(() -> callback.onSuccess(null));
        });

        scheduleApiService.deleteStudyScheduleRaw("eq." + schedule.id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Сервер не удалил study_schedule: "
                            + response.code() + " | " + getErrorBody(response));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Ошибка сети удаления study_schedule: " + t.getMessage(), t);
            }
        });
    }

    // Cache-first: сначала отдаёт локальные данные, затем обновляет с сервера в фоне
    public void getStudyScheduleForDate(int userId, String date,
                                        DataCallback<List<StudySchedule>> callback) {
        if (userId <= 0 || date == null || date.trim().isEmpty()) {
            callback.onError("Некорректные данные графика");
            return;
        }

        executor.execute(() -> {
            try {
                List<StudySchedule> local = studyScheduleDao.getByDate(userId, date);
                if (local != null && !local.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(local));
                }
                refreshScheduleForDateFromServer(userId, date, local, callback);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локальной загрузки: " + e.getMessage(), e);
                refreshScheduleForDateFromServer(userId, date, null, callback);
            }
        });
    }

    // Загружает расписание за дату с сервера и сохраняет локально
    private void refreshScheduleForDateFromServer(int userId, String date,
                                                  List<StudySchedule> localData,
                                                  DataCallback<List<StudySchedule>> callback) {
        scheduleApiService.getStudyScheduleRaw(
                "eq." + userId,
                "eq." + date,
                "start_time.asc"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        try {
                            List<StudySchedule> schedules = parseStudySchedules(response.body());

                            studyScheduleDao.insertAllOrReplace(schedules);

                            mainHandler.post(() -> callback.onSuccess(schedules));
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка обработки study_schedule: " + e.getMessage(), e);
                            if (localData == null || localData.isEmpty()) {
                                mainHandler.post(() -> callback.onError("Ошибка обработки данных"));
                            }
                        }
                    });
                } else {
                    Log.e(TAG, "Ошибка загрузки study_schedule: "
                            + response.code() + " | " + getErrorBody(response));
                    if (localData == null || localData.isEmpty()) {
                        mainHandler.post(() -> callback.onError(
                                "Не удалось загрузить график занятий"));
                    }
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e(TAG, "Ошибка сети getStudyScheduleForDate: " + t.getMessage(), t);
                if (localData == null || localData.isEmpty()) {
                    mainHandler.post(() -> callback.onError(
                            "Не удалось загрузить график занятий"));
                }
            }
        });
    }

    // Cache-first для диапазона дат: сначала локальные данные, затем обновление с сервера
    public void getStudyScheduleForRange(int userId, String startDate, String endDate,
                                         DataCallback<List<StudySchedule>> callback) {
        if (userId <= 0 || startDate == null || startDate.trim().isEmpty()
                || endDate == null || endDate.trim().isEmpty()) {
            callback.onError("Некорректный диапазон дат");
            return;
        }

        executor.execute(() -> {
            try {
                List<StudySchedule> local = studyScheduleDao.getByDateRange(userId, startDate, endDate);
                if (local != null && !local.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(local));
                }
                refreshScheduleRangeFromServer(userId, startDate, endDate, local, callback);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локальной загрузки диапазона: " + e.getMessage(), e);
                refreshScheduleRangeFromServer(userId, startDate, endDate, null, callback);
            }
        });
    }

    // Загружает расписание за диапазон дат с сервера и сохраняет локально
    private void refreshScheduleRangeFromServer(int userId, String startDate, String endDate,
                                                List<StudySchedule> localData,
                                                DataCallback<List<StudySchedule>> callback) {
        scheduleApiService.getStudyScheduleRangeRaw(
                "eq." + userId,
                "gte." + startDate,
                "lte." + endDate,
                "schedule_date.asc,start_time.asc"
        ).enqueue(new Callback<List<JsonObject>>() {
            @Override
            public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        executor.execute(() -> {
                            try {
                                List<StudySchedule> schedules = parseStudySchedules(response.body());

                                studyScheduleDao.insertAllOrReplace(schedules);  // ← заменили

                                mainHandler.post(() -> callback.onSuccess(schedules));
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка обработки диапазона: " + e.getMessage(), e);
                                if (localData == null || localData.isEmpty()) {
                                    mainHandler.post(() -> callback.onError("Ошибка обработки"));
                                }
                            }
                        });
                    });
                } else {
                    Log.e(TAG, "Ошибка загрузки диапазона: "
                            + response.code() + " | " + getErrorBody(response));
                    if (localData == null || localData.isEmpty()) {
                        mainHandler.post(() -> callback.onError(
                                "Не удалось загрузить график"));
                    }
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e(TAG, "Ошибка сети getRange: " + t.getMessage(), t);
                if (localData == null || localData.isEmpty()) {
                    mainHandler.post(() -> callback.onError("Не удалось загрузить график"));
                }
            }
        });
    }

    // Прямой доступ к локальной БД для получения расписания на дату без сетевого запроса
    public void loadLocalStudyScheduleForDate(int userId, String date,
                                              DataCallback<List<StudySchedule>> callback) {
        executor.execute(() -> {
            try {
                List<StudySchedule> local = studyScheduleDao.getByDate(userId, date);
                mainHandler.post(() -> callback.onSuccess(local));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локальной загрузки: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить график занятий"));
            }
        });
    }

    // Прямой доступ к локальной БД для получения расписания за диапазон дат
    public void loadLocalStudyScheduleForRange(int userId, String startDate, String endDate,
                                               DataCallback<List<StudySchedule>> callback) {
        executor.execute(() -> {
            try {
                List<StudySchedule> local = studyScheduleDao.getByDateRange(userId, startDate, endDate);
                mainHandler.post(() -> callback.onSuccess(local));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка локальной загрузки диапазона: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Не удалось загрузить график занятий"));
            }
        });
    }

    // Разбирает список JSON-объектов расписания в список моделей StudySchedule
    private List<StudySchedule> parseStudySchedules(List<JsonObject> jsonList) {
        List<StudySchedule> result = new ArrayList<>();
        if (jsonList == null) return result;

        for (JsonObject item : jsonList) {
            if (item == null) continue;

            StudySchedule schedule = new StudySchedule();

            if (item.has("id") && !item.get("id").isJsonNull())
                schedule.id = item.get("id").getAsInt();

            if (item.has("user_id") && !item.get("user_id").isJsonNull())
                schedule.userId = item.get("user_id").getAsInt();

            if (item.has("theme_id") && !item.get("theme_id").isJsonNull())
                schedule.themeId = item.get("theme_id").getAsInt();
            else
                schedule.themeId = 0;

            if (item.has("theme_title") && !item.get("theme_title").isJsonNull())
                schedule.themeTitle = item.get("theme_title").getAsString();
            else
                schedule.themeTitle = "Без темы";

            // Если короткое название отсутствует — генерируем из полного
            if (item.has("theme_short_title") && !item.get("theme_short_title").isJsonNull())
                schedule.themeShortTitle = item.get("theme_short_title").getAsString();
            else
                schedule.themeShortTitle = makeScheduleShortTitle(schedule.themeTitle);

            if (item.has("schedule_date") && !item.get("schedule_date").isJsonNull())
                schedule.scheduleDate = item.get("schedule_date").getAsString();

            if (item.has("start_time") && !item.get("start_time").isJsonNull())
                schedule.startTime = item.get("start_time").getAsString();

            if (item.has("end_time") && !item.get("end_time").isJsonNull())
                schedule.endTime = item.get("end_time").getAsString();

            if (item.has("note") && !item.get("note").isJsonNull())
                schedule.note = item.get("note").getAsString();
            else
                schedule.note = "";

            // Добавляем только записи с обязательными полями
            if (schedule.userId > 0 && schedule.scheduleDate != null) {
                result.add(schedule);
            }
        }

        return result;
    }

    // Генерирует короткое название из первых букв слов или первых двух символов строки
    private String makeScheduleShortTitle(String title) {
        if (title == null || title.trim().isEmpty()) return "??";

        String clean = title.trim();
        String[] words = clean.split("\\s+");

        if (words.length >= 2) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1))
                    .toUpperCase(Locale.getDefault());
        }

        if (clean.length() >= 2) {
            return clean.substring(0, 2).toUpperCase(Locale.getDefault());
        }

        return clean.toUpperCase(Locale.getDefault());
    }

    // Собирает JSON-объект для отправки записи расписания на сервер
    private JsonObject buildStudySchedulePayload(StudySchedule schedule) {
        JsonObject payload = new JsonObject();
        payload.addProperty("user_id", schedule.userId);

        // theme_id = null если тема не выбрана
        if (schedule.themeId > 0) {
            payload.addProperty("theme_id", schedule.themeId);
        } else {
            payload.add("theme_id", JsonNull.INSTANCE);
        }

        payload.addProperty("theme_title", schedule.themeTitle);
        payload.addProperty("theme_short_title", schedule.themeShortTitle);
        payload.addProperty("schedule_date", schedule.scheduleDate);
        payload.addProperty("start_time", schedule.startTime);
        payload.addProperty("end_time", schedule.endTime);

        // note = null если не заполнено
        if (schedule.note != null && !schedule.note.trim().isEmpty()) {
            payload.addProperty("note", schedule.note.trim());
        } else {
            payload.add("note", JsonNull.INSTANCE);
        }

        return payload;
    }

    private String getErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }
}