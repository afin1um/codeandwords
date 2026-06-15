package com.example.codeandwords.ui.profile;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.LessonHistory;
import com.example.codeandwords.model.ThemeProgressStats;
import com.example.codeandwords.model.UserOverallStats;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

// Экран детальной статистики: общие показатели, прогресс по темам, история уроков и график активности.
public class UserStatisticsActivity extends AppCompatActivity {

    // Количество строк в превью-списках
    private static final int PREVIEW_LIMIT = 5;

    private ImageButton btnBackStatistics;
    private TextView tvStatsLessons;
    private TextView tvStatsAccuracy;
    private TextView tvStatsFixedErrors;
    private TextView tvStatsLearnedWords;
    private TextView tvStatsXp;
    private TextView tvStatsLeague;
    private LinearLayout chartContainer;
    private TextView tvChartEmpty;
    private RecyclerView rvThemeProgress;
    private RecyclerView rvRecentLessons;
    private TextView tvThemeProgressEmpty;
    private TextView tvRecentLessonsEmpty;
    private MaterialButton btnShowAllThemes;
    private MaterialButton btnShowAllLessons;
    private ProgressBar pbStatistics;

    private Repository repository;
    private ThemeProgressAdapter themeProgressAdapter;
    private RecentLessonsAdapter recentLessonsAdapter;

    private final List<ThemeProgressStats> allThemeProgressItems = new ArrayList<>();
    private final List<LessonHistory> allRecentLessonItems = new ArrayList<>();

    // Форматы дат для ключей и меток графика в локальном часовом поясе
    private final SimpleDateFormat dayKeyFormat;
    private final SimpleDateFormat dayLabelFormat;

    {
        dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dayKeyFormat.setTimeZone(java.util.TimeZone.getDefault());

        dayLabelFormat = new SimpleDateFormat("dd.MM", Locale.getDefault());
        dayLabelFormat.setTimeZone(java.util.TimeZone.getDefault());
    }

