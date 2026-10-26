package com.example.shakegemini;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GeminiVoiceAccessibilityService extends AccessibilityService {

    private static final String TAG = "ShakeAiA11y";
    private static volatile GeminiVoiceAccessibilityService instance = null;
    private static volatile boolean pendingAutoClick = false;
    private static volatile long requestTimestamp = 0;
    private static final long TIMEOUT_MS = 7000;

    private Handler mainHandler;

    public static void requestAutoVoiceClick() {
        pendingAutoClick = true;
        requestTimestamp = SystemClock.uptimeMillis();
        Log.d(TAG, "AI Voice-to-Text auto-trigger armed");

        if (instance != null) {
            instance.startRetryPolling();
        }
    }

    public static boolean isServiceRunning() {
        return instance != null;
    }

    public static boolean isAccessibilityEnabled(Context context) {
        String prefString = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (prefString != null) {
            String expected = context.getPackageName() + "/" + GeminiVoiceAccessibilityService.class.getName();
            return prefString.contains(expected) || prefString.contains(GeminiVoiceAccessibilityService.class.getSimpleName());
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        mainHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "Accessibility service connected and armed for multi-AI voice typing");

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 50;
        setServiceInfo(info);
    }

    private void startRetryPolling() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        mainHandler.removeCallbacksAndMessages(null);

        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!pendingAutoClick) return;

                if (SystemClock.uptimeMillis() - requestTimestamp > TIMEOUT_MS) {
                    pendingAutoClick = false;
                    Log.d(TAG, "Auto-trigger timed out");
                    return;
                }

                if (tryClickVoiceOnActiveScreen()) {
                    pendingAutoClick = false;
                    Log.d(TAG, "Microphone (voice-to-text) button clicked successfully by polling loop!");
                    return;
                }

                mainHandler.postDelayed(this, 200);
            }
        };

        mainHandler.postDelayed(checkRunnable, 350);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!pendingAutoClick) return;

        if (SystemClock.uptimeMillis() - requestTimestamp > TIMEOUT_MS) {
            pendingAutoClick = false;
            return;
        }

        CharSequence pkgChar = event.getPackageName();
        if (pkgChar == null) return;
        String pkg = pkgChar.toString();

        if (isTargetAiPackage(pkg)) {
            if (tryClickVoiceOnActiveScreen()) {
                pendingAutoClick = false;
                Log.d(TAG, "Microphone (voice-to-text) clicked via event!");
            }
        }
    }

    private boolean isTargetAiPackage(String pkg) {
        if (pkg == null) return false;
        return pkg.contains("apps.bard") || pkg.contains("googlequicksearchbox")
                || pkg.contains("openai.chatgpt") || pkg.contains("anthropic.claude")
                || pkg.contains("deepseek.chat") || pkg.contains("perplexity");
    }

    private boolean tryClickVoiceOnActiveScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            try {
                if (isTargetAiWindow(root)) {
                    if (selectAndClickMicButton(root)) {
                        return true;
                    }
                }
            } finally {
                root.recycle();
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo win : windows) {
                    AccessibilityNodeInfo winRoot = win.getRoot();
                    if (winRoot != null) {
                        try {
                            if (isTargetAiWindow(winRoot)) {
                                if (selectAndClickMicButton(winRoot)) {
                                    return true;
                                }
                            }
                        } finally {
                            winRoot.recycle();
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isTargetAiWindow(AccessibilityNodeInfo node) {
        CharSequence pkg = node.getPackageName();
        if (pkg != null) {
            return isTargetAiPackage(pkg.toString());
        }
        return false;
    }

    // Exclude spoken voice chat modes (e.g. Gemini Live, ChatGPT Headphones/Voice mode)
    private boolean isSpokenAudioModeNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString().toLowerCase(Locale.ROOT);
            if (d.contains("live") || d.contains("headphone") || d.contains("voice mode") || d.contains("start call")) {
                return true;
            }
        }

        String id = node.getViewIdResourceName();
        if (id != null) {
            String v = id.toLowerCase(Locale.ROOT);
            if (v.contains("live") || v.contains("headphone") || v.contains("voice_mode")) {
                return true;
            }
        }

        CharSequence text = node.getText();
        if (text != null) {
            String t = text.toString().toLowerCase(Locale.ROOT);
            if (t.contains("live") || t.contains("headphone")) {
                return true;
            }
        }

        return false;
    }

    private boolean isTextVoiceMicNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (isSpokenAudioModeNode(node)) return false;

        // 1. Content Description
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString().toLowerCase(Locale.ROOT);
            if (d.contains("live") || d.contains("headphone")) return false;
            if (d.contains("mic") || d.contains("voice") || d.contains("speak") ||
                d.contains("dictat") || d.contains("audio") || d.contains("record") ||
                d.contains("speech") || d.contains("sound") || d.contains("transcrib")) {
                return true;
            }
        }

        // 2. View ID
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String v = viewId.toLowerCase(Locale.ROOT);
            if (v.contains("live") || v.contains("headphone")) return false;
            if (v.contains("mic") || v.contains("voice") || v.contains("speech") ||
                v.contains("dictat") || v.contains("audio")) {
                return true;
            }
        }

        // 3. Text
        CharSequence text = node.getText();
        if (text != null) {
            String t = text.toString().toLowerCase(Locale.ROOT);
            if (t.contains("live") || t.contains("headphone")) return false;
            if (t.contains("mic") || t.contains("voice") || t.contains("speak") || t.contains("dictat")) {
                return true;
            }
        }

        return false;
    }

    private boolean selectAndClickMicButton(AccessibilityNodeInfo root) {
        Rect screenBounds = new Rect();
        root.getBoundsInScreen(screenBounds);
        int screenHeight = screenBounds.height() > 0 ? screenBounds.height() : getResources().getDisplayMetrics().heightPixels;
        int screenWidth = screenBounds.width() > 0 ? screenBounds.width() : getResources().getDisplayMetrics().widthPixels;

        List<AccessibilityNodeInfo> micCandidates = new ArrayList<>();
        List<AccessibilityNodeInfo> allBottomIcons = new ArrayList<>();

        collectCandidateNodes(root, micCandidates, allBottomIcons, screenHeight, screenWidth, 0);

        // Priority 1: Explicit matching node
        if (!micCandidates.isEmpty()) {
            AccessibilityNodeInfo bestMic = null;
            for (AccessibilityNodeInfo cand : micCandidates) {
                Rect b = new Rect();
                cand.getBoundsInScreen(b);
                if (b.bottom > screenHeight * 0.60) {
                    bestMic = cand;
                    break;
                }
            }
            if (bestMic == null) bestMic = micCandidates.get(0);

            Log.d(TAG, "Selected explicit Microphone (voice-to-text) node: " + bestMic.getContentDescription());
            return clickNodeWithGesture(bestMic);
        }

        // Priority 2: Fallback bottom row icons
        if (!allBottomIcons.isEmpty()) {
            allBottomIcons.sort((a, b) -> {
                Rect ra = new Rect();
                Rect rb = new Rect();
                a.getBoundsInScreen(ra);
                b.getBoundsInScreen(rb);
                return Integer.compare(ra.centerX(), rb.centerX());
            });

            AccessibilityNodeInfo chosenNode = null;
            if (allBottomIcons.size() >= 2) {
                // If there are multiple icons on the right, the one before the far-right icon is the Microphone
                chosenNode = allBottomIcons.get(allBottomIcons.size() - 2);
            } else {
                chosenNode = allBottomIcons.get(0);
                Rect b = new Rect();
                chosenNode.getBoundsInScreen(b);
                if (b.centerX() > screenWidth * 0.93) {
                    Log.w(TAG, "Only icon found is at extreme right edge, skipping to prevent audio chat mode.");
                    return false;
                }
            }

            if (chosenNode != null) {
                Log.d(TAG, "Selected fallback Microphone icon (replies in text)");
                return clickNodeWithGesture(chosenNode);
            }
        }

        return false;
    }

    private void collectCandidateNodes(AccessibilityNodeInfo node,
                                       List<AccessibilityNodeInfo> micCandidates,
                                       List<AccessibilityNodeInfo> allBottomIcons,
                                       int screenHeight, int screenWidth, int depth) {
        if (node == null || depth > 30) return;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        boolean isAudioMode = isSpokenAudioModeNode(node);

        if (!isAudioMode && isTextVoiceMicNode(node)) {
            micCandidates.add(node);
        } else if (bounds.bottom > (screenHeight * 0.70) && bounds.width() > 25 && bounds.height() > 25) {
            CharSequence className = node.getClassName();
            String cls = className != null ? className.toString() : "";
            if (cls.contains("Button") || cls.contains("ImageView") || node.isClickable()) {
                if (!isAudioMode && bounds.centerX() > (screenWidth * 0.40)) {
                    allBottomIcons.add(node);
                }
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectCandidateNodes(child, micCandidates, allBottomIcons, screenHeight, screenWidth, depth + 1);
            }
        }
    }

    private boolean clickNodeWithGesture(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return false;
        }

        int x = bounds.centerX();
        int y = bounds.centerY();

        boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);

        if (!clicked) {
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null) {
                try {
                    clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } finally {
                    parent.recycle();
                }
            }
        }

        boolean gestureSuccess = dispatchTap(x, y);

        Log.d(TAG, "Tapped Microphone at (" + x + ", " + y + ") a11y=" + clicked + " gesture=" + gestureSuccess);
        return clicked || gestureSuccess;
    }

    private boolean dispatchTap(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 60);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return dispatchGesture(builder.build(), null, null);
    }

    @Override
    public void onInterrupt() {
        pendingAutoClick = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        pendingAutoClick = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}
