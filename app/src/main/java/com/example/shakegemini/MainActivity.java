package com.example.shakegemini;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "ShakePrefs";
    public static final String KEY_THRESHOLD = "threshold";
    private static final float DEFAULT_THRESHOLD = 2.7f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float currentThreshold = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 80, 60, 60);

        TextView info = new TextView(this);
        info.setText("Infinix Hot 12 Pro Setup:\n" +
                "1. Tap Enable below and allow 'Display over other apps'.\n" +
                "2. Lock this app in Recents & set Battery to Unrestricted.\n" +
                "3. Use the slider below to tune your shake sensitivity in real time.");
        info.setPadding(0, 0, 0, 50);

        TextView thresholdLabel = new TextView(this);
        thresholdLabel.setText(formatLabel(currentThreshold));
        thresholdLabel.setPadding(0, 0, 0, 20);

        SeekBar seekBar = new SeekBar(this);
        // Range: 1.5G (progress 0) to 4.5G (progress 30) in steps of 0.1G
        seekBar.setMax(30);
        int initialProgress = Math.round((currentThreshold - 1.5f) * 10);
        seekBar.setProgress(Math.max(0, Math.min(30, initialProgress)));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float newThreshold = 1.5f + (progress / 10.0f);
                thresholdLabel.setText(formatLabel(newThreshold));
                prefs.edit().putFloat(KEY_THRESHOLD, newThreshold).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                Toast.makeText(MainActivity.this, "Sensitivity updated!", Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sliderParams.setMargins(0, 0, 0, 60);
        seekBar.setLayoutParams(sliderParams);

        Button startBtn = new Button(this);
        startBtn.setText("1. Enable Shake to Gemini");
        startBtn.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant 'Display over other apps' first!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Intent serviceIntent = new Intent(this, ShakeService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Toast.makeText(this, "Shake Service Active! Shake your phone to test.", Toast.LENGTH_SHORT).show();
            }
        });

        Button stopBtn = new Button(this);
        stopBtn.setText("2. Stop Shake Service");
        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, ShakeService.class));
            Toast.makeText(this, "Shake Service Stopped", Toast.LENGTH_SHORT).show();
        });

        layout.addView(info);
        layout.addView(thresholdLabel);
        layout.addView(seekBar);
        layout.addView(startBtn);
        layout.addView(stopBtn);
        setContentView(layout);
    }

    private String formatLabel(float val) {
        String feel;
        if (val < 2.2f) {
            feel = "Very Sensitive (Light flick)";
        } else if (val <= 3.0f) {
            feel = "Balanced (Recommended)";
        } else {
            feel = "Firm Shake (Prevents accidental triggers)";
        }
        return String.format(Locale.US, "Shake Force Threshold: %.1f G\nMode: %s", val, feel);
    }
}