    // Счётчик параллельных запросов — прогресс-бар скрывается когда все завершились
    private int loadedRequests = 0;
    private static final int TOTAL_REQUESTS = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_statistics);

        repository = Repository.getInstance(getApplicationContext());

        initViews();
        setupRecyclerViews();
        setupClicks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatistics();
    }

    private void initViews() {
        btnBackStatistics = findViewById(R.id.btnBackStatistics);

        tvStatsLessons = findViewById(R.id.tvStatsLessons);
        tvStatsAccuracy = findViewById(R.id.tvStatsAccuracy);
        tvStatsFixedErrors = findViewById(R.id.tvStatsFixedErrors);
        tvStatsLearnedWords = findViewById(R.id.tvStatsLearnedWords);
        tvStatsXp = findViewById(R.id.tvStatsXp);
        tvStatsLeague = findViewById(R.id.tvStatsLeague);

        chartContainer = findViewById(R.id.chartContainer);
        tvChartEmpty = findViewById(R.id.tvChartEmpty);

        rvThemeProgress = findViewById(R.id.rvThemeProgress);
        rvRecentLessons = findViewById(R.id.rvRecentLessons);

        tvThemeProgressEmpty = findViewById(R.id.tvThemeProgressEmpty);
        tvRecentLessonsEmpty = findViewById(R.id.tvRecentLessonsEmpty);

        btnShowAllThemes = findViewById(R.id.btnShowAllThemes);
        btnShowAllLessons = findViewById(R.id.btnShowAllLessons);

        pbStatistics = findViewById(R.id.pbStatistics);
    }

    private void setupRecyclerViews() {
        themeProgressAdapter = new ThemeProgressAdapter();
        rvThemeProgress.setLayoutManager(new LinearLayoutManager(this));
        rvThemeProgress.setNestedScrollingEnabled(false);
        rvThemeProgress.setAdapter(themeProgressAdapter);

        recentLessonsAdapter = new RecentLessonsAdapter();
        rvRecentLessons.setLayoutManager(new LinearLayoutManager(this));
        rvRecentLessons.setNestedScrollingEnabled(false);
        rvRecentLessons.setAdapter(recentLessonsAdapter);
    }

    private void setupClicks() {
        btnBackStatistics.setOnClickListener(v -> finish());
        btnShowAllThemes.setOnClickListener(v -> showAllThemesBottomSheet());
        btnShowAllLessons.setOnClickListener(v -> showAllLessonsBottomSheet());
    }

    // Запускает все 4 запроса параллельно и сбрасывает счётчик завершённых
    private void loadStatistics() {
        pbStatistics.setVisibility(View.VISIBLE);
        loadedRequests = 0;

        loadOverallStats();
        loadThemeProgress();
        loadRecentLessons();
        loadActivityChart();
    }

    // Загружает общую статистику и заполняет сводный блок
    private void loadOverallStats() {
        repository.getUserOverallStatistics(new Repository.DataCallback<UserOverallStats>() {
            @Override
            public void onSuccess(UserOverallStats data) {
                if (data == null) {
                    setEmptyOverallStats();
                } else {
                    tvStatsLessons.setText(String.valueOf(data.getTotalLessons()));
                    tvStatsAccuracy.setText(data.getAccuracyPercent() + "%");
                    tvStatsFixedErrors.setText(String.valueOf(data.getFixedErrors()));
                    tvStatsLearnedWords.setText(String.valueOf(data.getLearnedWords()));
                    tvStatsXp.setText(String.valueOf(data.getTotalXp()));
                    tvStatsLeague.setText(data.getLeagueIcon() + " " + data.getLeagueTitle());
                }
                checkAllLoaded();
            }

            @Override
            public void onError(String error) {
                setEmptyOverallStats();
                Toast.makeText(UserStatisticsActivity.this,
                        error != null ? error : "Не удалось загрузить статистику",
                        Toast.LENGTH_SHORT).show();
                checkAllLoaded();
            }
        });
    }

    private void setEmptyOverallStats() {
        tvStatsLessons.setText("0");
        tvStatsAccuracy.setText("0%");
        tvStatsFixedErrors.setText("0");
        tvStatsLearnedWords.setText("0");
        tvStatsXp.setText("0");
        tvStatsLeague.setText("🥉 Бронзовая лига");
    }

    // Загружает прогресс по темам и отображает превью
    private void loadThemeProgress() {
        repository.getThemeProgressStatistics(new Repository.DataCallback<List<ThemeProgressStats>>() {
            @Override
            public void onSuccess(List<ThemeProgressStats> data) {
                allThemeProgressItems.clear();
                if (data != null) allThemeProgressItems.addAll(data);
                bindThemeProgressPreview();
                checkAllLoaded();
            }

            @Override
            public void onError(String error) {
                allThemeProgressItems.clear();
                themeProgressAdapter.setItems(new ArrayList<>());
                tvThemeProgressEmpty.setVisibility(View.VISIBLE);
                rvThemeProgress.setVisibility(View.GONE);
                btnShowAllThemes.setVisibility(View.GONE);
                checkAllLoaded();
            }
        });
    }

    // Показывает первые PREVIEW_LIMIT тем; кнопка «Показать все» видна при наличии остальных
    private void bindThemeProgressPreview() {
        if (allThemeProgressItems.isEmpty()) {
            themeProgressAdapter.setItems(new ArrayList<>());
            tvThemeProgressEmpty.setVisibility(View.VISIBLE);
            rvThemeProgress.setVisibility(View.GONE);
            btnShowAllThemes.setVisibility(View.GONE);
            return;
        }

        List<ThemeProgressStats> previewItems = takeFirstItems(allThemeProgressItems, PREVIEW_LIMIT);
        themeProgressAdapter.setItems(previewItems);
        tvThemeProgressEmpty.setVisibility(View.GONE);
        rvThemeProgress.setVisibility(View.VISIBLE);

        if (allThemeProgressItems.size() > PREVIEW_LIMIT) {
            btnShowAllThemes.setVisibility(View.VISIBLE);
            btnShowAllThemes.setText("ПОКАЗАТЬ ВСЕ ТЕМЫ (" + allThemeProgressItems.size() + ")");
        } else {
            btnShowAllThemes.setVisibility(View.GONE);
        }
    }

    // Загружает последние 50 уроков и отображает превью
    private void loadRecentLessons() {
        repository.getRecentLessonHistory(50, new Repository.DataCallback<List<LessonHistory>>() {
            @Override
            public void onSuccess(List<LessonHistory> data) {
                allRecentLessonItems.clear();
                if (data != null) allRecentLessonItems.addAll(data);
                bindRecentLessonsPreview();
                checkAllLoaded();
            }

            @Override
            public void onError(String error) {
                allRecentLessonItems.clear();
                recentLessonsAdapter.setItems(new ArrayList<>());
                tvRecentLessonsEmpty.setVisibility(View.VISIBLE);
                rvRecentLessons.setVisibility(View.GONE);
                btnShowAllLessons.setVisibility(View.GONE);
                checkAllLoaded();
            }
        });
    }

    private void bindRecentLessonsPreview() {
        if (allRecentLessonItems.isEmpty()) {
            recentLessonsAdapter.setItems(new ArrayList<>());
            tvRecentLessonsEmpty.setVisibility(View.VISIBLE);
            rvRecentLessons.setVisibility(View.GONE);
            btnShowAllLessons.setVisibility(View.GONE);
            return;
        }

        List<LessonHistory> previewItems = takeFirstItems(allRecentLessonItems, PREVIEW_LIMIT);
        recentLessonsAdapter.setItems(previewItems);
        tvRecentLessonsEmpty.setVisibility(View.GONE);
        rvRecentLessons.setVisibility(View.VISIBLE);

        if (allRecentLessonItems.size() > PREVIEW_LIMIT) {
            btnShowAllLessons.setVisibility(View.VISIBLE);
            btnShowAllLessons.setText("ПОКАЗАТЬ ВСЕ ТРЕНИРОВКИ (" + allRecentLessonItems.size() + ")");
        } else {
            btnShowAllLessons.setVisibility(View.GONE);
        }
    }

    // Возвращает первые limit элементов из списка
    private <T> List<T> takeFirstItems(List<T> source, int limit) {
        List<T> result = new ArrayList<>();
        if (source == null || source.isEmpty() || limit <= 0) return result;
        int count = Math.min(source.size(), limit);
        for (int i = 0; i < count; i++) result.add(source.get(i));
        return result;
    }

    // Скрывает прогресс-бар когда все параллельные запросы завершились
    private synchronized void checkAllLoaded() {
        loadedRequests++;
        if (loadedRequests >= TOTAL_REQUESTS) {
            pbStatistics.setVisibility(View.GONE);
            loadedRequests = 0;
        }
    }

    // Загружает полную историю уроков для построения столбчатого графика активности
    private void loadActivityChart() {
        repository.getLessonHistoryForStatistics(new Repository.DataCallback<List<LessonHistory>>() {
            @Override
            public void onSuccess(List<LessonHistory> data) {
                buildActivityChart(data != null ? data : new ArrayList<>());
                checkAllLoaded();
            }

            @Override
            public void onError(String error) {
                buildActivityChart(new ArrayList<>());
                checkAllLoaded();
            }
        });
    }

    // Строит столбчатый график за последние 7 дней
    private void buildActivityChart(List<LessonHistory> history) {
        chartContainer.removeAllViews();

        int daysCount = 7;
        int[] lessonsByDay = new int[daysCount];
        String[] labels = new String[daysCount];
        String[] keys = new String[daysCount];

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -(daysCount - 1));

        for (int i = 0; i < daysCount; i++) {
            keys[i] = dayKeyFormat.format(calendar.getTime());
            labels[i] = dayLabelFormat.format(calendar.getTime());
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Подсчёт уроков по дням
        if (history != null) {
            for (LessonHistory item : history) {
                if (item == null || item.finishedAt <= 0) continue;
                Calendar itemCalendar = Calendar.getInstance();
                itemCalendar.setTimeInMillis(item.finishedAt);
                String key = dayKeyFormat.format(itemCalendar.getTime());

                for (int i = 0; i < daysCount; i++) {
                    if (keys[i].equals(key)) {
                        lessonsByDay[i]++;
                        break;
                    }
                }
            }
        }

        int max = 0;
        for (int count : lessonsByDay) if (count > max) max = count;

        tvChartEmpty.setVisibility(max == 0 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < daysCount; i++) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            itemParams.setMargins(4, 0, 4, 0);
            itemLayout.setLayoutParams(itemParams);

            TextView countView = new TextView(this);
            countView.setText(String.valueOf(lessonsByDay[i]));
            countView.setTextColor(0xFF1CB0F6);
            countView.setTextSize(12);
            countView.setGravity(Gravity.CENTER);

            View bar = new View(this);
            int barHeight = max > 0 ? dp(22 + (lessonsByDay[i] * 88 / max)) : dp(12);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(18), barHeight);
            barParams.setMargins(0, 6, 0, 6);
            bar.setLayoutParams(barParams);
            bar.setBackgroundResource(R.drawable.bg_stats_chart_bar);

            TextView labelView = new TextView(this);
            labelView.setText(labels[i]);
            labelView.setTextColor(0xFF8A9AA5);
            labelView.setTextSize(10);
            labelView.setGravity(Gravity.CENTER);

            itemLayout.addView(countView);
            itemLayout.addView(bar);
            itemLayout.addView(labelView);
            chartContainer.addView(itemLayout);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // Открывает BottomSheet с полным списком тем
    private void showAllThemesBottomSheet() {
        if (allThemeProgressItems.isEmpty()) {
            Toast.makeText(this, "Нет данных по темам", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(
                R.layout.dialog_statistics_full_list, null, false);
        TextView tvTitle = sheetView.findViewById(R.id.tvStatisticsDialogTitle);
        TextView tvSubtitle = sheetView.findViewById(R.id.tvStatisticsDialogSubtitle);
        TextView btnClose = sheetView.findViewById(R.id.btnCloseStatisticsDialog);
        RecyclerView rvItems = sheetView.findViewById(R.id.rvStatisticsDialogItems);

        tvTitle.setText("Все темы");
        tvSubtitle.setText("Полный прогресс по темам: " + allThemeProgressItems.size());

        ThemeProgressAdapter dialogAdapter = new ThemeProgressAdapter();
        dialogAdapter.setItems(allThemeProgressItems);

        rvItems.setLayoutManager(new LinearLayoutManager(this));
        rvItems.setAdapter(dialogAdapter);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(sheetView);
        dialog.show();
    }

    // Открывает BottomSheet с полной историей уроков
    private void showAllLessonsBottomSheet() {
        if (allRecentLessonItems.isEmpty()) {
            Toast.makeText(this, "История занятий пока пустая", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(
                R.layout.dialog_statistics_full_list, null, false);
        TextView tvTitle = sheetView.findViewById(R.id.tvStatisticsDialogTitle);
        TextView tvSubtitle = sheetView.findViewById(R.id.tvStatisticsDialogSubtitle);
        TextView btnClose = sheetView.findViewById(R.id.btnCloseStatisticsDialog);
        RecyclerView rvItems = sheetView.findViewById(R.id.rvStatisticsDialogItems);

        tvTitle.setText("Все тренировки");
        tvSubtitle.setText("История последних занятий: " + allRecentLessonItems.size());

        RecentLessonsAdapter dialogAdapter = new RecentLessonsAdapter();
        dialogAdapter.setItems(allRecentLessonItems);

        rvItems.setLayoutManager(new LinearLayoutManager(this));
        rvItems.setAdapter(dialogAdapter);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(sheetView);
        dialog.show();
    }
}