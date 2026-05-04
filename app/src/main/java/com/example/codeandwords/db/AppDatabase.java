package com.example.codeandwords.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.codeandwords.model.Achievement;
import com.example.codeandwords.model.DailyQuest;
import com.example.codeandwords.model.Friend;
import com.example.codeandwords.model.LessonHistory;
import com.example.codeandwords.model.StudySchedule;
import com.example.codeandwords.model.Team;
import com.example.codeandwords.model.TeamChallenge;
import com.example.codeandwords.model.TeamChallengeProgress;
import com.example.codeandwords.model.TeamMember;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.User;
import com.example.codeandwords.model.UserAchievement;
import com.example.codeandwords.model.UserStats;
import com.example.codeandwords.model.UserWord;
import com.example.codeandwords.model.Word;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                User.class,
                Theme.class,
                Word.class,
                Achievement.class,
                DailyQuest.class,
                UserAchievement.class,
                UserWord.class,
                UserStats.class,
                LessonHistory.class,
                StudySchedule.class,
                Friend.class,
                Team.class,
                TeamMember.class,
                TeamChallenge.class,
                TeamChallengeProgress.class
        },
        version = 17,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract UserDao userDao();
    public abstract ThemeDao themeDao();
    public abstract WordDao wordDao();
    public abstract AchievementDao achievementDao();
    public abstract DailyQuestDao dailyQuestDao();
    public abstract UserWordDao userWordDao();
    public abstract UserStatsDao userStatsDao();
    public abstract LessonHistoryDao lessonHistoryDao();
    public abstract StudyScheduleDao studyScheduleDao();
    public abstract FriendDao friendDao();
    public abstract TeamDao teamDao();
    public abstract TeamMemberDao teamMemberDao();
    public abstract TeamChallengeDao teamChallengeDao();
    public abstract TeamChallengeProgressDao teamChallengeProgressDao();

    private static volatile AppDatabase INSTANCE;

    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(4);

    private static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS team_challenge_progress");
            database.execSQL("DROP TABLE IF EXISTS team_challenges");
            database.execSQL("DROP TABLE IF EXISTS team_members");
            database.execSQL("DROP TABLE IF EXISTS teams");

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS teams (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "team_name TEXT, " +
                            "owner_id INTEGER NOT NULL)"
            );

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS team_members (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "team_id INTEGER NOT NULL, " +
                            "user_id INTEGER NOT NULL, " +
                            "created_at TEXT)"
            );

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS team_challenges (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "team_id INTEGER NOT NULL, " +
                            "title TEXT, " +
                            "condition_type TEXT, " +
                            "target_value INTEGER NOT NULL, " +
                            "xp_first INTEGER NOT NULL, " +
                            "xp_second INTEGER NOT NULL, " +
                            "xp_other INTEGER NOT NULL, " +
                            "is_completed INTEGER NOT NULL, " +
                            "winner_user_id INTEGER, " +
                            "created_at TEXT, " +
                            "completed_at TEXT)"
            );

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS team_challenge_progress (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "challenge_id INTEGER NOT NULL, " +
                            "team_id INTEGER NOT NULL, " +
                            "user_id INTEGER NOT NULL, " +
                            "progress INTEGER NOT NULL, " +
                            "is_completed INTEGER NOT NULL, " +
                            "completed_at TEXT, " +
                            "place INTEGER, " +
                            "awarded_xp INTEGER NOT NULL)"
            );
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "codeandwords_database"
                            )
                            .addMigrations(MIGRATION_15_16)
                            .fallbackToDestructiveMigration()
                            .build();

                    databaseWriteExecutor.execute(() -> forceInitDatabase(INSTANCE));
                }
            }
        }

        return INSTANCE;
    }

    public static AppDatabase getInstance(final Context context) {
        return getDatabase(context);
    }

    private static void forceInitDatabase(AppDatabase db) {
        try {
            ThemeDao themeDao = db.themeDao();
            WordDao wordDao = db.wordDao();
            AchievementDao achievementDao = db.achievementDao();

            List<Theme> themesInDb = themeDao.getAllThemes();

            boolean shouldSeed = themesInDb == null || themesInDb.isEmpty();

            if (!shouldSeed) {
                for (Theme t : themesInDb) {
                    if (t.getTheoryText() == null || t.getTheoryText().trim().isEmpty()) {
                        shouldSeed = true;
                        break;
                    }
                }
            }

            if (!shouldSeed) {
                return;
            }

            themeDao.deleteAll();
            wordDao.deleteAll();
            achievementDao.deleteAll();

            String gitTheory = "Система контроля версий Git позволяет разработчикам работать вместе, не мешая друг другу. " +
                    "Главное место хранения вашего кода называется [[Репозиторий|Repository]]. " +
                    "Когда вы написали кусок кода и хотите его сохранить в историю, вы делаете сохранение, которое называется [[Фиксация|Commit]].\n\n" +
                    "Чтобы несколько человек могли работать параллельно над разными задачами, создается отдельная [[Ветка|Branch]]. " +
                    "После того как задача завершена, изменения из вашей ветки объединяются с главным кодом через [[Слияние|Merge]].\n\n" +
                    "Перед началом работы утром всегда не забывайте делать [[Получение|Pull]], чтобы скачать самые свежие обновления от ваших коллег.";

            String javaTheory = "Java — это строго типизированный язык программирования. " +
                    "Одной из главных концепций здесь является [[Инкапсуляция|Encapsulation]], которая скрывает важные данные внутри класса от случайного изменения.\n\n" +
                    "Классы могут перенимать свойства и методы друг друга — этот мощный механизм называется [[Наследование|Inheritance]]. " +
                    "А вот способность одного и того же метода вести себя по-разному в зависимости от того, кто его вызывает, называется [[Полиморфизм|Polymorphism]].\n\n" +
                    "Чтобы задать строгие правила того, что должен уметь делать класс, используется [[Интерфейс|Interface]]. " +
                    "В самом конце написанный вами текстовый код переводится в понятный машине байт-код. Этим занимается специальная программа — [[Компилятор|Compiler]].";

            String sqlTheory = "Для надежного хранения огромных массивов информации используется [[База данных|Database]]. " +
                    "Чтобы получить список пользователей или добавить новый товар, разработчик пишет специальный текстовый [[Запрос|Query]].\n\n" +
                    "Чтобы не перепутать двух пользователей с одинаковыми именами, каждая запись в таблице должна иметь свой уникальный идентификатор, который называется [[Первичный ключ|Primary Key]].\n\n" +
                    "Часто данные разбиты на разные таблицы. Если нам нужно получить эти данные вместе за один раз, мы используем операцию объединения — [[Соединение|Join]].";

            List<Theme> themes = new ArrayList<>();
            themes.add(new Theme(1L, "Git Basics", "Система контроля версий", "Easy", gitTheory));
            themes.add(new Theme(2L, "Java Core", "Основы языка Java и ООП", "Medium", javaTheory));
            themes.add(new Theme(3L, "SQL & Data", "Базы данных и запросы", "Hard", sqlTheory));
            themeDao.insertAll(themes);

            List<Word> words = new ArrayList<>();
            words.add(new Word(1L, 1L, "Repository", "Репозиторий", "Хранилище кода", "[rɪˈpɒzɪtəri]", "I cloned the repository."));
            words.add(new Word(2L, 1L, "Commit", "Фиксация", "Сохранение изменений", "[kəˈmɪt]", "Make a commit often."));
            words.add(new Word(3L, 1L, "Merge", "Слияние", "Объединение веток", "[mɜːdʒ]", "Merge feature branch to main."));
            words.add(new Word(4L, 1L, "Branch", "Ветка", "Параллельная версия", "[brɑːntʃ]", "Create a new branch."));
            words.add(new Word(5L, 1L, "Pull", "Получение", "Загрузка изменений", "[pʊl]", "Pull the latest changes."));

            words.add(new Word(6L, 2L, "Inheritance", "Наследование", "Механизм перенимания свойств", "[ɪnˈherɪtəns]", "Java supports single inheritance."));
            words.add(new Word(7L, 2L, "Polymorphism", "Полиморфизм", "Много форм одного метода", "[ˌpɒlɪˈmɔːfɪzm]", "Polymorphism increases flexibility."));
            words.add(new Word(8L, 2L, "Encapsulation", "Инкапсуляция", "Сокрытие данных", "[ɪnˌkæpsjuˈleɪʃn]", "Use private fields for encapsulation."));
            words.add(new Word(9L, 2L, "Interface", "Интерфейс", "Контракт для классов", "[ˈɪntəfeɪs]", "Implements Serializable interface."));
            words.add(new Word(10L, 2L, "Compiler", "Компилятор", "Переводчик кода в байт-код", "[kəmˈpaɪlə(r)]", "The compiler checks for errors."));

            words.add(new Word(11L, 3L, "Query", "Запрос", "Обращение к базе данных", "[ˈkwɪəri]", "Write a SQL query."));
            words.add(new Word(12L, 3L, "Database", "База данных", "Организованное хранение", "[ˈdeɪtəbeɪs]", "Connect to the database."));
            words.add(new Word(13L, 3L, "Primary Key", "Первичный ключ", "Уникальный идентификатор", "[ˈpraɪməri kiː]", "ID is usually a primary key."));
            words.add(new Word(14L, 3L, "Join", "Соединение", "Объединение таблиц", "[dʒɔɪn]", "Inner join returns matching rows."));
            wordDao.insertAll(words);

            List<Achievement> achievements = new ArrayList<>();
            achievements.add(new Achievement(1L, "Ударный рекорд", "Серия входов в приложение подряд", 50, "LOGIN_STREAK", 7, 7, "ic_ach_streak"));
            achievements.add(new Achievement(2L, "Максимум опыта", "Максимум опыта, набранного за день", 60, "MAX_XP_DAY", 100, 100, "ic_ach_max_day_xp"));
            achievements.add(new Achievement(3L, "Уроки без ошибок", "Несколько уроков подряд без ошибок", 70, "PERFECT_STREAK", 5, 5, "ic_ach_perfect_streak"));
            achievements.add(new Achievement(4L, "Проснись и пой", "Пройти 10 уроков до 9 утра", 80, "EARLY_BIRD", 10, 10, "ic_ach_early_bird"));
            achievements.add(new Achievement(5L, "Техник", "Исправить 75 ошибок", 80, "ERROR_FIXER", 75, 75, "ic_ach_technician"));
            achievements.add(new Achievement(6L, "Миссия выполнима", "Выполнить 100 заданий", 100, "TASK_MASTER", 100, 100, "ic_ach_mission"));
            achievements.add(new Achievement(7L, "Под покровом ночи", "Пройти 100 уроков после 22:00", 100, "NIGHT_OWL", 100, 100, "ic_ach_night"));
            achievements.add(new Achievement(8L, "Вершины опыта", "Заработать 7500 очков опыта", 150, "TOTAL_XP", 7500, 7500, "ic_ach_xp_peak"));
            achievements.add(new Achievement(9L, "В яблочко", "Пройти 50 уроков без ошибок", 120, "PERFECT_TOTAL", 50, 50, "ic_ach_bullseye"));
            achievements.add(new Achievement(10L, "Спринтер", "Заработать 40 очков опыта в заданиях на время", 60, "SPRINT_XP", 40, 40, "ic_ach_sprinter"));
            achievementDao.insertAll(achievements);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}