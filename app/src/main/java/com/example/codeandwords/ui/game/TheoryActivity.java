package com.example.codeandwords.ui.game;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;
import com.example.codeandwords.model.Word;
import com.example.codeandwords.ui.base.BaseBackActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Экран теории:
 * - грузит слова темы из таблицы words через Repository.getWordsByTheme;
 * - автоматически делает все английские термины кликабельными;
 * - не ломает уже размеченный текст вида [[Compiler|Compiler]];
 * - показывает popup с переводом, транскрипцией и озвучкой.
 */
public class TheoryActivity extends BaseBackActivity {

    private TextView tvTitle, tvTheoryText;
    private View btnBack;

    private Repository repository;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private Long themeId;
    private String themeTitle;

    /**
     * Словарь терминов текущей темы.
     * key (нормализованный английский термин) -> TermInfo
     */
    private final Map<String, TermInfo> termsDictionary = new LinkedHashMap<>();

    /**
     * Алиасы: вариант написания в тексте -> канонический ключ.
     */
    private final Map<String, String> termAliases = new HashMap<>();

    /**
     * Общий regex для поиска терминов в тексте.
     */
    private Pattern termsAutoPattern;

    private static final class TermInfo {
        final String key;
        final String translation;
        final String transcription;

        TermInfo(String key, String translation, String transcription) {
            this.key = key;
            this.translation = translation;
            this.transcription = transcription;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theory);

        tvTitle = findViewById(R.id.tvTheoryTitle);
        tvTheoryText = findViewById(R.id.tvTheoryText);
        btnBack = findViewById(R.id.btnBackTheory);

        repository = Repository.getInstance(getApplicationContext());

