package com.example.shakegemini;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import java.util.Arrays;
import java.util.List;

public class ShakeService extends Service implements SensorEventListener {

    private static final String TAG = "ShakeService";
    private static final int COOLDOWN_MS = 1500;
    private static volatile boolean isServiceRunning = false;

    public static final String KEY_SELECTED_AI = "selected_ai_app";
    public static final String DEFAULT_AI = "gemini";

    public static class AiAppInfo {
        public final String id;
        public final String name;
        public final String tagline;
        public final String packageName;
        public final String webUrl;
        public final int accentColor;

        public AiAppInfo(String id, String name, String tagline, String packageName, String webUrl, int accentColor) {
            this.id = id;
            this.name = name;
            this.tagline = tagline;
            this.packageName = packageName;
            this.webUrl = webUrl;
            this.accentColor = accentColor;
        }
    }

    public static final List<AiAppInfo> SUPPORTED_APPS = Arrays.asList(
            new AiAppInfo("gemini", "Google Gemini", "Fast coding, planning & Google integration",
                    "com.google.android.apps.bard", "https://gemini.google.com", Color.parseColor("#38BDF8")),
            new AiAppInfo("chatgpt", "ChatGPT", "OpenAI GPT-4o, coding & reasoning",
                    "com.openai.chatgpt", "https://chatgpt.com", Color.parseColor("#10B981")),
            new AiAppInfo("claude", "Claude", "Anthropic Claude 3.5 Sonnet & nuanced writing",
                    "com.anthropic.claude", "https://claude.ai", Color.parseColor("#F97316")),
            new AiAppInfo("deepseek", "DeepSeek", "DeepSeek V3 / R1 reasoning & coding",
                    "com.deepseek.chat", "https://chat.deepseek.com", Color.parseColor("#06B6D4")),
            new AiAppInfo("perplexity", "Perplexity AI", "Real-time web search & academic citations",
                    "ai.perplexity.app.android", "https://www.perplexity.ai", Color.parseColor("#14B8A6"))
    );

    public static AiAppInfo getAiApp(String id) {
        for (AiAppInfo app : SUPPORTED_APPS) {
            if (app.id.equalsIgnoreCase(id)) {
                return app;
            }
        }
        return SUPPORTED_APPS.get(0);
    }

    public static boolean isRunning() {
        return isServiceRunning;
    }

    private SensorManager sensorManager;
    private SharedPreferences prefs;
    private long lastShakeTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        isServiceRunning = true;
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        startForegroundNotification();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void startForegroundNotification() {
        String channelId = "shake_ai_channel";
        String aiId = prefs.getString(KEY_SELECTED_AI, DEFAULT_AI);
        AiAppInfo app = getAiApp(aiId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Shake to AI Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);

            Intent openAppIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, openAppIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("Shake to " + app.name + " Active")
                    .setContentText("Shake phone to trigger " + app.name + " (replies in text)")
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentIntent(pendingIntent)
                    .build();

            startForeground(1, notification);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;

        float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);
        float threshold = prefs.getFloat(MainActivity.KEY_THRESHOLD, 2.7f);

        if (gForce > threshold) {
            long now = System.currentTimeMillis();
            if (lastShakeTime + COOLDOWN_MS > now) return;
            lastShakeTime = now;

            vibrateFeedback();
            int mode = prefs.getInt(MainActivity.KEY_MODE, MainActivity.MODE_VOICE_TO_TEXT);
            String aiId = prefs.getString(KEY_SELECTED_AI, DEFAULT_AI);
            launchSelectedAi(this, mode, aiId);
        }
    }

    private void vibrateFeedback() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    public static void launchSelectedAi(Context context, int mode, String aiId) {
        AiAppInfo app = getAiApp(aiId);
        Log.d(TAG, "Launching AI: " + app.name + " with mode " + mode);

        if (mode == MainActivity.MODE_VOICE_TO_TEXT) {
            GeminiVoiceAccessibilityService.requestAutoVoiceClick();
        }

        if (mode == MainActivity.MODE_ASSISTANT_OVERLAY) {
            launchAssistantOverlay(context);
            return;
        }

        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                context.startActivity(launchIntent);
                return;
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch package directly: " + app.packageName, e);
            }
        }

        // Fallback: If native app is not installed, open official web interface
        try {
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(app.webUrl));
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(webIntent);
        } catch (Exception e) {
            try {
                Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + app.packageName));
                marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(marketIntent);
            } catch (Exception ignored) {}
        }
    }

    private static void launchAssistantOverlay(Context context) {
        Intent assistIntent = new Intent("android.intent.action.VOICE_ASSIST");
        assistIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(assistIntent);
        } catch (Exception e) {
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com"));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
