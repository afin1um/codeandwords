package com.example.codeandwords.ui.profile;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.db.AppDatabase;
import com.example.codeandwords.model.StudySchedule;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Экран расписания занятий: календарь с метками, добавление и удаление записей.
public class StudyScheduleActivity extends AppCompatActivity {

    private View layoutAddScheduleForm;

    private TextView tvMonthTitle;
    private TextView tvSelectedDayTitle;
    private TextView tvSelectedDate;
    private TextView tvStartTime;
    private TextView tvEndTime;

    private Spinner spinnerTheme;
    private EditText etNote;
    private Button btnAddSchedule;

    private RecyclerView recyclerMonthCalendar;
    private RecyclerView recyclerSchedule;
    private TextView tvEmptySchedule;

    private AppDatabase db;
    private Repository repository;
    private StudyScheduleAdapter scheduleAdapter;
    private StudyCalendarAdapter calendarAdapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int currentUserId = -1;

    private String selectedDate;
    private String selectedStartTime = "18:00";
    private String selectedEndTime = "19:00";

    private final Calendar visibleMonthCalendar = Calendar.getInstance();
    private final List<Theme> themeList = new ArrayList<>();

    private final SimpleDateFormat dbDateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private final SimpleDateFormat visibleDateFormat =
            new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

    private final SimpleDateFormat monthTitleFormat =
            new SimpleDateFormat("LLLL yyyy", new Locale("ru"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_schedule);

        db = AppDatabase.getInstance(this);
        repository = Repository.getInstance(getApplicationContext());

        selectedDate = dbDateFormat.format(Calendar.getInstance().getTime());

        initViews();
        setupThemeSpinner();
        setupCalendarRecycler();
        setupScheduleRecycler();
        setupClicks();
        loadCurrentUser();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBackStudySchedule);
        btnBack.setOnClickListener(v -> finish());

        layoutAddScheduleForm = findViewById(R.id.layoutAddScheduleForm);