        themeId = getIntent().getLongExtra("THEME_ID", -1);
        themeTitle = getIntent().getStringExtra("THEME_TITLE");

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> goBackToGameSelection());
        }

        initTextToSpeech();

        if (themeId != null && themeId != -1) {
            loadTheory(themeId);
        } else {
            Toast.makeText(this, "Ошибка загрузки теории", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initTextToSpeech() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);

                if (result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = true;
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        goBackToGameSelection();
    }

    private void goBackToGameSelection() {
        Intent intent = new Intent(this, GameSelectionActivity.class);
        intent.putExtra("THEME_ID", themeId != null ? themeId : -1L);
        intent.putExtra("THEME_TITLE", themeTitle);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Главная загрузка:
     * 1. Грузим слова темы (для словаря терминов).
     * 2. Грузим текст темы.
     * 3. Автоматически размечаем термины и показываем.
     */
    private void loadTheory(long themeId) {
        repository.getWordsByTheme(themeId, new Repository.DataCallback<List<Word>>() {
            @Override
            public void onSuccess(List<Word> words) {
                initTermsDictionaryFromWords(themeId, words);
                loadThemeText(themeId);
            }

            @Override
            public void onError(String error) {
                // Даже если слова не загрузились — пробуем показать теорию.
                initTermsDictionaryFromWords(themeId, new ArrayList<>());
                loadThemeText(themeId);
            }
        });
    }

    private void loadThemeText(long themeId) {
        repository.getThemeById(themeId, new Repository.DataCallback<Theme>() {
            @Override
            public void onSuccess(Theme theme) {
                if (theme == null) {
                    tvTheoryText.setText("Для этой темы пока нет теории.");
                    return;
                }

                tvTitle.setText("Теория: " + theme.getTitle());

                String dbText = theme.getTheoryText();

                if (dbText == null || dbText.trim().isEmpty()) {
                    dbText = getHardcodedTheory(themeId);
                }

                if (dbText != null && !dbText.trim().isEmpty()) {
                    dbText = autoMarkTerms(dbText);
                    processInteractiveText(dbText);
                } else {
                    tvTheoryText.setText("Для этой темы пока нет теории.");
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TheoryActivity.this, error, Toast.LENGTH_SHORT).show();
                tvTheoryText.setText("Ошибка загрузки теории.");
            }
        });
    }

    /**
     * Заполняем словарь терминов из таблицы words + добавляем известные алиасы.
     */
    private void initTermsDictionaryFromWords(long currentThemeId, List<Word> words) {
        termsDictionary.clear();
        termAliases.clear();
        termsAutoPattern = null;

        if (words != null) {
            for (Word word : words) {
                if (word == null || isBlank(word.getTerm())) {
                    continue;
                }

                String term = normalizeSpaces(word.getTerm());
                String translation = !isBlank(word.getTranslation())
                        ? word.getTranslation().trim()
                        : term;

                String transcription = !isBlank(word.getTranscription())
                        ? word.getTranscription().trim()
                        : "";

                addTerm(term, translation, transcription);
            }
        }

        addKnownAliasesAndFallbackTerms(currentThemeId);

        buildTermsAutoPattern();
    }

    private void addTerm(String key, String translation, String transcription) {
        if (isBlank(key)) return;

        String normalizedKey = normalizeSpaces(key);

        termsDictionary.put(
                normalizedKey,
                new TermInfo(
                        normalizedKey,
                        translation != null ? translation.trim() : normalizedKey,
                        transcription != null ? transcription.trim() : ""
                )
        );

        addAlias(normalizedKey, normalizedKey);
        addBasicAliases(normalizedKey);
    }

    private void addTermIfAbsent(String key, String translation, String transcription) {
        if (isBlank(key)) return;

        String existing = findCanonicalKey(key);

        if (existing == null) {
            addTerm(key, translation, transcription);
        }
    }

    private void addAlias(String alias, String canonicalKey) {
        if (isBlank(alias) || isBlank(canonicalKey)) return;

        termAliases.put(
                toLookupKey(alias),
                normalizeSpaces(canonicalKey)
        );
    }

    /**
     * Простые автоматические алиасы:
     * - разные тире;
     * - множественное число для однословных английских терминов.
     */
    private void addBasicAliases(String term) {
        if (isBlank(term)) return;

        String normalized = normalizeSpaces(term);

        if (normalized.contains("-")) {
            addAlias(normalized.replace("-", "‑"), normalized);
            addAlias(normalized.replace("-", "–"), normalized);
            addAlias(normalized.replace("-", "—"), normalized);
        }

        if (!normalized.contains(" ")) {
            addAlias(normalized + "s", normalized);
            addAlias(normalized + "es", normalized);

            if (normalized.endsWith("y") && normalized.length() > 1) {
                addAlias(
                        normalized.substring(0, normalized.length() - 1) + "ies",
                        normalized
                );
            }
        }
    }

    /**
     * Добавляем алиасы и недостающие термины для случаев,
     * когда в theory_text слово написано иначе, чем в words.term.
     */
    private void addKnownAliasesAndFallbackTerms(long currentThemeId) {
        switch ((int) currentThemeId) {

            case 1: // Git Basics
                addTermIfAbsent("Git", "Git", "[ɡɪt]");
                addTermIfAbsent("VCS", "Система контроля версий", "[ˌviː siː ˈes]");
                addTermIfAbsent("Snapshot", "Снимок состояния", "[ˈsnæpʃɑːt]");
                addTermIfAbsent("Delta", "Разница между версиями", "[ˈdeltə]");
                addTermIfAbsent("SHA-1 hash", "SHA-1 хэш", "[ˌes eɪtʃ ˌeɪ wʌn hæʃ]");

                addAlias("snapshots", "Snapshot");
                addAlias("deltas", "Delta");
                addAlias("SHA-1 хэш", "SHA-1 hash");
                addAlias("SHA-1 хеш", "SHA-1 hash");
                break;

            case 2: // Java Core
                addTermIfAbsent("Java", "Java", "[ˈdʒɑːvə]");
                addTermIfAbsent("Bytecode", "Байт-код", "[ˈbaɪtkoʊd]");
                addTermIfAbsent("JVM", "Виртуальная машина Java", "[ˌdʒeɪ viː ˈem]");
                addTermIfAbsent("Android", "Android", "[ˈændrɔɪd]");

                addAlias("байт-код", "Bytecode");
                addAlias("байт код", "Bytecode");
                break;

            case 3: // SQL & Data
                addTermIfAbsent("SQL", "Язык структурированных запросов", "[ˌes kjuː ˈel]");
                addTermIfAbsent("DBMS", "Система управления базами данных", "[ˌdiː biː em ˈes]");
                addTermIfAbsent("Structured Query Language", "Язык структурированных запросов", "");
                addTermIfAbsent("Relational Model", "Реляционная модель", "[rɪˈleɪʃənəl ˈmɑːdl]");
                addTermIfAbsent("DDL", "Язык определения данных", "[ˌdiː diː ˈel]");
                addTermIfAbsent("DML", "Язык манипулирования данными", "[ˌdiː em ˈel]");
                addTermIfAbsent("DCL", "Язык управления доступом", "[ˌdiː siː ˈel]");
                addTermIfAbsent("TCL", "Язык управления транзакциями", "[ˌtiː siː ˈel]");
                addTermIfAbsent("Data Definition Language", "Язык определения данных", "");
                addTermIfAbsent("Data Manipulation Language", "Язык манипулирования данными", "");
                addTermIfAbsent("Data Control Language", "Язык управления доступом к данным", "");
                addTermIfAbsent("Transaction Control Language", "Язык управления транзакциями", "");
                addTermIfAbsent("SELECT", "Оператор выборки данных", "[sɪˈlekt]");
                addTermIfAbsent("ACID", "Свойства надёжности транзакций", "[ˈæsɪd]");
                addTermIfAbsent("COUNT", "Функция подсчёта", "[kaʊnt]");
                addTermIfAbsent("SUM", "Функция суммы", "[sʌm]");
                addTermIfAbsent("AVG", "Функция среднего значения", "[ˌeɪ viː ˈdʒiː]");
                addTermIfAbsent("MIN", "Функция минимума", "[mɪn]");
                addTermIfAbsent("MAX", "Функция максимума", "[mæks]");

                addAlias("СУБД", "DBMS");
                break;

            case 4: // HTML & CSS Basics
                addTermIfAbsent("HTML", "Язык гипертекстовой разметки", "[ˌeɪtʃ tiː em ˈel]");
                addTermIfAbsent("CSS", "Каскадные таблицы стилей", "[ˌsiː es ˈes]");
                addTermIfAbsent("HTML5", "HTML пятой версии", "[ˌeɪtʃ tiː em ˈel faɪv]");
                addTermIfAbsent("CSS3", "CSS третьей версии", "[ˌsiː es ˈes θriː]");
                addTermIfAbsent("HyperText Markup Language", "Язык гипертекстовой разметки", "");
                addTermIfAbsent("Cascading Style Sheets", "Каскадные таблицы стилей", "");
                break;

            case 5: // Networking Basics
                addTermIfAbsent("OSI", "Модель взаимодействия открытых систем", "[ˌoʊ es ˈaɪ]");
                addTermIfAbsent("Internet", "Интернет", "[ˈɪntərnet]");
                addTermIfAbsent("LAN", "Локальная сеть", "[læn]");
                addTermIfAbsent("NIC", "Сетевая карта", "[ˌen aɪ ˈsiː]");
                break;

            case 6: // Python Fundamentals
                addTermIfAbsent("Python", "Python", "[ˈpaɪθɑːn]");
                addTermIfAbsent("Dynamic Typing", "Динамическая типизация", "[daɪˈnæmɪk ˈtaɪpɪŋ]");
                addTermIfAbsent("True", "Истина", "[truː]");
                addTermIfAbsent("False", "Ложь", "[fɔːls]");
                addTermIfAbsent("PyPI", "Индекс пакетов Python", "[ˌpaɪ piː ˈaɪ]");
                addTermIfAbsent("def", "Ключевое слово объявления функции", "[def]");
                break;

            case 7: // JavaScript & DOM
                addTermIfAbsent("JavaScript", "JavaScript", "[ˈdʒɑːvəskrɪpt]");
                addTermIfAbsent("Document Object Model", "Объектная модель документа", "");
                addTermIfAbsent("XML", "Расширяемый язык разметки", "[ˌeks em ˈel]");
                addTermIfAbsent("HTML", "Язык гипертекстовой разметки", "[ˌeɪtʃ tiː em ˈel]");
                break;

            case 8: // Linux & Terminal
                addTermIfAbsent("Linux", "Linux", "[ˈlɪnʊks]");
                addTermIfAbsent("Bash", "Командная оболочка Bash", "[bæʃ]");
                addTermIfAbsent("Bourne Again Shell", "Оболочка Bourne Again Shell", "");
                addTermIfAbsent("PID", "Идентификатор процесса", "[ˌpiː aɪ ˈdiː]");
                break;

            case 9: // Docker & Containers
                addTermIfAbsent("Docker", "Платформа контейнеризации", "[ˈdɑːkər]");
                addTermIfAbsent("Containerization", "Контейнеризация", "[kənˌteɪnərəˈzeɪʃn]");
                addTermIfAbsent("Kubernetes", "Система оркестрации контейнеров", "[ˌkuːbərˈnetiːz]");
                break;

            case 10: // Cybersecurity Basics
                addTermIfAbsent("Cybersecurity", "Кибербезопасность", "[ˌsaɪbərsɪˈkjʊrəti]");
                addTermIfAbsent("Firewall", "Межсетевой экран", "[ˈfaɪərwɔːl]");
                addTermIfAbsent("VPN", "Виртуальная частная сеть", "[ˌviː piː ˈen]");
                addTermIfAbsent("Token", "Токен", "[ˈtoʊkən]");
                addTermIfAbsent("SSL", "Протокол защищённого соединения", "[ˌes es ˈel]");
                addTermIfAbsent("IDS", "Система обнаружения вторжений", "[ˌaɪ diː ˈes]");

                // В БД слова называются Security Firewall / Secure VPN / Security Token,
                // но в теории встречается Firewall / VPN / Token.
                copyTermDataIfExists("Firewall", "Security Firewall");
                copyTermDataIfExists("VPN", "Secure VPN");
                copyTermDataIfExists("Token", "Security Token");
                break;

            case 11: // Cloud Computing
                addTermIfAbsent("Cloud", "Облако", "[klaʊd]");
                addTermIfAbsent("Cloud Computing", "Облачные вычисления", "[klaʊd kəmˈpjuːtɪŋ]");
                addTermIfAbsent("Internet", "Интернет", "[ˈɪntərnet]");
                addTermIfAbsent("SLA", "Соглашение об уровне сервиса", "[ˌes el ˈeɪ]");
                break;

            case 12: // API & REST
                addTermIfAbsent("API", "Программный интерфейс приложения", "[ˌeɪ piː ˈaɪ]");
                addTermIfAbsent("REST", "Архитектурный стиль REST", "[rest]");
                addTermIfAbsent("HTTP", "Протокол передачи гипертекста", "[ˌeɪtʃ tiː tiː ˈpiː]");
                addTermIfAbsent("URL", "Адрес ресурса", "[ˌjuː ɑːr ˈel]");
                addTermIfAbsent("Application Programming Interface", "Программный интерфейс приложения", "");
                addTermIfAbsent("Representational State Transfer", "Передача репрезентативного состояния", "");
                addTermIfAbsent("JSON", "Формат обмена данными", "[ˈdʒeɪsɑːn]");
                addTermIfAbsent("Token", "Токен", "[ˈtoʊkən]");

                // В БД слова называются API JSON / API Token,
                // но в теории встречается JSON / Token.
                copyTermDataIfExists("JSON", "API JSON");
                copyTermDataIfExists("Token", "API Token");
                break;

            case 13: // Agile & Scrum
                addTermIfAbsent("Agile", "Гибкая методология разработки", "[ˈædʒaɪl]");
                addTermIfAbsent("Scrum", "Scrum-фреймворк", "[skrʌm]");
                addTermIfAbsent("CI", "Непрерывная интеграция", "[ˌsiː ˈaɪ]");
                break;

            case 14: // Data Structures
                addTermIfAbsent("LIFO", "Последним пришёл — первым вышел", "[ˈlaɪfoʊ]");
                addTermIfAbsent("FIFO", "Первым пришёл — первым вышел", "[ˈfaɪfoʊ]");
                addTermIfAbsent("Hash Function", "Хэш-функция", "[hæʃ ˈfʌŋkʃn]");
                addTermIfAbsent("AVL", "Самобалансирующееся бинарное дерево", "[ˌeɪ viː ˈel]");
                addTermIfAbsent("BFS", "Поиск в ширину", "[ˌbiː ef ˈes]");
                addTermIfAbsent("Big O", "Нотация «О» большое", "[bɪɡ oʊ]");

                addAlias("O(n log n)", "Big O Notation");
                addAlias("O(n)", "Big O Notation");
                addAlias("O(1)", "Big O Notation");
                break;
        }
    }

    /**
     * Если в БД есть только sourceKey (например "API Token"),
     * а в тексте встречается newKey (например "Token"),
     * создаём отдельную запись newKey с теми же переводом и транскрипцией.
     */
    private void copyTermDataIfExists(String newKey, String sourceKey) {
        if (isBlank(newKey) || isBlank(sourceKey)) return;

        TermInfo source = getTermData(sourceKey);

        if (source == null) return;

        String existing = findCanonicalKey(newKey);

        if (existing == null) {
            addTerm(newKey, source.translation, source.transcription);
        } else {
            termsDictionary.put(
                    existing,
                    new TermInfo(
                            existing,
                            source.translation,
                            source.transcription
                    )
            );
        }
    }

    /**
     * Один большой regex из всех терминов и алиасов.
     * Сначала длинные — чтобы "Primary Key" находился раньше, чем просто "Key".
     */
    private void buildTermsAutoPattern() {
        List<String> allTerms = new ArrayList<>();

        for (String key : termsDictionary.keySet()) {
            if (!isBlank(key)) {
                allTerms.add(key);
            }
        }

        for (String alias : termAliases.keySet()) {
            if (!isBlank(alias)) {
                allTerms.add(alias);
            }
        }

        if (allTerms.isEmpty()) {
            termsAutoPattern = null;
            return;
        }

        allTerms.sort((a, b) -> Integer.compare(b.length(), a.length()));

        StringBuilder regex = new StringBuilder();

        for (String term : allTerms) {
            if (isBlank(term)) continue;

            if (regex.length() > 0) {
                regex.append("|");
            }

            regex.append(Pattern.quote(term));
        }

        termsAutoPattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}_])(" + regex + ")(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
    }

    /**
     * Автоматически оборачивает найденные термины в [[Compiler|Compiler]].
     * При этом уже размеченные [[...|...]] не трогает.
     */
    private String autoMarkTerms(String text) {
        if (isBlank(text) || termsAutoPattern == null) {
            return text;
        }

        Pattern alreadyMarkedPattern = Pattern.compile("\\[\\[[\\s\\S]*?\\]\\]");
        Matcher matcher = alreadyMarkedPattern.matcher(text);

        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String plainPart = text.substring(lastEnd, matcher.start());

            result.append(markTermsInPlainSegment(plainPart));

            // Уже размеченный термин оставляем как есть
            result.append(matcher.group());

            lastEnd = matcher.end();
        }

        result.append(markTermsInPlainSegment(text.substring(lastEnd)));

        return result.toString();
    }

    private String markTermsInPlainSegment(String segment) {
        if (isBlank(segment) || termsAutoPattern == null) {
            return segment;
        }

        Matcher matcher = termsAutoPattern.matcher(segment);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String foundText = matcher.group(1);

            String canonicalKey = normalizeTermKey(foundText);

            matcher.appendReplacement(
                    sb,
                    Matcher.quoteReplacement("[[" + foundText + "|" + canonicalKey + "]]")
            );
        }

        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Делает кликабельным каждый [[...|...]] в тексте.
     * Поддерживает оба формата:
     *   [[Compiler|Compiler]]
     *   [[Compiler]]
     */
    private void processInteractiveText(String rawText) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        Pattern pattern = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?\\]\\]");
        Matcher matcher = pattern.matcher(rawText);

        final int linkColor = ContextCompat.getColor(this, R.color.app_blue);

        int lastEnd = 0;

        while (matcher.find()) {
            builder.append(rawText.substring(lastEnd, matcher.start()));

            final String displayWord = matcher.group(1).trim();

            String keyFromMarkup = matcher.group(2) != null
                    ? matcher.group(2).trim()
                    : displayWord;

            final String termKey = normalizeTermKey(keyFromMarkup);

            int startSpan = builder.length();
            builder.append(displayWord);
            int endSpan = builder.length();

            final int spanStart = startSpan;

            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    TextView tv = (TextView) widget;

                    TermInfo termInfo = getTermData(termKey);

                    String ruTranslation = termInfo != null
                            ? termInfo.translation
                            : displayWord;

                    String transcription = termInfo != null
                            ? termInfo.transcription
                            : "";

                    String englishForPopup = termInfo != null
                            ? termInfo.key
                            : displayWord;

                    String wordForSpeech = termInfo != null
                            ? termInfo.key
                            : displayWord;

                    int screenY = calculatePopupY(tv, spanStart);

                    showDuoTooltip(
                            tv,
                            screenY,
                            ruTranslation,
                            englishForPopup,
                            transcription,
                            wordForSpeech
                    );
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(linkColor);
                    ds.setUnderlineText(true);
                    ds.setFakeBoldText(true);
                }
            };

            builder.setSpan(
                    clickableSpan,
                    startSpan,
                    endSpan,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            lastEnd = matcher.end();
        }

        builder.append(rawText.substring(lastEnd));

        tvTheoryText.setText(builder);
        tvTheoryText.setMovementMethod(LinkMovementMethod.getInstance());
        tvTheoryText.setHighlightColor(Color.TRANSPARENT);
    }

    private int calculatePopupY(TextView tv, int spanStart) {
        if (tv.getLayout() == null) {
            int[] location = new int[2];
            tv.getLocationOnScreen(location);
            return location[1];
        }

        int line = tv.getLayout().getLineForOffset(spanStart);

        int y = tv.getLayout().getLineBottom(line)
                + tv.getTotalPaddingTop()
                - tv.getScrollY();

        int[] location = new int[2];
        tv.getLocationOnScreen(location);

        return location[1] + y;
    }

    /**
     * Popup с переводом, транскрипцией и кнопкой озвучки.
     */
    private void showDuoTooltip(
            View anchor,
            int yCoordinate,
            String ruWord,
            String enWord,
            String transcription,
            String wordForSpeech
    ) {
        View popupView = LayoutInflater.from(this)
                .inflate(R.layout.layout_duo_tooltip, null);

        TextView tvRu = popupView.findViewById(R.id.tvDuoRu);
        TextView tvEn = popupView.findViewById(R.id.tvDuoEn);
        TextView tvTranscription = popupView.findViewById(R.id.tvDuoTranscription);
        ImageButton btnSpeak = popupView.findViewById(R.id.btnSpeak);

        tvRu.setText(ruWord);
        tvEn.setText(enWord);

        if (!isBlank(transcription)) {
            tvTranscription.setText(transcription);
            tvTranscription.setVisibility(View.VISIBLE);
        } else {
            tvTranscription.setVisibility(View.GONE);
        }

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        popupWindow.setElevation(20f);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        String speechText = !isBlank(wordForSpeech)
                ? wordForSpeech
                : enWord;

        btnSpeak.setOnClickListener(v -> speak(speechText));

        popupWindow.showAtLocation(
                anchor,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                0,
                yCoordinate + 60
        );

        if (ttsReady) {
            speak(speechText);
        }
    }

    private void speak(String text) {
        if (ttsReady && tts != null && !isBlank(text)) {
            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "TERM_TTS"
            );
        } else {
            Toast.makeText(this, "Озвучка недоступна", Toast.LENGTH_SHORT).show();
        }
    }

    private String normalizeTermKey(String key) {
        if (isBlank(key)) return "";

        String cleaned = normalizeSpaces(key);

        String direct = findCanonicalKey(cleaned);
        if (direct != null) {
            return direct;
        }

        String aliasTarget = termAliases.get(toLookupKey(cleaned));

        if (aliasTarget != null) {
            String canonical = findCanonicalKey(aliasTarget);
            return canonical != null ? canonical : aliasTarget;
        }

        return cleaned;
    }

    private TermInfo getTermData(String key) {
        if (isBlank(key)) return null;

        String canonical = normalizeTermKey(key);

        TermInfo direct = termsDictionary.get(canonical);
        if (direct != null) {
            return direct;
        }

        String foundKey = findCanonicalKey(canonical);

        if (foundKey != null) {
            return termsDictionary.get(foundKey);
        }

        return null;
    }

    private String findCanonicalKey(String key) {
        if (isBlank(key)) return null;

        String lookup = toLookupKey(key);

        for (String existingKey : termsDictionary.keySet()) {
            if (toLookupKey(existingKey).equals(lookup)) {
                return existingKey;
            }
        }

        return null;
    }

    private String toLookupKey(String value) {
        if (value == null) return "";

        return normalizeSpaces(value)
                .replace('‑', '-')
                .replace('–', '-')
                .replace('—', '-')
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeSpaces(String value) {
        if (value == null) return "";

        return value
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Резервный текст, если theory_text в БД пустой.
     */
    private String getHardcodedTheory(long themeId) {
        if (themeId == 1L) {
            return "Система контроля версий Git позволяет разработчикам работать вместе. " +
                    "Главное место хранения кода — Repository. " +
                    "Сохранение состояния называется Commit.\n\n" +
                    "Для параллельной работы используется Branch. " +
                    "Изменения объединяются через Merge.\n\n" +
                    "Перед началом работы делается Pull.";
        } else if (themeId == 2L) {
            return "Java — строго типизированный язык. " +
                    "Сокрытие данных называется Encapsulation.\n\n" +
                    "Перенимание свойств — Inheritance. " +
                    "Различное поведение метода — Polymorphism.\n\n" +
                    "Контракт задаёт Interface. " +
                    "Код в байт-код переводит Compiler. " +
                    "Выделение главного — Abstraction.";
        } else if (themeId == 3L) {
            return "Данные хранятся в Database. " +
                    "Для получения данных пишется Query.\n\n" +
                    "Уникальный идентификатор — Primary Key.\n\n" +
                    "Объединение данных из таблиц — Join.";
        }

        return "";
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
}