package com.example.codeandwords.data.speech;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class TtsManager {

    private final Context appContext;
    private TextToSpeech tts;
    private boolean isTtsReady = false;

    public TtsManager(Context context) {
        this.appContext = context.getApplicationContext();
        initTtsIfNeeded();
    }

    private void initTtsIfNeeded() {
        if (tts != null) return;

        tts = new TextToSpeech(appContext, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;

                // ✅ ЛОГ ДЛЯ ОТЛАДКИ
                Log.d("TtsManager", "TTS инициализирован: " + isTtsReady
                        + ", lang_result=" + result);
            } else {
                isTtsReady = false;
                Log.e("TtsManager", "Ошибка инициализации TextToSpeech: " + status);
            }
        });
    }

    public void speak(String text, boolean isSlow) {
        if (tts == null || !isTtsReady || text == null || text.trim().isEmpty()) {
            Log.w("TtsManager", "speak() пропущен: tts=" + (tts != null)
                    + ", ready=" + isTtsReady + ", text=" + text);
            return;
        }

        tts.setSpeechRate(isSlow ? 0.5f : 1.0f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WORD_TTS");
    }

    // ✅ ДОБАВЛЕН МЕТОД
    public boolean isReady() {
        return isTtsReady;
    }

    public void destroy() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.e("TtsManager", "Ошибка освобождения TTS: " + e.getMessage(), e);
            } finally {
                tts = null;
                isTtsReady = false;
            }
        }
    }
}