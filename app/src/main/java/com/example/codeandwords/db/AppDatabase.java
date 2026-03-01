package com.example.codeandwords.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.codeandwords.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {User.class, Theme.class, Word.class, Achievement.class, DailyQuest.class, UserAchievement.class},
        version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract UserDao userDao();
    public abstract ThemeDao themeDao();
    public abstract WordDao wordDao();
    public abstract AchievementDao achievementDao();
    public abstract DailyQuestDao dailyQuestDao();

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // 1. Создаем базу с новым именем для 100% чистого старта
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "code_and_words_sync_db")
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries() // РАЗРЕШАЕМ СИНХРОННУЮ РАБОТУ (решает проблему гонки потоков)
                            .build();

                    // 2. СИНХРОННАЯ ИНИЦИАЛИЗАЦИЯ (Ни один экран не откроется, пока это не выполнится)
                    forceInitDatabase(INSTANCE);
                }
            }
        }
        return INSTANCE;
    }

    private static void forceInitDatabase(AppDatabase db) {
        ThemeDao themeDao = db.themeDao();
        List<Theme> themesInDb = themeDao.getAllThemes();

        // Проверяем, пустая ли база или в ней старые темы без теории
        boolean needsUpdate = false;
        if (themesInDb.isEmpty()) {
            needsUpdate = true;
        } else {
            for (Theme t : themesInDb) {
                if (t.getTheoryText() == null || t.getTheoryText().trim().isEmpty()) {
                    needsUpdate = true;
                    break;
                }
            }
        }

        if (needsUpdate) {
            // ВСТРОЕННАЯ КОМАНДА ROOM: Полностью стирает все таблицы (гарантия очистки)
            db.clearAllTables();

            WordDao wordDao = db.wordDao();
            AchievementDao achievementDao = db.achievementDao();

            // 1. ТЕКСТЫ ТЕОРИИ
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
                    "Чтобы задать строгие правила (контракт) того, что должен уметь делать класс, используется [[Интерфейс|Interface]]. " +
                    "В самом конце написанный вами текстовый код переводится в понятный машине байт-код. Этим занимается специальная программа — [[Компилятор|Compiler]].";

            String sqlTheory = "Для надежного хранения огромных массивов информации используется [[База данных|Database]]. " +
                    "Чтобы получить список пользователей или добавить новый товар, разработчик пишет специальный текстовый [[Запрос|Query]].\n\n" +
                    "Чтобы не перепутать двух пользователей с одинаковыми именами, каждая запись в таблице должна иметь свой уникальный идентификатор (ID), который называется [[Первичный ключ|Primary Key]].\n\n" +
                    "Часто данные разбиты на разные таблицы (например, \"Пользователи\" и \"Их заказы\"). Если нам нужно получить эти данные вместе за один раз, мы используем операцию объединения — [[Соединение|Join]].";

            // 2. ВСТАВЛЯЕМ ТЕМЫ
            List<Theme> themes = new ArrayList<>();
            themes.add(new Theme(1L, "Git Basics", "Система контроля версий", "Easy", gitTheory));
            themes.add(new Theme(2L, "Java Core", "Основы языка Java и ООП", "Medium", javaTheory));
            themes.add(new Theme(3L, "SQL & Data", "Базы данных и запросы", "Hard", sqlTheory));
            themeDao.insertAll(themes);

            // 3. ВСТАВЛЯЕМ СЛОВА
            List<Word> allWords = new ArrayList<>();
            allWords.add(new Word(1L, 1L, "Repository", "Репозиторий", "Хранилище кода", "[rɪˈpɒzɪtəri]", "I cloned the repository."));
            allWords.add(new Word(2L, 1L, "Commit", "Фиксация", "Сохранение изменений", "[kəˈmɪt]", "Make a commit often."));
            allWords.add(new Word(3L, 1L, "Merge", "Слияние", "Объединение веток", "[mɜːdʒ]", "Merge feature branch to main."));
            allWords.add(new Word(4L, 1L, "Branch", "Ветка", "Параллельная версия", "[brɑːntʃ]", "Create a new branch."));
            allWords.add(new Word(5L, 1L, "Pull", "Получение", "Загрузка изменений", "[pʊl]", "Pull the latest changes."));

            allWords.add(new Word(6L, 2L, "Inheritance", "Наследование", "Механизм перенимания свойств", "[ɪnˈherɪtəns]", "Java supports single inheritance."));
            allWords.add(new Word(7L, 2L, "Polymorphism", "Полиморфизм", "Много форм одного метода", "[ˌpɒlɪˈmɔːfɪzm]", "Polymorphism increases flexibility."));
            allWords.add(new Word(8L, 2L, "Encapsulation", "Инкапсуляция", "Сокрытие данных", "[ɪnˌkæpsjuˈleɪʃn]", "Use private fields for encapsulation."));
            allWords.add(new Word(9L, 2L, "Interface", "Интерфейс", "Контракт для классов", "[ˈɪntəfeɪs]", "Implements Serializable interface."));
            allWords.add(new Word(10L, 2L, "Compiler", "Компилятор", "Переводчик кода в байт-код", "[kəmˈpaɪlə(r)]", "The compiler checks for errors."));

            allWords.add(new Word(11L, 3L, "Query", "Запрос", "Обращение к базе данных", "[ˈkwɪəri]", "Write a SQL query."));
            allWords.add(new Word(12L, 3L, "Database", "База данных", "Организованное хранение", "[ˈdeɪtəbeɪs]", "Connect to the database."));
            allWords.add(new Word(13L, 3L, "Primary Key", "Первичный ключ", "Уникальный идентификатор", "[ˈpraɪməri kiː]", "ID is usually a primary key."));
            allWords.add(new Word(14L, 3L, "Join", "Соединение", "Объединение таблиц", "[dʒɔɪn]", "Inner join returns matching rows."));
            wordDao.insertAll(allWords);

            // 4. ВСТАВЛЯЕМ АЧИВКИ
            List<Achievement> achs = new ArrayList<>();
            achs.add(new Achievement(1L, "Техник", "Изучить 10 технических терминов", 25, "WORDS", 10, 10, "ic_ach_technician"));
            achs.add(new Achievement(2L, "Идеальная неделя", "Заходить 7 дней подряд", 50, "STREAK", 7, 7, "ic_ach_perfect_week"));
            achs.add(new Achievement(3L, "Под покровом ночи", "Учиться после 22:00", 75, "TIME", 1, 1, "ic_ach_night"));
            achs.add(new Achievement(4L, "Знаток легенд", "Пройти тему 'Легенды'", 50, "THEME", 1, 1, "ic_ach_legend"));
            achs.add(new Achievement(5L, "Миссия выполнима", "Выполнить 10 квестов", 50, "QUESTS", 10, 10, "ic_ach_mission"));
            achs.add(new Achievement(6L, "Вершины опыта", "Набрать 200 XP", 200, "XP", 200, 200, "ic_ach_xp_peak"));
            achs.add(new Achievement(7L, "В яблочко!", "5 раз пройти игру без ошибок", 50, "PERFECT_GAME", 5, 5, "ic_ach_bullseye"));
            achs.add(new Achievement(8L, "Спринтер", "Сыграть в Спринт 5 раз", 40, "SPRINT_GAME", 5, 5, "ic_ach_sprinter"));
            achievementDao.insertAll(achs);
        }
    }
}