        tvMonthTitle = findViewById(R.id.tvMonthTitle);
        tvSelectedDayTitle = findViewById(R.id.tvSelectedDayTitle);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);

        spinnerTheme = findViewById(R.id.spinnerTheme);
        etNote = findViewById(R.id.etScheduleNote);
        btnAddSchedule = findViewById(R.id.btnAddSchedule);

        recyclerMonthCalendar = findViewById(R.id.recyclerMonthCalendar);
        recyclerSchedule = findViewById(R.id.recyclerStudySchedule);
        tvEmptySchedule = findViewById(R.id.tvEmptySchedule);

        tvSelectedDate.setText(formatVisibleDate(selectedDate));
        tvStartTime.setText(selectedStartTime);
        tvEndTime.setText(selectedEndTime);

        updateSelectedDayTitle();
    }

    // Загружает список тем из локальной БД в фоновом потоке
    private void setupThemeSpinner() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Theme> themes = db.themeDao().getAllThemes();

            mainHandler.post(() -> {
                themeList.clear();

                if (themes != null) {
                    themeList.addAll(themes);
                }

                List<String> themeNames = new ArrayList<>();

                if (themeList.isEmpty()) {
                    themeNames.add("Нет доступных тем");
                } else {
                    for (Theme theme : themeList) {
                        themeNames.add(getThemeTitle(theme));
                    }
                }

                ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(
                        StudyScheduleActivity.this,
                        android.R.layout.simple_spinner_item,
                        themeNames
                );

                themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerTheme.setAdapter(themeAdapter);
            });
        });
    }

    private void setupCalendarRecycler() {
        calendarAdapter = new StudyCalendarAdapter(date -> {
            if (date == null || date.trim().isEmpty()) {
                return;
            }

            selectedDate = date;
            tvSelectedDate.setText(formatVisibleDate(selectedDate));
            updateSelectedDayTitle();
            refreshMonthCalendarLocal();
            loadScheduleForSelectedDateLocal();
        });

        recyclerMonthCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        recyclerMonthCalendar.setNestedScrollingEnabled(false);
        recyclerMonthCalendar.setAdapter(calendarAdapter);
    }

    private void setupScheduleRecycler() {
        scheduleAdapter = new StudyScheduleAdapter(this::showScheduleDetails);
        recyclerSchedule.setLayoutManager(new LinearLayoutManager(this));
        recyclerSchedule.setAdapter(scheduleAdapter);
    }

    private void setupClicks() {
        ImageButton btnAddStudySchedule = findViewById(R.id.btnAddStudySchedule);
        ImageButton btnPrevMonth = findViewById(R.id.btnPrevMonth);
        ImageButton btnNextMonth = findViewById(R.id.btnNextMonth);

        btnAddStudySchedule.setOnClickListener(v -> {
            if (layoutAddScheduleForm.getVisibility() == View.VISIBLE) {
                layoutAddScheduleForm.setVisibility(View.GONE);
            } else {
                layoutAddScheduleForm.setVisibility(View.VISIBLE);
            }
        });

        btnPrevMonth.setOnClickListener(v -> {
            visibleMonthCalendar.add(Calendar.MONTH, -1);
            refreshMonthCalendarLocal();
            refreshMonthCalendarFromServer();
        });

        btnNextMonth.setOnClickListener(v -> {
            visibleMonthCalendar.add(Calendar.MONTH, 1);
            refreshMonthCalendarLocal();
            refreshMonthCalendarFromServer();
        });

        tvSelectedDate.setOnClickListener(v -> showDatePicker());
        tvStartTime.setOnClickListener(v -> showTimePicker(true));
        tvEndTime.setOnClickListener(v -> showTimePicker(false));

        btnAddSchedule.setOnClickListener(v -> saveScheduleToDatabase());
    }

    private void loadCurrentUser() {
        repository.getCurrentUser(new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user == null || user.getId() == null) {
                    Toast.makeText(StudyScheduleActivity.this, "Пользователь не найден",
                            Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                currentUserId = user.getId();

                // Мгновенный показ из локальной БД
                refreshMonthCalendarLocal();
                loadScheduleForSelectedDateLocal();

                // Фоновая синхронизация с сервером
                refreshMonthCalendarFromServer();
                loadScheduleForSelectedDateFromServer();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(StudyScheduleActivity.this, "Ошибка загрузки пользователя",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // Валидирует данные и создаёт запись расписания после проверки пересечений
    private void saveScheduleToDatabase() {
        if (currentUserId <= 0) {
            Toast.makeText(this, "Пользователь не загружен", Toast.LENGTH_SHORT).show();
            return;
        }

        if (themeList.isEmpty()) {
            Toast.makeText(this, "Сначала добавьте темы", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isSelectedDateAndTimeValid()) {
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            boolean overlapping = isTimeOverlappingBackground();

            mainHandler.post(() -> {
                if (overlapping) {
                    Toast.makeText(this, "В это время уже есть занятие", Toast.LENGTH_LONG).show();
                    return;
                }

                createScheduleAfterValidation();
            });
        });
    }

    private void createScheduleAfterValidation() {
        int pos = spinnerTheme.getSelectedItemPosition();

        if (pos < 0 || pos >= themeList.size()) {
            Toast.makeText(this, "Выберите тему", Toast.LENGTH_SHORT).show();
            return;
        }

        Theme theme = themeList.get(pos);
        String themeTitle = getThemeTitle(theme);

        StudySchedule schedule = new StudySchedule(
                currentUserId,
                (int) getThemeId(theme),
                themeTitle,
                makeShortTitle(themeTitle),
                selectedDate,
                selectedStartTime,
                selectedEndTime,
                etNote.getText().toString().trim()
        );

        // 1) Мгновенно сохраняем локально и обновляем UI
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                db.studyScheduleDao().insert(schedule);
            } catch (Exception e) {
                Log.w("StudySchedule",
                        "Локальный insert не удался (возможно, повторный ID): "
                                + e.getMessage());
            }

            mainHandler.post(() -> {
                etNote.setText("");
                layoutAddScheduleForm.setVisibility(View.GONE);

                refreshMonthCalendarLocal();
                loadScheduleForSelectedDateLocal();

                Toast.makeText(StudyScheduleActivity.this,
                        "Занятие добавлено", Toast.LENGTH_SHORT).show();
            });
        });

        // 2) В фоне — синхронизация с сервером, ошибки не мешают пользователю
        repository.createStudySchedule(schedule, new Repository.DataCallback<StudySchedule>() {
            @Override
            public void onSuccess(StudySchedule data) {
                refreshMonthCalendarFromServer();
                loadScheduleForSelectedDateFromServer();
            }

            @Override
            public void onError(String error) {
                Log.w("StudySchedule",
                        "Серверная синхронизация при создании: " + error);
            }
        });
    }

    // Проверяет корректность даты и времени: не в прошлом, окончание позже начала
    private boolean isSelectedDateAndTimeValid() {
        if (selectedStartTime.compareTo(selectedEndTime) >= 0) {
            Toast.makeText(this, "Время окончания должно быть позже времени начала",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            Calendar now = Calendar.getInstance();

            Calendar todayStart = Calendar.getInstance();
            todayStart.set(Calendar.HOUR_OF_DAY, 0);
            todayStart.set(Calendar.MINUTE, 0);
            todayStart.set(Calendar.SECOND, 0);
            todayStart.set(Calendar.MILLISECOND, 0);

            Calendar selectedDay = Calendar.getInstance();
            selectedDay.setTime(dbDateFormat.parse(selectedDate));
            selectedDay.set(Calendar.HOUR_OF_DAY, 0);
            selectedDay.set(Calendar.MINUTE, 0);
            selectedDay.set(Calendar.SECOND, 0);
            selectedDay.set(Calendar.MILLISECOND, 0);

            if (selectedDay.before(todayStart)) {
                Toast.makeText(this, "Нельзя добавить занятие на прошедшую дату",
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            String[] startParts = selectedStartTime.split(":");
            Calendar selectedStartDateTime = Calendar.getInstance();
            selectedStartDateTime.setTime(dbDateFormat.parse(selectedDate));
            selectedStartDateTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(startParts[0]));
            selectedStartDateTime.set(Calendar.MINUTE, Integer.parseInt(startParts[1]));
            selectedStartDateTime.set(Calendar.SECOND, 0);
            selectedStartDateTime.set(Calendar.MILLISECOND, 0);

            if (!selectedStartDateTime.after(now)) {
                Toast.makeText(this, "Нельзя добавить занятие на уже прошедшее время",
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка проверки даты и времени", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    // Мгновенная перестройка календаря из локальной БД
    private void refreshMonthCalendarLocal() {
        if (currentUserId <= 0) return;

        visibleMonthCalendar.set(Calendar.DAY_OF_MONTH, 1);

        String monthTitle = monthTitleFormat.format(visibleMonthCalendar.getTime());
        tvMonthTitle.setText(capitalize(monthTitle));

        Calendar start = (Calendar) visibleMonthCalendar.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeek = start.get(Calendar.DAY_OF_WEEK);
        int mondayBasedOffset = firstDayOfWeek == Calendar.SUNDAY ? 6 : firstDayOfWeek - 2;
        start.add(Calendar.DAY_OF_MONTH, -mondayBasedOffset);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 41);

        final String startDate = dbDateFormat.format(start.getTime());
        final String endDate = dbDateFormat.format(end.getTime());
        final int visibleMonth = visibleMonthCalendar.get(Calendar.MONTH);
        final String today = dbDateFormat.format(Calendar.getInstance().getTime());
        final Calendar startCopy = (Calendar) start.clone();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<StudySchedule> monthSchedules =
                    db.studyScheduleDao().getByDateRange(currentUserId, startDate, endDate);

            List<StudyCalendarAdapter.CalendarDay> days =
                    buildCalendarDays(monthSchedules, startCopy, visibleMonth, today);

            mainHandler.post(() -> calendarAdapter.setItems(days));
        });
    }

    // Перестройка календаря через серверный репозиторий (фон)
    private void refreshMonthCalendarFromServer() {
        if (currentUserId <= 0) return;

        visibleMonthCalendar.set(Calendar.DAY_OF_MONTH, 1);

        Calendar start = (Calendar) visibleMonthCalendar.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeek = start.get(Calendar.DAY_OF_WEEK);
        int mondayBasedOffset = firstDayOfWeek == Calendar.SUNDAY ? 6 : firstDayOfWeek - 2;
        start.add(Calendar.DAY_OF_MONTH, -mondayBasedOffset);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 41);

        final String startDate = dbDateFormat.format(start.getTime());
        final String endDate = dbDateFormat.format(end.getTime());
        final int visibleMonth = visibleMonthCalendar.get(Calendar.MONTH);
        final String today = dbDateFormat.format(Calendar.getInstance().getTime());
        final Calendar startCopy = (Calendar) start.clone();

        repository.getStudyScheduleForRange(
                currentUserId,
                startDate,
                endDate,
                new Repository.DataCallback<List<StudySchedule>>() {
                    @Override
                    public void onSuccess(List<StudySchedule> monthSchedules) {
                        List<StudyCalendarAdapter.CalendarDay> days =
                                buildCalendarDays(monthSchedules, startCopy, visibleMonth, today);
                        calendarAdapter.setItems(days);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w("StudySchedule",
                                "Ошибка серверной загрузки месяца: " + error);
                    }
                }
        );
    }

    // Собирает 42 дня сетки календаря с проставленными метками
    private List<StudyCalendarAdapter.CalendarDay> buildCalendarDays(
            List<StudySchedule> monthSchedules,
            Calendar startCopy,
            int visibleMonth,
            String today) {

        Map<String, Integer> countByDate = new HashMap<>();

        if (monthSchedules != null) {
            for (StudySchedule schedule : monthSchedules) {
                if (schedule == null || schedule.scheduleDate == null) continue;

                Integer oldCount = countByDate.get(schedule.scheduleDate);
                countByDate.put(schedule.scheduleDate,
                        oldCount == null ? 1 : oldCount + 1);
            }
        }

        List<StudyCalendarAdapter.CalendarDay> days = new ArrayList<>();
        Calendar cursor = (Calendar) startCopy.clone();

        for (int i = 0; i < 42; i++) {
            String date = dbDateFormat.format(cursor.getTime());
            int dayNumber = cursor.get(Calendar.DAY_OF_MONTH);
            boolean currentMonth = cursor.get(Calendar.MONTH) == visibleMonth;
            boolean selected = date.equals(selectedDate);
            boolean isToday = date.equals(today);
            int eventsCount = countByDate.containsKey(date)
                    ? countByDate.get(date) : 0;

            days.add(new StudyCalendarAdapter.CalendarDay(
                    date,
                    String.valueOf(dayNumber),
                    currentMonth,
                    selected,
                    isToday,
                    eventsCount
            ));

            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        return days;
    }

    // Мгновенный показ списка занятий дня из локальной БД
    private void loadScheduleForSelectedDateLocal() {
        if (currentUserId <= 0) return;

        final String date = selectedDate;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<StudySchedule> schedules = db.studyScheduleDao().getByDate(currentUserId, date);

            mainHandler.post(() -> applySchedulesForSelectedDay(schedules));
        });
    }

    // Фоновое обновление списка занятий дня через сервер
    private void loadScheduleForSelectedDateFromServer() {
        if (currentUserId <= 0) return;

        final String date = selectedDate;

        repository.getStudyScheduleForDate(currentUserId, date,
                new Repository.DataCallback<List<StudySchedule>>() {
                    @Override
                    public void onSuccess(List<StudySchedule> schedules) {
                        applySchedulesForSelectedDay(schedules);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w("StudySchedule",
                                "Ошибка серверной загрузки дня: " + error);
                    }
                });
    }

    private void applySchedulesForSelectedDay(List<StudySchedule> schedules) {
        scheduleAdapter.setItems(schedules);
        updateSelectedDayTitle();

        if (schedules == null || schedules.isEmpty()) {
            tvEmptySchedule.setVisibility(View.VISIBLE);
            recyclerSchedule.setVisibility(View.GONE);
        } else {
            tvEmptySchedule.setVisibility(View.GONE);
            recyclerSchedule.setVisibility(View.VISIBLE);
        }
    }

    private void updateSelectedDayTitle() {
        if (tvSelectedDayTitle != null) {
            tvSelectedDayTitle.setText("Занятия на " + formatVisibleDate(selectedDate));
        }
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();

        try {
            calendar.setTime(dbDateFormat.parse(selectedDate));
        } catch (Exception ignored) {
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(year, month, dayOfMonth);

                    selectedDate = dbDateFormat.format(selectedCalendar.getTime());
                    tvSelectedDate.setText(formatVisibleDate(selectedDate));

                    visibleMonthCalendar.set(Calendar.YEAR, year);
                    visibleMonthCalendar.set(Calendar.MONTH, month);
                    visibleMonthCalendar.set(Calendar.DAY_OF_MONTH, 1);

                    refreshMonthCalendarLocal();
                    loadScheduleForSelectedDateLocal();
                    refreshMonthCalendarFromServer();
                    loadScheduleForSelectedDateFromServer();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void showTimePicker(boolean isStartTime) {
        String currentTime = isStartTime ? selectedStartTime : selectedEndTime;
        String[] parts = currentTime.split(":");

        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    String time = String.format(
                            Locale.getDefault(),
                            "%02d:%02d",
                            selectedHour,
                            selectedMinute
                    );

                    if (isStartTime) {
                        selectedStartTime = time;
                        tvStartTime.setText(time);
                    } else {
                        selectedEndTime = time;
                        tvEndTime.setText(time);
                    }
                },
                hour,
                minute,
                true
        );

        dialog.show();
    }

    // Показывает детали занятия в диалоге с возможностью удаления
    private void showScheduleDetails(StudySchedule schedule) {
        if (schedule == null) {
            return;
        }

        StringBuilder message = new StringBuilder();

        message.append("Дата: ").append(formatVisibleDate(schedule.scheduleDate)).append("\n");
        message.append("Время: ").append(schedule.startTime).append(" — ").append(schedule.endTime).append("\n");
        message.append("Тема: ").append(schedule.themeTitle).append("\n");

        if (schedule.note != null && !schedule.note.trim().isEmpty()) {
            message.append("Заметка: ").append(schedule.note);
        } else {
            message.append("Заметка: нет");
        }

        new AlertDialog.Builder(this)
                .setTitle("Информация о занятии")
                .setMessage(message.toString())
                .setPositiveButton("ОК", null)
                .setNegativeButton("Удалить", (dialog, which) -> deleteSchedule(schedule))
                .show();
    }

    private void deleteSchedule(StudySchedule schedule) {
        // 1) Мгновенно удаляем локально
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                db.studyScheduleDao().delete(schedule);
            } catch (Exception e) {
                Log.w("StudySchedule",
                        "Локальное удаление не удалось: " + e.getMessage());
            }

            mainHandler.post(() -> {
                refreshMonthCalendarLocal();
                loadScheduleForSelectedDateLocal();
                Toast.makeText(StudyScheduleActivity.this,
                        "Занятие удалено", Toast.LENGTH_SHORT).show();
            });
        });

        // 2) В фоне удаляем на сервере
        repository.deleteStudySchedule(schedule, new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                refreshMonthCalendarFromServer();
                loadScheduleForSelectedDateFromServer();
            }

            @Override
            public void onError(String error) {
                Log.w("StudySchedule",
                        "Ошибка удаления на сервере: " + error);
            }
        });
    }

    private String getThemeTitle(Theme theme) {
        if (theme == null) return "Без названия";
        String title = theme.getTitle();
        if (title == null || title.trim().isEmpty()) return "Без названия";
        return title.trim();
    }

    private long getThemeId(Theme theme) {
        if (theme == null) return 0L;
        Long id = theme.getId();
        if (id == null) return 0L;
        return id;
    }

    // Генерирует двухбуквенное сокращение из заголовка темы
    private String makeShortTitle(String title) {
        if (title == null || title.trim().isEmpty()) return "??";

        String[] words = title.trim().split("\\s+");

        if (words.length >= 2) {
            String first = words[0].substring(0, 1);
            String second = words[1].substring(0, 1);
            return (first + second).toUpperCase(Locale.getDefault());
        }

        String clean = title.trim();

        if (clean.length() >= 2) {
            return clean.substring(0, 2).toUpperCase(Locale.getDefault());
        }

        return clean.toUpperCase(Locale.getDefault());
    }

    private String formatVisibleDate(String dbDate) {
        try {
            return visibleDateFormat.format(dbDateFormat.parse(dbDate));
        } catch (Exception e) {
            return dbDate;
        }
    }

    private String capitalize(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String clean = value.trim();
        return clean.substring(0, 1).toUpperCase(new Locale("ru")) + clean.substring(1);
    }

    // Проверяет пересечение нового занятия с уже существующими (в фоновом потоке)
    private boolean isTimeOverlappingBackground() {
        List<StudySchedule> existing =
                db.studyScheduleDao().getByDate(currentUserId, selectedDate);

        if (existing == null || existing.isEmpty()) {
            return false;
        }

        try {
            String[] startParts = selectedStartTime.split(":");
            String[] endParts = selectedEndTime.split(":");

            int newStart = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);
            int newEnd = Integer.parseInt(endParts[0]) * 60 + Integer.parseInt(endParts[1]);

            for (StudySchedule s : existing) {
                String[] sStartParts = s.startTime.split(":");
                String[] sEndParts = s.endTime.split(":");

                int existingStart = Integer.parseInt(sStartParts[0]) * 60 + Integer.parseInt(sStartParts[1]);
                int existingEnd = Integer.parseInt(sEndParts[0]) * 60 + Integer.parseInt(sEndParts[1]);

                if (newStart < existingEnd && newEnd > existingStart) {
                    return true;
                }
            }

        } catch (Exception e) {
            return true;
        }

        return false;
    }
}