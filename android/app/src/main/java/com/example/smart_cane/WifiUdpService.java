package com.example.smart_cane;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Locale;

public class WifiUdpService extends Service {

    private static final int PORT = 4210;
    private static final String CHANNEL_ID = "SmartCaneChannel";
    public static final String ACTION_TRACK_DETECTED = "com.example.smart_cane.TRACK_DETECTED";

    private DatagramSocket socket;
    private boolean running = true;

    private TextToSpeech textToSpeech;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Smart Cane Active")
                .setContentText("Listening for Vision Node via WiFi")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(1.1f);
            }
        });

        startUdpListener();
    }

    private void startUdpListener() {
        new Thread(() -> {
            try {
                socket = new DatagramSocket(PORT);
                byte[] buffer = new byte[1024];

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    processSensorData(message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void processSensorData(String data) {
        data = data.trim().toLowerCase(); // Normalize input

        if (data.contains("obstacle")) {
            speak("Warning. Obstacle detected.");
        } else if (data.contains("water")) {
            speak("Warning. Water detected.");
        } else if (data.contains("stairs")) {
            speak("Caution. Stairs detected.");
        } else if (data.contains("drop")) {
            speak("Danger. Drop detected.");
        } else if (data.contains("track")) {
            // --- UPDATED: Speak aloud to confirm packet receipt ---
            speak("Track sign detected.");

            // Forward the track detection to the Activity for navigation logic
            Intent intent = new Intent(ACTION_TRACK_DETECTED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, null);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Smart Cane Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (socket != null) socket.close();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}