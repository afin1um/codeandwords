package com.example.codeandwords.ui.game;

import android.content.Intent;
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
import com.example.codeandwords.ui.base.BaseBackActivity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TheoryActivity extends BaseBackActivity {

    private TextView tvTitle, tvTheoryText;
    private View btnBack;
    private Repository repository;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private Long themeId;
    private String themeTitle;

    // Словарь терминов: английское слово -> [русский перевод, транскрипция]
    private final Map<String, String[]> termsDictionary = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theory);

        initTermsDictionary();

        tvTitle = findViewById(R.id.tvTheoryTitle);
        tvTheoryText = findViewById(R.id.tvTheoryText);
        btnBack = findViewById(R.id.btnBackTheory);
        repository = Repository.getInstance(getApplicationContext());

        themeId = getIntent().getLongExtra("THEME_ID", -1);
        themeTitle = getIntent().getStringExtra("THEME_TITLE");

        // ✅ Кнопка назад → возврат в GameSelectionActivity текущей темы
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> goBackToGameSelection());
        }

        // Инициализация TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = true;
                }
            }
        });

        if (themeId != null && themeId != -1) {
            loadTheory(themeId);
        } else {
            Toast.makeText(this, "Ошибка загрузки теории", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        goBackToGameSelection();
    }

    /**
     * ✅ Возврат в GameSelectionActivity текущей темы.
     */
    private void goBackToGameSelection() {
        Intent intent = new Intent(this, GameSelectionActivity.class);
        intent.putExtra("THEME_ID", themeId != null ? themeId : -1L);
        intent.putExtra("THEME_TITLE", themeTitle);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void initTermsDictionary() {
        // Git
        termsDictionary.put("Repository", new String[]{"Репозиторий", "[rɪˈpɒzɪtri]"});
        termsDictionary.put("Commit", new String[]{"Фиксация (коммит)", "[kəˈmɪt]"});
        termsDictionary.put("Branch", new String[]{"Ветка", "[brɑːntʃ]"});
        termsDictionary.put("Merge", new String[]{"Слияние", "[mɜːdʒ]"});
        termsDictionary.put("Pull", new String[]{"Получение", "[pʊl]"});
        termsDictionary.put("Push", new String[]{"Отправка", "[pʊʃ]"});
        termsDictionary.put("Clone", new String[]{"Клонирование", "[kloʊn]"});
        termsDictionary.put("Fork", new String[]{"Форк (ответвление)", "[fɔːk]"});

        // Java / OOP
        termsDictionary.put("Encapsulation", new String[]{"Инкапсуляция", "[ɪnˌkæpsjʊˈleɪʃn]"});
        termsDictionary.put("Inheritance", new String[]{"Наследование", "[ɪnˈherɪtəns]"});
        termsDictionary.put("Polymorphism", new String[]{"Полиморфизм", "[ˌpɒlɪˈmɔːfɪzəm]"});
        termsDictionary.put("Interface", new String[]{"Интерфейс", "[ˈɪntəfeɪs]"});
        termsDictionary.put("Compiler", new String[]{"Компилятор", "[kəmˈpaɪlə]"});
        termsDictionary.put("Class", new String[]{"Класс", "[klɑːs]"});
        termsDictionary.put("Object", new String[]{"Объект", "[ˈɒbdʒɪkt]"});
        termsDictionary.put("Method", new String[]{"Метод", "[ˈmeθəd]"});

        // Database
        termsDictionary.put("Database", new String[]{"База данных", "[ˈdeɪtəbeɪs]"});
        termsDictionary.put("Query", new String[]{"Запрос", "[ˈkwɪəri]"});
        termsDictionary.put("Primary Key", new String[]{"Первичный ключ", "[ˈpraɪməri kiː]"});
        termsDictionary.put("Join", new String[]{"Соединение", "[dʒɔɪn]"});
        termsDictionary.put("Table", new String[]{"Таблица", "[ˈteɪbl]"});
        termsDictionary.put("Index", new String[]{"Индекс", "[ˈɪndeks]"});
    }

    private void loadTheory(long themeId) {
        repository.getThemeById(themeId, new Repository.DataCallback<Theme>() {
            @Override
            public void onSuccess(Theme theme) {
                tvTitle.setText("Теория: " + theme.getTitle());
                String dbText = theme.getTheoryText();

                if (dbText == null || dbText.trim().isEmpty()) {
                    dbText = getHardcodedTheory(themeId);
                } else {
                    dbText = autoMarkTerms(dbText);
                }

                if (dbText != null && !dbText.isEmpty()) {
                    processInteractiveText(dbText);
                } else {
                    tvTheoryText.setText("Для этой темы пока нет теории.");
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(TheoryActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String autoMarkTerms(String text) {
        if (text.contains("[[")) return text;

        for (Map.Entry<String, String[]> entry : termsDictionary.entrySet()) {
            String en = entry.getKey();
            String ru = entry.getValue()[0];

            Pattern p = Pattern.compile("\\b" + Pattern.quote(en) + "\\b");
            Matcher m = p.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement("[[" + en + "|" + en + "]]"));
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }

    private String getHardcodedTheory(long themeId) {
        if (themeId == 1L) {
            return "Система контроля версий Git позволяет разработчикам работать вместе, не мешая друг другу. " +
                    "Главное место хранения вашего кода называется [[Repository|Repository]]. " +
                    "Когда вы написали кусок кода и хотите его сохранить в историю, вы делаете сохранение, которое называется [[Commit|Commit]].\n\n" +
                    "Чтобы несколько человек могли работать параллельно над разными задачами, создается отдельная [[Branch|Branch]]. " +
                    "После того как задача завершена, изменения из вашей ветки объединяются с главным кодом через [[Merge|Merge]].\n\n" +
                    "Перед началом работы утром всегда не забывайте делать [[Pull|Pull]], чтобы скачать самые свежие обновления от ваших коллег.";
        } else if (themeId == 2L) {
            return "Java — это строго типизированный язык программирования. " +
                    "Одной из главных концепций здесь является [[Encapsulation|Encapsulation]], которая скрывает важные данные внутри класса от случайного изменения.\n\n" +
                    "Классы могут перенимать свойства и методы друг друга — этот мощный механизм называется [[Inheritance|Inheritance]]. " +
                    "А вот способность одного и того же метода вести себя по-разному в зависимости от того, кто его вызывает, называется [[Polymorphism|Polymorphism]].\n\n" +
                    "Чтобы задать строгие правила (контракт) того, что должен уметь делать класс, используется [[Interface|Interface]]. " +
                    "В самом конце написанный вами текстовый код переводится в понятный машине байт-код. Этим занимается специальная программа — [[Compiler|Compiler]].";
        } else if (themeId == 3L) {
            return "Для надежного хранения огромных массивов информации используется [[Database|Database]]. " +
                    "Чтобы получить список пользователей или добавить новый товар, разработчик пишет специальный текстовый [[Query|Query]].\n\n" +
                    "Чтобы не перепутать двух пользователей с одинаковыми именами, каждая запись в таблице должна иметь свой уникальный идентификатор (ID), который называется [[Primary Key|Primary Key]].\n\n" +
                    "Часто данные разбиты на разные таблицы (например, \"Пользователи\" и \"Их заказы\"). Если нам нужно получить эти данные вместе за один раз, мы используем операцию объединения — [[Join|Join]].";
        }
        return "";
    }

    private void processInteractiveText(String rawText) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        Pattern pattern = Pattern.compile("\\[\\[(.*?)\\|(.*?)\\]\\]");
        Matcher matcher = pattern.matcher(rawText);

        final int linkColor = ContextCompat.getColor(this, R.color.app_blue);

        int lastEnd = 0;
        while (matcher.find()) {
            builder.append(rawText.substring(lastEnd, matcher.start()));

            final String displayWord = matcher.group(1);
            final String enWord = matcher.group(2);

            int startSpan = builder.length();
            builder.append(displayWord);
            int endSpan = builder.length();

            final int spanStart = startSpan;

            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    TextView tv = (TextView) widget;

                    int line = tv.getLayout().getLineForOffset(spanStart);
                    int y = tv.getLayout().getLineBottom(line);

                    int[] location = new int[2];
                    tv.getLocationOnScreen(location);
                    int screenY = location[1] + y;

                    String[] data = termsDictionary.get(enWord);
                    String ruTranslation = data != null ? data[0] : displayWord;
                    String transcription = data != null ? data[1] : "";

                    showDuoTooltip(tv, screenY, ruTranslation, enWord, transcription);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(linkColor);
                    ds.setUnderlineText(true);
                    ds.setFakeBoldText(true);
                }
            };

            builder.setSpan(clickableSpan, startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            lastEnd = matcher.end();
        }
        builder.append(rawText.substring(lastEnd));

        tvTheoryText.setText(builder);
        tvTheoryText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void showDuoTooltip(View anchor, int yCoordinate, String ruWord, String enWord, String transcription) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.layout_duo_tooltip, null);

        TextView tvRu = popupView.findViewById(R.id.tvDuoRu);
        TextView tvEn = popupView.findViewById(R.id.tvDuoEn);
        TextView tvTranscription = popupView.findViewById(R.id.tvDuoTranscription);
        ImageButton btnSpeak = popupView.findViewById(R.id.btnSpeak);

        tvRu.setText(ruWord);
        tvEn.setText(enWord);

        if (transcription != null && !transcription.isEmpty()) {
            tvTranscription.setText(transcription);
            tvTranscription.setVisibility(View.VISIBLE);
        } else {
            tvTranscription.setVisibility(View.GONE);
        }

        PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);

        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        popupWindow.setElevation(20f);
        popupWindow.setOutsideTouchable(true);

        btnSpeak.setOnClickListener(v -> speak(enWord));

        popupWindow.showAtLocation(anchor, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, yCoordinate + 60);

        if (ttsReady) {
            speak(enWord);
        }
    }

    private void speak(String text) {
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TERM_TTS");
        } else {
            Toast.makeText(this, "Озвучка недоступна", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
        // ❌ Не вызываем repository.onDestroy() — Repository это синглтон!
    }
}