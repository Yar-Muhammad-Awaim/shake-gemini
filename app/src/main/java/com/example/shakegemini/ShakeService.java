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

public class ShakeService extends Service implements SensorEventListener {

    private static final String TAG = "ShakeService";
    public static final String PACKAGE_GEMINI = "com.google.android.apps.bard";
    private static final int COOLDOWN_MS = 1500;

    private SensorManager sensorManager;
    private SharedPreferences prefs;
    private long lastShakeTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        startForegroundNotification();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void startForegroundNotification() {
        String channelId = "shake_gemini_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Shake to Gemini Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);

            Intent openAppIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, openAppIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("Shake to Gemini Active")
                    .setContentText("Shake phone to trigger Gemini Voice message mode")
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
            int mode = prefs.getInt(MainActivity.KEY_MODE, MainActivity.MODE_GEMINI_APP_VOICE);
            launchGemini(this, mode);
        }
    }

    private void vibrateFeedback() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    public static void launchGemini(Context context, int mode) {
        Log.d(TAG, "Launching Gemini with mode: " + mode);

        if (mode == MainActivity.MODE_GEMINI_APP_VOICE) {
            GeminiVoiceAccessibilityService.requestAutoVoiceClick();
        }

        if (mode == MainActivity.MODE_GEMINI_VOICE_ASSIST) {
            launchVoiceAssistOverlay(context);
            return;
        }

        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(PACKAGE_GEMINI);

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                context.startActivity(launchIntent);
                return;
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch Gemini package directly", e);
            }
        }

        // Fallback: If standalone Gemini package is not installed or failed, try Voice Assist Intent
        launchVoiceAssistOverlay(context);
    }

    private static void launchVoiceAssistOverlay(Context context) {
        Intent assistIntent = new Intent(Intent.ACTION_VOICE_ASSIST);
        assistIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(assistIntent);
        } catch (Exception e) {
            Log.w(TAG, "ACTION_VOICE_ASSIST unavailable, falling back to Gemini web", e);
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
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
