package com.example.codeandwords.data.schedule;

import android.os.Handler;
import android.util.Log;

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

public class ScheduleRepository {

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
                                Log.e("ScheduleRepository", "Локально занятие не сохранено: " + e.getMessage(), e);
                            }

                            mainHandler.post(() -> callback.onSuccess(schedule));
                        });

                    } catch (Exception e) {
                        Log.e("ScheduleRepository", "Ошибка обработки study_schedule: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Ошибка обработки ответа сервера"));
                    }
                } else {
                    Log.e("ScheduleRepository", "PostgreSQL не сохранил study_schedule: "
                            + response.code() + " | " + getErrorBody(response));

                    mainHandler.post(() -> callback.onError(
                            "PostgreSQL не сохранил запись. Код: " + response.code()
                    ));
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("ScheduleRepository", "Ошибка сети study_schedule: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка сети: " + t.getMessage()));
            }
        });
    }

    public void deleteStudySchedule(StudySchedule schedule, DataCallback<Void> callback) {
        if (schedule == null) {
            callback.onError("Занятие не найдено");
            return;
        }

        scheduleApiService.deleteStudyScheduleRaw("eq." + schedule.id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                executor.execute(() -> {
                    try {
                        studyScheduleDao.delete(schedule);
                    } catch (Exception e) {
                        Log.e("ScheduleRepository", "Ошибка локального удаления занятия: " + e.getMessage(), e);
                    }

                    mainHandler.post(() -> callback.onSuccess(null));
                });

                if (!response.isSuccessful()) {
                    Log.e("ScheduleRepository", "PostgreSQL не удалил study_schedule: "
                            + response.code() + " | " + getErrorBody(response));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("ScheduleRepository", "Ошибка удаления study_schedule: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Ошибка удаления: " + t.getMessage()));
            }
        });
    }

    public void getStudyScheduleForDate(int userId, String date, DataCallback<List<StudySchedule>> callback) {
        if (userId <= 0 || date == null || date.trim().isEmpty()) {
            callback.onError("Некорректные данные графика");
            return;
        }

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

                            for (StudySchedule schedule : schedules) {
                                studyScheduleDao.insert(schedule);
                            }

                            mainHandler.post(() -> callback.onSuccess(schedules));
                        } catch (Exception e) {
                            Log.e("ScheduleRepository", "Ошибка обработки study_schedule: " + e.getMessage(), e);
                            loadLocalStudyScheduleForDate(userId, date, callback);
                        }
                    });
                } else {
                    Log.e("ScheduleRepository", "Ошибка загрузки study_schedule: "
                            + response.code() + " | " + getErrorBody(response));
                    loadLocalStudyScheduleForDate(userId, date, callback);
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("ScheduleRepository", "Ошибка сети getStudyScheduleForDate: " + t.getMessage(), t);
                loadLocalStudyScheduleForDate(userId, date, callback);
            }
        });
    }

    public void getStudyScheduleForRange(int userId, String startDate, String endDate, DataCallback<List<StudySchedule>> callback) {
        if (userId <= 0 || startDate == null || startDate.trim().isEmpty()
                || endDate == null || endDate.trim().isEmpty()) {
            callback.onError("Некорректный диапазон дат");
            return;
        }

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
                        try {
                            List<StudySchedule> schedules = parseStudySchedules(response.body());

                            for (StudySchedule schedule : schedules) {
                                studyScheduleDao.insert(schedule);
                            }

                            mainHandler.post(() -> callback.onSuccess(schedules));
                        } catch (Exception e) {
                            Log.e("ScheduleRepository", "Ошибка обработки диапазона study_schedule: " + e.getMessage(), e);
                            loadLocalStudyScheduleForRange(userId, startDate, endDate, callback);
                        }
                    });
                } else {
                    Log.e("ScheduleRepository", "Ошибка загрузки диапазона study_schedule: "
                            + response.code() + " | " + getErrorBody(response));

                    loadLocalStudyScheduleForRange(userId, startDate, endDate, callback);
                }
            }

            @Override
            public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                Log.e("ScheduleRepository", "Ошибка сети getStudyScheduleForRange: " + t.getMessage(), t);
                loadLocalStudyScheduleForRange(userId, startDate, endDate, callback);
            }
        });
    }
    private List<StudySchedule> parseStudySchedules(List<JsonObject> jsonList) {
        List<StudySchedule> result = new ArrayList<>();

        if (jsonList == null) {
            return result;
        }

        for (JsonObject item : jsonList) {
            if (item == null) {
                continue;
            }

            StudySchedule schedule = new StudySchedule();

            if (item.has("id") && !item.get("id").isJsonNull()) {
                schedule.id = item.get("id").getAsInt();
            }

            if (item.has("user_id") && !item.get("user_id").isJsonNull()) {
                schedule.userId = item.get("user_id").getAsInt();
            }

            if (item.has("theme_id") && !item.get("theme_id").isJsonNull()) {
                schedule.themeId = item.get("theme_id").getAsInt();
            } else {
                schedule.themeId = 0;
            }

            if (item.has("theme_title") && !item.get("theme_title").isJsonNull()) {
                schedule.themeTitle = item.get("theme_title").getAsString();
            } else {
                schedule.themeTitle = "Без темы";
            }

            if (item.has("theme_short_title") && !item.get("theme_short_title").isJsonNull()) {
                schedule.themeShortTitle = item.get("theme_short_title").getAsString();
            } else {
                schedule.themeShortTitle = makeScheduleShortTitle(schedule.themeTitle);
            }

            if (item.has("schedule_date") && !item.get("schedule_date").isJsonNull()) {
                schedule.scheduleDate = item.get("schedule_date").getAsString();
            }

            if (item.has("start_time") && !item.get("start_time").isJsonNull()) {
                schedule.startTime = item.get("start_time").getAsString();
            }

            if (item.has("end_time") && !item.get("end_time").isJsonNull()) {
                schedule.endTime = item.get("end_time").getAsString();
            }

            if (item.has("note") && !item.get("note").isJsonNull()) {
                schedule.note = item.get("note").getAsString();
            } else {
                schedule.note = "";
            }

            if (schedule.userId > 0 && schedule.scheduleDate != null) {
                result.add(schedule);
            }
        }

        return result;
    }

    private String makeScheduleShortTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "??";
        }

        String clean = title.trim();
        String[] words = clean.split("\\s+");

        if (words.length >= 2) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.getDefault());
        }

        if (clean.length() >= 2) {
            return clean.substring(0, 2).toUpperCase(Locale.getDefault());
        }

        return clean.toUpperCase(Locale.getDefault());
    }

    private JsonObject buildStudySchedulePayload(StudySchedule schedule) {
        JsonObject payload = new JsonObject();

        payload.addProperty("user_id", schedule.userId);

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
            Log.e("ScheduleRepository", "Ошибка чтения errorBody: " + e.getMessage(), e);
        }
        return "Неизвестная ошибка";
    }

    public void loadLocalStudyScheduleForDate(int userId, String date,
                                              DataCallback<List<StudySchedule>> callback) {
        executor.execute(() -> {
            try {
                List<StudySchedule> local = studyScheduleDao.getByDate(userId, date);
                mainHandler.post(() -> callback.onSuccess(local));
            } catch (Exception e) {
                Log.e("ScheduleRepository",
                        "Ошибка локальной загрузки: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(
                        "Не удалось загрузить график занятий"));
            }
        });
    }

    public void loadLocalStudyScheduleForRange(int userId,
                                               String startDate,
                                               String endDate,
                                               DataCallback<List<StudySchedule>> callback) {
        executor.execute(() -> {
            try {
                List<StudySchedule> local =
                        studyScheduleDao.getByDateRange(userId, startDate, endDate);
                mainHandler.post(() -> callback.onSuccess(local));
            } catch (Exception e) {
                Log.e("ScheduleRepository",
                        "Ошибка локальной загрузки диапазона: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(
                        "Не удалось загрузить график занятий"));
            }
        });
    }

    // ===== ИНТЕРФЕЙС CALLBACK =====

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}