package com.example.shakegemini;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "ShakePrefs";
    public static final String KEY_THRESHOLD = "threshold";
    public static final String KEY_MODE = "trigger_mode";

    public static final int MODE_GEMINI_APP_VOICE = 0;
    public static final int MODE_GEMINI_VOICE_ASSIST = 1;
    public static final int MODE_GEMINI_APP_DIRECT = 2;

    private static final float DEFAULT_THRESHOLD = 2.7f;

    private SharedPreferences prefs;
    private TextView statusGeminiApp;
    private TextView statusAccessibility;
    private TextView statusOverlay;
    private LinearLayout bannerA11yWarning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float currentThreshold = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD);
        int currentMode = prefs.getInt(KEY_MODE, MODE_GEMINI_APP_VOICE);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 56, 48, 64);
        scrollView.addView(root);

        // Header
        TextView title = new TextView(this);
        title.setText("Shake to Gemini Voice");
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 10);

        TextView subtitle = new TextView(this);
        subtitle.setText("Shake phone to launch Gemini and automatically trigger Voice / Microphone mode.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, 0, 0, 30);

        root.addView(title);
        root.addView(subtitle);

        // Accessibility Missing Banner (visible when A11y is disabled)
        bannerA11yWarning = new LinearLayout(this);
        bannerA11yWarning.setOrientation(LinearLayout.VERTICAL);
        bannerA11yWarning.setPadding(32, 24, 32, 24);
        bannerA11yWarning.setBackgroundColor(Color.parseColor("#FEF3C7")); // warm amber

        TextView bannerText = new TextView(this);
        bannerText.setText("⚠️ Microphone Auto-Trigger is OFF\nTap here to turn on 'ShakeGemini Voice Helper' in Accessibility Settings so Gemini starts directly in microphone mode.");
        bannerText.setTextColor(Color.parseColor("#92400E"));
        bannerText.setTextSize(13);
        bannerText.setTypeface(null, Typeface.BOLD);
        bannerA11yWarning.addView(bannerText);
        bannerA11yWarning.setOnClickListener(v -> openAccessibilitySettings());

        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerParams.setMargins(0, 0, 0, 30);
        bannerA11yWarning.setLayoutParams(bannerParams);
        root.addView(bannerA11yWarning);

        // System Status Card
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(32, 28, 32, 28);
        statusCard.setBackgroundColor(Color.parseColor("#F1F5F9"));

        TextView statusTitle = new TextView(this);
        statusTitle.setText("Setup & Permissions Status:");
        statusTitle.setTypeface(null, Typeface.BOLD);
        statusTitle.setPadding(0, 0, 0, 16);
        statusCard.addView(statusTitle);

        statusOverlay = new TextView(this);
        statusCard.addView(statusOverlay);

        statusGeminiApp = new TextView(this);
        statusCard.addView(statusGeminiApp);

        statusAccessibility = new TextView(this);
        statusCard.addView(statusAccessibility);

        // Quick settings buttons
        LinearLayout setupButtons = new LinearLayout(this);
        setupButtons.setOrientation(LinearLayout.HORIZONTAL);
        setupButtons.setPadding(0, 16, 0, 0);

        Button btnA11y = new Button(this);
        btnA11y.setText("1. Open Accessibility");
        btnA11y.setTextSize(12);
        btnA11y.setOnClickListener(v -> openAccessibilitySettings());

        Button btnAssistant = new Button(this);
        btnAssistant.setText("2. Digital Assistant");
        btnAssistant.setTextSize(12);
        btnAssistant.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                } catch (Exception ignored) {}
            }
        });

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnParams.setMargins(0, 0, 12, 0);
        setupButtons.addView(btnA11y, btnParams);

        LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        setupButtons.addView(btnAssistant, btnParams2);

        statusCard.addView(setupButtons);
        root.addView(statusCard);

        addSpacer(root, 36);

        // Trigger Mode Section
        TextView modeTitle = new TextView(this);
        modeTitle.setText("Shake Trigger Mode:");
        modeTitle.setTypeface(null, Typeface.BOLD);
        modeTitle.setTextSize(16);
        modeTitle.setPadding(0, 0, 0, 12);
        root.addView(modeTitle);

        RadioGroup radioGroup = new RadioGroup(this);

        RadioButton rbAppVoice = new RadioButton(this);
        rbAppVoice.setId(View.generateViewId());
        rbAppVoice.setText("1. Gemini App + Voice Mode (Recommended)\nOpens Gemini and automatically triggers microphone for dictation.");
        rbAppVoice.setPadding(0, 0, 0, 16);

        RadioButton rbOverlay = new RadioButton(this);
        rbOverlay.setId(View.generateViewId());
        rbOverlay.setText("2. Gemini Voice Assistant Overlay\nInvokes floating assistant overlay (requires Gemini set as default assistant).");
        rbOverlay.setPadding(0, 0, 0, 16);

        RadioButton rbAppOnly = new RadioButton(this);
        rbAppOnly.setId(View.generateViewId());
        rbAppOnly.setText("3. Gemini App (Standard Open)\nOpens Gemini without auto-triggering microphone.");

        radioGroup.addView(rbAppVoice);
        radioGroup.addView(rbOverlay);
        radioGroup.addView(rbAppOnly);

        if (currentMode == MODE_GEMINI_VOICE_ASSIST) {
            rbOverlay.setChecked(true);
        } else if (currentMode == MODE_GEMINI_APP_DIRECT) {
            rbAppOnly.setChecked(true);
        } else {
            rbAppVoice.setChecked(true);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int newMode = MODE_GEMINI_APP_VOICE;
            if (checkedId == rbOverlay.getId()) {
                newMode = MODE_GEMINI_VOICE_ASSIST;
            } else if (checkedId == rbAppOnly.getId()) {
                newMode = MODE_GEMINI_APP_DIRECT;
            }
            prefs.edit().putInt(KEY_MODE, newMode).apply();
            Toast.makeText(this, "Trigger mode updated!", Toast.LENGTH_SHORT).show();
        });

        root.addView(radioGroup);

        addSpacer(root, 36);

        // Sensitivity Section
        TextView thresholdTitle = new TextView(this);
        thresholdTitle.setText("Shake Sensitivity:");
        thresholdTitle.setTypeface(null, Typeface.BOLD);
        thresholdTitle.setTextSize(16);
        thresholdTitle.setPadding(0, 0, 0, 8);
        root.addView(thresholdTitle);

        TextView thresholdLabel = new TextView(this);
        thresholdLabel.setText(formatLabel(currentThreshold));
        thresholdLabel.setPadding(0, 0, 0, 16);
        root.addView(thresholdLabel);

        SeekBar seekBar = new SeekBar(this);
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
                Toast.makeText(MainActivity.this, "Sensitivity saved!", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(seekBar);

        addSpacer(root, 36);

        // Action Buttons
        Button startBtn = new Button(this);
        startBtn.setText("1. Start Shake Listener Service");
        startBtn.setOnClickListener(v -> checkPermissionsAndStartService());
        root.addView(startBtn);

        Button testBtn = new Button(this);
        testBtn.setText("2. Test Gemini Voice Trigger Now");
        testBtn.setOnClickListener(v -> {
            if (!GeminiVoiceAccessibilityService.isAccessibilityEnabled(this)
                    && prefs.getInt(KEY_MODE, MODE_GEMINI_APP_VOICE) == MODE_GEMINI_APP_VOICE) {
                showAccessibilityPromptDialog();
            } else {
                Toast.makeText(this, "Testing Gemini trigger...", Toast.LENGTH_SHORT).show();
                ShakeService.launchGemini(this, prefs.getInt(KEY_MODE, MODE_GEMINI_APP_VOICE));
            }
        });
        root.addView(testBtn);

        Button stopBtn = new Button(this);
        stopBtn.setText("3. Stop Shake Service");
        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, ShakeService.class));
            Toast.makeText(this, "Shake Service Stopped", Toast.LENGTH_SHORT).show();
        });
        root.addView(stopBtn);

        addSpacer(root, 28);

        TextView infinixInfo = new TextView(this);
        infinixInfo.setText("Infinix Hot 12 Pro (Android 12) Tips:\n" +
                "• Lock ShakeGemini in Recents screen to prevent background kill.\n" +
                "• In Settings > Battery Lab > ShakeGemini, choose 'Don't Optimize'.\n" +
                "• In Accessibility > Installed Services, turn ON 'ShakeGemini Voice Helper'.");
        infinixInfo.setTextSize(12);
        infinixInfo.setTextColor(Color.GRAY);
        root.addView(infinixInfo);

        setContentView(scrollView);
        updateStatus();
    }

    private void checkPermissionsAndStartService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant 'Display over other apps' to allow launching from background!", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        if (!GeminiVoiceAccessibilityService.isAccessibilityEnabled(this)
                && prefs.getInt(KEY_MODE, MODE_GEMINI_APP_VOICE) == MODE_GEMINI_APP_VOICE) {
            showAccessibilityPromptDialog();
            return;
        }

        startShakeService();
    }

    private void showAccessibilityPromptDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Voice Auto-Trigger")
                .setMessage("To automatically tap the Microphone button inside Gemini when you shake, enable 'ShakeGemini Voice Helper' in Accessibility Settings.\n\nTap 'Enable Now' -> Look for 'Installed Services' or 'Downloaded Apps' -> Turn 'ShakeGemini Voice Helper' ON.")
                .setPositiveButton("Enable Now", (d, w) -> openAccessibilitySettings())
                .setNegativeButton("Skip for Now", (d, w) -> startShakeService())
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Find 'ShakeGemini Voice Helper' in Installed Services and turn it ON", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open Accessibility Settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void startShakeService() {
        Intent serviceIntent = new Intent(this, ShakeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Shake Listener Active! Shake your phone to test.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean hasOverlay = Settings.canDrawOverlays(this);
        statusOverlay.setText("• Display Over Other Apps: " + (hasOverlay ? "Granted ✓" : "Missing ✕ (Required)"));
        statusOverlay.setTextColor(hasOverlay ? Color.parseColor("#166534") : Color.parseColor("#991B1B"));

        boolean hasGemini = isPackageInstalled(ShakeService.PACKAGE_GEMINI);
        statusGeminiApp.setText("• Gemini App: " + (hasGemini ? "Installed ✓" : "Not Found (Click to Install)"));
        statusGeminiApp.setTextColor(hasGemini ? Color.parseColor("#166534") : Color.parseColor("#C2410C"));

        boolean a11yActive = GeminiVoiceAccessibilityService.isAccessibilityEnabled(this);
        statusAccessibility.setText("• Voice Auto-Trigger Helper: " + (a11yActive ? "Active ✓" : "Disabled ✕ (Required for Mic Auto-Click)"));
        statusAccessibility.setTextColor(a11yActive ? Color.parseColor("#166534") : Color.parseColor("#DC2626"));

        bannerA11yWarning.setVisibility(a11yActive ? View.GONE : View.VISIBLE);
    }

    private boolean isPackageInstalled(String pkgName) {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(pkgName, 0);
            return true;
        } catch (Exception e) {
            return pm.getLaunchIntentForPackage(pkgName) != null;
        }
    }

    private void addSpacer(LinearLayout layout, int heightDp) {
        View spacer = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightDp);
        spacer.setLayoutParams(params);
        layout.addView(spacer);
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
        return String.format(Locale.US, "Threshold: %.1f G  (%s)", val, feel);
    }
}
