package com.example.smart_cane;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class TTSManager {

    private TextToSpeech textToSpeech;

    public TTSManager(Context context) {

        textToSpeech = new TextToSpeech(context, status -> {

            if (status == TextToSpeech.SUCCESS) {

                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(1.0f);

            }

        });

    }

    public void speak(String text) {

        if (textToSpeech != null) {

            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);

        }

    }

    public void shutdown() {

        if (textToSpeech != null) {

            textToSpeech.stop();
            textToSpeech.shutdown();

        }

    }
}