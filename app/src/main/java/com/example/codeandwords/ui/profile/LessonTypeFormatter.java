package com.example.codeandwords.ui.profile;

import java.util.Locale;

// Утилита для преобразования внутренних кодов типов уроков в человекочитаемые строки.
public final class LessonTypeFormatter {

    private LessonTypeFormatter() {
    }

    // Возвращает полный объект с заголовком, описанием и иконкой по коду типа урока.
    public static LessonTypeUi format(String rawType) {
        String normalized = normalize(rawType);

        switch (normalized) {
            case "SPRINT":
                return new LessonTypeUi("Спринт", "Скоростная тренировка", "⚡");

            case "MATCHING":
                return new LessonTypeUi("Сопоставление", "Пары терминов", "⇄");

            case "WRITE_WORD":
            case "WRITING":
                return new LessonTypeUi("Правописание", "Ввод перевода", "Aa");

            case "LISTENING":
                return new LessonTypeUi("Аудирование", "Выбор перевода на слух", "♪");

            case "THEORY":
                return new LessonTypeUi("Теория", "Изучение материала", "T");

            case "TRAINING_LISTENING":
                return new LessonTypeUi("Аудирование", "Тренировка", "♪");

            case "TRAINING_WORDS":
                return new LessonTypeUi("Слова", "Тренировка правописания", "Aa");

            case "TRAINING_MISTAKES":
                return new LessonTypeUi("Ошибки", "Работа над ошибками", "↺");

            case "LEARNED_WORDS":
                return new LessonTypeUi("Выученные слова", "Просмотр словаря", "★");

            case "DICTIONARY":
                return new LessonTypeUi("Словарь", "Личный словарь", "★");

            default:
                return new LessonTypeUi(prettifyFallback(rawType), "Занятие", "✓");
        }
    }

    public static String getDisplayName(String rawType) {
        return format(rawType).getTitle();
    }

    public static String getSubtitle(String rawType) {
        return format(rawType).getSubtitle();
    }

    private static String normalize(String rawType) {
        if (rawType == null || rawType.trim().isEmpty()) {
            return "";
        }

        return rawType.trim().toUpperCase(Locale.ROOT);
    }

    // Преобразует snake_case строку в Title Case как запасной вариант
    private static String prettifyFallback(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "Тренировка";
        }

        String value = raw.trim();

        if (!value.contains("_")) {
            return value;
        }

        String[] parts = value.split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;

            if (builder.length() > 0) {
                builder.append(" ");
            }

            String cleanPart = part.trim().toLowerCase(Locale.ROOT);
            builder.append(cleanPart.substring(0, 1).toUpperCase(Locale.ROOT));

            if (cleanPart.length() > 1) {
                builder.append(cleanPart.substring(1));
            }
        }

        return builder.length() > 0 ? builder.toString() : "Тренировка";
    }

    // DTO для отображения типа урока в UI
    public static class LessonTypeUi {
        private final String title;
        private final String subtitle;
        private final String icon;

        public LessonTypeUi(String title, String subtitle, String icon) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
        }

        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public String getIcon() { return icon; }
    }
}