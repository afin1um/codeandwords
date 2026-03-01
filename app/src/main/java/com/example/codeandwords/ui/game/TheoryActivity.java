package com.example.codeandwords.ui.game;

import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.Theme;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TheoryActivity extends AppCompatActivity {

    private TextView tvTitle, tvTheoryText;
    private View btnBack;
    private Repository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theory);

        tvTitle = findViewById(R.id.tvTheoryTitle);
        tvTheoryText = findViewById(R.id.tvTheoryText);
        btnBack = findViewById(R.id.btnBackTheory);
        repository = new Repository(this);

        btnBack.setOnClickListener(v -> finish());

        long themeId = getIntent().getLongExtra("THEME_ID", -1);
        if (themeId != -1) {
            loadTheory(themeId);
        } else {
            Toast.makeText(this, "Ошибка загрузки теории", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadTheory(long themeId) {
        repository.getThemeById(themeId, new Repository.DataCallback<Theme>() {
            @Override
            public void onSuccess(Theme theme) {
                tvTitle.setText("Теория: " + theme.getTitle());
                String dbText = theme.getTheoryText();

                if (dbText == null || dbText.trim().isEmpty()) {
                    dbText = getHardcodedTheory(themeId);
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

    private String getHardcodedTheory(long themeId) {
        if (themeId == 1L) {
            return "Система контроля версий Git позволяет разработчикам работать вместе, не мешая друг другу. " +
                    "Главное место хранения вашего кода называется [[Репозиторий|Repository]]. " +
                    "Когда вы написали кусок кода и хотите его сохранить в историю, вы делаете сохранение, которое называется [[Фиксация|Commit]].\n\n" +
                    "Чтобы несколько человек могли работать параллельно над разными задачами, создается отдельная [[Ветка|Branch]]. " +
                    "После того как задача завершена, изменения из вашей ветки объединяются с главным кодом через [[Слияние|Merge]].\n\n" +
                    "Перед началом работы утром всегда не забывайте делать [[Получение|Pull]], чтобы скачать самые свежие обновления от ваших коллег.";
        } else if (themeId == 2L) {
            return "Java — это строго типизированный язык программирования. " +
                    "Одной из главных концепций здесь является [[Инкапсуляция|Encapsulation]], которая скрывает важные данные внутри класса от случайного изменения.\n\n" +
                    "Классы могут перенимать свойства и методы друг друга — этот мощный механизм называется [[Наследование|Inheritance]]. " +
                    "А вот способность одного и того же метода вести себя по-разному в зависимости от того, кто его вызывает, называется [[Полиморфизм|Polymorphism]].\n\n" +
                    "Чтобы задать строгие правила (контракт) того, что должен уметь делать класс, используется [[Интерфейс|Interface]]. " +
                    "В самом конце написанный вами текстовый код переводится в понятный машине байт-код. Этим занимается специальная программа — [[Компилятор|Compiler]].";
        } else if (themeId == 3L) {
            return "Для надежного хранения огромных массивов информации используется [[База данных|Database]]. " +
                    "Чтобы получить список пользователей или добавить новый товар, разработчик пишет специальный текстовый [[Запрос|Query]].\n\n" +
                    "Чтобы не перепутать двух пользователей с одинаковыми именами, каждая запись в таблице должна иметь свой уникальный идентификатор (ID), который называется [[Первичный ключ|Primary Key]].\n\n" +
                    "Часто данные разбиты на разные таблицы (например, \"Пользователи\" и \"Их заказы\"). Если нам нужно получить эти данные вместе за один раз, мы используем операцию объединения — [[Соединение|Join]].";
        }
        return "";
    }

    private void processInteractiveText(String rawText) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        Pattern pattern = Pattern.compile("\\[\\[(.*?)\\|(.*?)\\]\\]");
        Matcher matcher = pattern.matcher(rawText);

        int lastEnd = 0;
        while (matcher.find()) {
            builder.append(rawText.substring(lastEnd, matcher.start()));

            String ruWord = matcher.group(1);
            String enWord = matcher.group(2);

            int startSpan = builder.length();
            builder.append(ruWord);
            int endSpan = builder.length();

            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    TextView tv = (TextView) widget;

                    // Вычисляем, на какой строчке находится слово, чтобы показать попап прямо под ним
                    int line = tv.getLayout().getLineForOffset(startSpan);
                    int y = tv.getLayout().getLineBottom(line);

                    int[] location = new int[2];
                    tv.getLocationOnScreen(location);
                    int screenY = location[1] + y;

                    // Вызываем наш красивый попап!
                    showDuoTooltip(tv, screenY, ruWord, enWord);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(Color.parseColor("#2196F3")); // Синий цвет ссылки
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

    // --- НОВЫЙ МЕТОД: Создание и показ красивой карточки ---
    private void showDuoTooltip(View anchor, int yCoordinate, String ruWord, String enWord) {
        // Подгружаем наш красивый XML-макет
        View popupView = LayoutInflater.from(this).inflate(R.layout.layout_duo_tooltip, null);

        TextView tvRu = popupView.findViewById(R.id.tvDuoRu);
        TextView tvEn = popupView.findViewById(R.id.tvDuoEn);

        tvRu.setText(ruWord);
        tvEn.setText(enWord);

        // Создаем всплывающее окно
        PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true); // true означает, что окно закроется, если кликнуть мимо него

        // Добавляем красивую стандартную анимацию появления
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        popupWindow.setElevation(20f);

        // Самая последняя строчка в TheoryActivity.java
        popupWindow.showAtLocation(anchor, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, yCoordinate + 60);
    }
}