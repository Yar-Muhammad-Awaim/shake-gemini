package com.example.shakegemini;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "ShakePrefs";
    public static final String KEY_THRESHOLD = "threshold";
    public static final String KEY_MODE = "trigger_mode";

    public static final int MODE_VOICE_TO_TEXT = 0; // Speech-to-text dictation (replies in text)
    public static final int MODE_APP_DIRECT = 1;     // Launch app without auto-mic
    public static final int MODE_ASSISTANT_OVERLAY = 2; // Voice Assist intent

    private static final float DEFAULT_THRESHOLD = 2.7f;

    private SharedPreferences prefs;
    private String currentAiId;
    private int currentMode;
    private float currentThreshold;

    private LinearLayout appsContainer;
    private List<View> appCardViews = new ArrayList<>();

    private TextView statusOverlay;
    private TextView statusAccessibility;
    private LinearLayout bannerA11yWarning;
    private Button startStopBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentAiId = prefs.getString(ShakeService.KEY_SELECTED_AI, ShakeService.DEFAULT_AI);
        currentMode = prefs.getInt(KEY_MODE, MODE_VOICE_TO_TEXT);
        currentThreshold = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD);

        // Dark theme background
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0F172A")); // Deep Slate 900

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(18), dpToPx(24), dpToPx(18), dpToPx(36));
        scrollView.addView(root);

        // Header Section
        buildHeader(root);

        // Warning banner for Accessibility (if not yet enabled)
        buildA11yBanner(root);

        // 1. AI App Selection Section
        buildAiAppSelector(root);

        addSpacer(root, 22);

        // 2. Mode Selector Section
        buildModeSelector(root);

        addSpacer(root, 22);

        // 3. Sensitivity Slider Section
        buildSensitivityCard(root);

        addSpacer(root, 22);

        // 4. System Readiness & Permissions Card
        buildStatusCard(root);

        addSpacer(root, 26);

        // 5. Action Buttons (Start / Stop & Test)
        buildActionButtons(root);

        addSpacer(root, 24);

        // 6. Device Tips (Infinix Hot 12 Pro / Android 12)
        buildDeviceTips(root);

        setContentView(scrollView);
        updateStatus();
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout headerCard = new LinearLayout(this);
        headerCard.setOrientation(LinearLayout.VERTICAL);
        headerCard.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
        headerCard.setBackground(createCard(Color.parseColor("#1E293B"), Color.parseColor("#334155"), 1, dpToPx(18)));

        TextView badge = new TextView(this);
        badge.setText("⚡ SHAKE TO AI ASSISTANT");
        badge.setTextSize(11);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextColor(Color.parseColor("#38BDF8"));
        badge.setPadding(0, 0, 0, dpToPx(8));
        headerCard.addView(badge);

        TextView title = new TextView(this);
        title.setText("Instant AI Voice Dictation");
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#F8FAFC"));
        headerCard.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Shake your phone from any screen to open your selected AI and dictate prompts for coding, planning, or academic research.");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setPadding(0, dpToPx(6), 0, 0);
        headerCard.addView(subtitle);

        root.addView(headerCard);
        addSpacer(root, 16);
    }

    private void buildA11yBanner(LinearLayout root) {
        bannerA11yWarning = new LinearLayout(this);
        bannerA11yWarning.setOrientation(LinearLayout.VERTICAL);
        bannerA11yWarning.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        bannerA11yWarning.setBackground(createCard(Color.parseColor("#422006"), Color.parseColor("#B45309"), 1, dpToPx(14)));

        TextView bannerText = new TextView(this);
        bannerText.setText("⚠️ Microphone Auto-Trigger is OFF\nTap here to turn on 'ShakeAI Voice Helper' in Accessibility Settings so your AI opens directly in voice dictation mode.");
        bannerText.setTextColor(Color.parseColor("#FDE68A"));
        bannerText.setTextSize(12);
        bannerText.setTypeface(null, Typeface.BOLD);
        bannerA11yWarning.addView(bannerText);

        bannerA11yWarning.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(bannerA11yWarning);
        addSpacer(root, 16);
    }

    private void buildAiAppSelector(LinearLayout root) {
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("1. Choose Your AI App");
        sectionTitle.setTextSize(15);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionTitle.setTextColor(Color.parseColor("#F1F5F9"));
        sectionTitle.setPadding(0, 0, 0, dpToPx(10));
        root.addView(sectionTitle);

        appsContainer = new LinearLayout(this);
        appsContainer.setOrientation(LinearLayout.VERTICAL);
        renderAiAppCards();
        root.addView(appsContainer);
    }

    private void renderAiAppCards() {
        appsContainer.removeAllViews();
        appCardViews.clear();

        for (ShakeService.AiAppInfo app : ShakeService.SUPPORTED_APPS) {
            boolean isSelected = app.id.equalsIgnoreCase(currentAiId);
            boolean isInstalled = isPackageInstalled(app.packageName);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

            // Card background: tinted if selected
            int bgColor = isSelected ? Color.parseColor("#1E293B") : Color.parseColor("#141E33");
            int strokeColor = isSelected ? app.accentColor : Color.parseColor("#26334D");
            int strokeWidth = isSelected ? 2 : 1;
            card.setBackground(createCard(bgColor, strokeColor, strokeWidth, dpToPx(14)));

            // Top row: [Radio Indicator + Name] ... [Installed Badge]
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView indicator = new TextView(this);
            indicator.setText(isSelected ? "◉ " : "○ ");
            indicator.setTextSize(16);
            indicator.setTextColor(isSelected ? app.accentColor : Color.parseColor("#64748B"));
            topRow.addView(indicator);

            TextView nameView = new TextView(this);
            nameView.setText(app.name);
            nameView.setTextSize(16);
            nameView.setTypeface(null, Typeface.BOLD);
            nameView.setTextColor(isSelected ? app.accentColor : Color.parseColor("#F8FAFC"));

            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            topRow.addView(nameView, nameParams);

            // Badge
            TextView badge = new TextView(this);
            badge.setTextSize(11);
            badge.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
            if (isInstalled) {
                badge.setText("Installed ✓");
                badge.setTextColor(Color.parseColor("#4ADE80"));
                badge.setBackground(createCard(Color.parseColor("#064E3B"), Color.parseColor("#059669"), 1, dpToPx(10)));
            } else {
                badge.setText("Install App ↗");
                badge.setTextColor(Color.parseColor("#FDBA74"));
                badge.setBackground(createCard(Color.parseColor("#451A03"), Color.parseColor("#D97706"), 1, dpToPx(10)));
                badge.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + app.packageName)));
                    } catch (Exception e) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(app.webUrl)));
                    }
                });
            }
            topRow.addView(badge);
            card.addView(topRow);

            // Tagline
            TextView taglineView = new TextView(this);
            taglineView.setText(app.tagline);
            taglineView.setTextSize(12);
            taglineView.setTextColor(Color.parseColor("#94A3B8"));
            taglineView.setPadding(dpToPx(24), dpToPx(4), 0, 0);
            card.addView(taglineView);

            card.setOnClickListener(v -> {
                currentAiId = app.id;
                prefs.edit().putString(ShakeService.KEY_SELECTED_AI, currentAiId).apply();
                renderAiAppCards();
                Toast.makeText(this, "Shake target set to " + app.name, Toast.LENGTH_SHORT).show();
            });

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, dpToPx(10));
            card.setLayoutParams(cardParams);

            appsContainer.addView(card);
            appCardViews.add(card);
        }
    }

    private void buildModeSelector(LinearLayout root) {
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("2. Interaction Mode");
        sectionTitle.setTextSize(15);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionTitle.setTextColor(Color.parseColor("#F1F5F9"));
        sectionTitle.setPadding(0, 0, 0, dpToPx(10));
        root.addView(sectionTitle);

        LinearLayout modeCard = new LinearLayout(this);
        modeCard.setOrientation(LinearLayout.VERTICAL);
        modeCard.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(16));
        modeCard.setBackground(createCard(Color.parseColor("#1E293B"), Color.parseColor("#334155"), 1, dpToPx(16)));

        RadioGroup radioGroup = new RadioGroup(this);

        RadioButton rbVoice = new RadioButton(this);
        rbVoice.setId(View.generateViewId());
        rbVoice.setText("🎙️ Voice-to-Text Mode (Replies in Text) [Recommended]\nOpens AI app and activates the microphone to transcribe your prompt. The AI responds in written text & code.");
        rbVoice.setTextColor(Color.parseColor("#E2E8F0"));
        rbVoice.setTextSize(13);
        rbVoice.setPadding(0, 0, 0, dpToPx(16));

        RadioButton rbAppOnly = new RadioButton(this);
        rbAppOnly.setId(View.generateViewId());
        rbAppOnly.setText("📱 Open App Directly\nOpens your selected AI app to the chat screen without auto-triggering the microphone.");
        rbAppOnly.setTextColor(Color.parseColor("#CBD5E1"));
        rbAppOnly.setTextSize(13);
        rbAppOnly.setPadding(0, 0, 0, dpToPx(16));

        RadioButton rbOverlay = new RadioButton(this);
        rbOverlay.setId(View.generateViewId());
        rbOverlay.setText("⚡ System Assistant Overlay\nInvokes device digital assistant overlay (Gemini / Google Voice).");
        rbOverlay.setTextColor(Color.parseColor("#94A3B8"));
        rbOverlay.setTextSize(13);

        radioGroup.addView(rbVoice);
        radioGroup.addView(rbAppOnly);
        radioGroup.addView(rbOverlay);

        if (currentMode == MODE_APP_DIRECT) {
            rbAppOnly.setChecked(true);
        } else if (currentMode == MODE_ASSISTANT_OVERLAY) {
            rbOverlay.setChecked(true);
        } else {
            rbVoice.setChecked(true);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int newMode = MODE_VOICE_TO_TEXT;
            if (checkedId == rbAppOnly.getId()) {
                newMode = MODE_APP_DIRECT;
            } else if (checkedId == rbOverlay.getId()) {
                newMode = MODE_ASSISTANT_OVERLAY;
            }
            currentMode = newMode;
            prefs.edit().putInt(KEY_MODE, newMode).apply();
            Toast.makeText(this, "Mode updated!", Toast.LENGTH_SHORT).show();
        });

        modeCard.addView(radioGroup);
        root.addView(modeCard);
    }

    private void buildSensitivityCard(LinearLayout root) {
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("3. Shake Sensitivity");
        sectionTitle.setTextSize(15);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionTitle.setTextColor(Color.parseColor("#F1F5F9"));
        sectionTitle.setPadding(0, 0, 0, dpToPx(10));
        root.addView(sectionTitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(16));
        card.setBackground(createCard(Color.parseColor("#1E293B"), Color.parseColor("#334155"), 1, dpToPx(16)));

        TextView thresholdLabel = new TextView(this);
        thresholdLabel.setText(formatLabel(currentThreshold));
        thresholdLabel.setTextColor(Color.parseColor("#38BDF8"));
        thresholdLabel.setTypeface(null, Typeface.BOLD);
        thresholdLabel.setTextSize(14);
        thresholdLabel.setPadding(0, 0, 0, dpToPx(14));
        card.addView(thresholdLabel);

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
        card.addView(seekBar);
        root.addView(card);
    }

    private void buildStatusCard(LinearLayout root) {
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("4. System Readiness");
        sectionTitle.setTextSize(15);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionTitle.setTextColor(Color.parseColor("#F1F5F9"));
        sectionTitle.setPadding(0, 0, 0, dpToPx(10));
        root.addView(sectionTitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(16));
        card.setBackground(createCard(Color.parseColor("#1E293B"), Color.parseColor("#334155"), 1, dpToPx(16)));

        statusOverlay = new TextView(this);
        statusOverlay.setPadding(0, 0, 0, dpToPx(10));
        card.addView(statusOverlay);

        statusAccessibility = new TextView(this);
        statusAccessibility.setPadding(0, 0, 0, dpToPx(14));
        card.addView(statusAccessibility);

        LinearLayout buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnA11y = createStyledButton("Open Accessibility", Color.parseColor("#334155"), Color.parseColor("#E2E8F0"));
        btnA11y.setTextSize(12);
        btnA11y.setOnClickListener(v -> openAccessibilitySettings());

        Button btnAssistant = createStyledButton("Digital Assistant", Color.parseColor("#334155"), Color.parseColor("#E2E8F0"));
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

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        p1.setMargins(0, 0, dpToPx(8), 0);
        buttonsRow.addView(btnA11y, p1);

        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        buttonsRow.addView(btnAssistant, p2);

        card.addView(buttonsRow);
        root.addView(card);
    }

    private void buildActionButtons(LinearLayout root) {
        startStopBtn = new Button(this);
        updateStartStopButtonState();
        startStopBtn.setOnClickListener(v -> {
            if (ShakeService.isRunning()) {
                stopService(new Intent(this, ShakeService.class));
                Toast.makeText(this, "Shake Listener Stopped", Toast.LENGTH_SHORT).show();
            } else {
                checkPermissionsAndStartService();
            }
            updateStartStopButtonState();
        });
        root.addView(startStopBtn);

        addSpacer(root, 12);

        Button testBtn = createStyledButton("⚡ Test Shake Trigger Now", Color.parseColor("#1E293B"), Color.parseColor("#38BDF8"));
        testBtn.setTypeface(null, Typeface.BOLD);
        testBtn.setOnClickListener(v -> {
            if (!GeminiVoiceAccessibilityService.isAccessibilityEnabled(this) && currentMode == MODE_VOICE_TO_TEXT) {
                showAccessibilityPromptDialog();
            } else {
                ShakeService.AiAppInfo app = ShakeService.getAiApp(currentAiId);
                Toast.makeText(this, "Testing " + app.name + " launch & voice...", Toast.LENGTH_SHORT).show();
                ShakeService.launchSelectedAi(this, currentMode, currentAiId);
            }
        });
        root.addView(testBtn);
    }

    private void updateStartStopButtonState() {
        if (ShakeService.isRunning()) {
            startStopBtn.setText("■ Stop Shake Listener");
            startStopBtn.setTextColor(Color.WHITE);
            startStopBtn.setTypeface(null, Typeface.BOLD);
            startStopBtn.setBackground(createCard(Color.parseColor("#DC2626"), Color.parseColor("#EF4444"), 0, dpToPx(14)));
        } else {
            startStopBtn.setText("▶ Start Shake Listener");
            startStopBtn.setTextColor(Color.WHITE);
            startStopBtn.setTypeface(null, Typeface.BOLD);
            startStopBtn.setBackground(createCard(Color.parseColor("#2563EB"), Color.parseColor("#3B82F6"), 0, dpToPx(14)));
        }
    }

    private void buildDeviceTips(LinearLayout root) {
        LinearLayout tipsCard = new LinearLayout(this);
        tipsCard.setOrientation(LinearLayout.VERTICAL);
        tipsCard.setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(14));
        tipsCard.setBackground(createCard(Color.parseColor("#131D31"), Color.parseColor("#1E293B"), 1, dpToPx(14)));

        TextView tipsTitle = new TextView(this);
        tipsTitle.setText("💡 Background Reliability (Infinix Hot 12 Pro / Android 12):");
        tipsTitle.setTextSize(12);
        tipsTitle.setTypeface(null, Typeface.BOLD);
        tipsTitle.setTextColor(Color.parseColor("#94A3B8"));
        tipsTitle.setPadding(0, 0, 0, dpToPx(6));
        tipsCard.addView(tipsTitle);

        TextView tipsText = new TextView(this);
        tipsText.setText("• Lock ShakeAI in your Recents app switcher to keep it always alive.\n• Set Battery Optimization to 'Don't Optimize' / 'Unrestricted'.\n• Turn ON 'ShakeAI Voice Helper' in Accessibility for automatic microphone activation.");
        tipsText.setTextSize(11);
        tipsText.setTextColor(Color.parseColor("#64748B"));
        tipsCard.addView(tipsText);

        root.addView(tipsCard);
    }

    private void checkPermissionsAndStartService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant 'Display over other apps' to allow shake trigger from anywhere!", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        if (!GeminiVoiceAccessibilityService.isAccessibilityEnabled(this) && currentMode == MODE_VOICE_TO_TEXT) {
            showAccessibilityPromptDialog();
            return;
        }

        startShakeService();
    }

    private void showAccessibilityPromptDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Voice Auto-Trigger")
                .setMessage("To automatically tap the Microphone button inside your AI app when you shake, enable 'ShakeAI Voice Helper' in Accessibility Settings.\n\nTap 'Enable Now' -> Look for 'Installed Services' or 'Downloaded Apps' -> Turn 'ShakeAI Voice Helper' ON.")
                .setPositiveButton("Enable Now", (d, w) -> openAccessibilitySettings())
                .setNegativeButton("Skip for Now", (d, w) -> startShakeService())
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Find 'ShakeAI Voice Helper' in Installed Services and turn it ON", Toast.LENGTH_LONG).show();
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
        updateStartStopButtonState();
        ShakeService.AiAppInfo app = ShakeService.getAiApp(currentAiId);
        Toast.makeText(this, "Shake listener active for " + app.name + "! Shake phone to test.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        renderAiAppCards();
        updateStartStopButtonState();
    }

    private void updateStatus() {
        boolean hasOverlay = Settings.canDrawOverlays(this);
        statusOverlay.setText("• Display Over Other Apps: " + (hasOverlay ? "Granted ✓" : "Missing ✕ (Required)"));
        statusOverlay.setTextColor(hasOverlay ? Color.parseColor("#4ADE80") : Color.parseColor("#F87171"));

        boolean a11yActive = GeminiVoiceAccessibilityService.isAccessibilityEnabled(this);
        statusAccessibility.setText("• Voice Auto-Trigger Helper: " + (a11yActive ? "Active ✓" : "Disabled ✕ (Required for Mic Auto-Click)"));
        statusAccessibility.setTextColor(a11yActive ? Color.parseColor("#4ADE80") : Color.parseColor("#F87171"));

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

    private Button createStyledButton(String text, int bgColor, int textColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setBackground(createCard(bgColor, Color.parseColor("#475569"), 1, dpToPx(14)));
        return btn;
    }

    private GradientDrawable createCard(int bgColor, int strokeColor, int strokeWidthDp, int cornerRadiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(cornerRadiusPx);
        if (strokeWidthDp > 0) {
            gd.setStroke(dpToPx(strokeWidthDp), strokeColor);
        }
        return gd;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void addSpacer(LinearLayout layout, int heightDp) {
        View spacer = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp));
        spacer.setLayoutParams(params);
        layout.addView(spacer);
    }

    private String formatLabel(float val) {
        String feel;
        if (val < 2.2f) {
            feel = "Light Flick";
        } else if (val <= 3.0f) {
            feel = "Balanced (Recommended)";
        } else {
            feel = "Firm Shake";
        }
        return String.format(Locale.US, "Threshold: %.1f G  •  %s", val, feel);
    }
}
