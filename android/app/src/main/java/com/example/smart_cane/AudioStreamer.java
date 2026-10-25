package com.example.smart_cane;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class AudioStreamer {

    private static final String TAG = "AudioStreamer";

    private static final int SAMPLE_RATE = 16000;
    private static final int PORT = 50005;

    private boolean isStreaming = false;

    private DatagramSocket socket;
    private InetAddress espAddress;

    private AudioRecord audioRecord;

    public void startStreaming(MediaProjection projection, String espIP) {

        new Thread(() -> {

            try {

                espAddress = InetAddress.getByName(espIP);
                socket = new DatagramSocket();

                AudioPlaybackCaptureConfiguration config =
                        new AudioPlaybackCaptureConfiguration.Builder(projection)
                                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                                .build();

                int bufferSize = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

                audioRecord = new AudioRecord.Builder()
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(SAMPLE_RATE)
                                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                        .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setAudioPlaybackCaptureConfig(config)
                        .build();

                byte[] buffer = new byte[bufferSize];

                audioRecord.startRecording();
                isStreaming = true;

                Log.d(TAG, "Audio streaming started");

                while (isStreaming) {

                    int read = audioRecord.read(buffer, 0, buffer.length);

                    if (read > 0) {

                        DatagramPacket packet = new DatagramPacket(
                                buffer,
                                read,
                                espAddress,
                                PORT
                        );

                        socket.send(packet);
                    }
                }

                audioRecord.stop();
                audioRecord.release();

            } catch (Exception e) {

                Log.e(TAG, "Streaming error", e);

            }

        }).start();
    }

    public void stopStreaming() {

        isStreaming = false;

        if (socket != null) socket.close();

        if (audioRecord != null) {

            audioRecord.stop();
            audioRecord.release();

        }
    }
}