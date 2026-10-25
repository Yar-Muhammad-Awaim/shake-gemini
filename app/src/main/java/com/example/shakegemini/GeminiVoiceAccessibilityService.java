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
import java.util.List;
import java.util.Locale;

public class GeminiVoiceAccessibilityService extends AccessibilityService {

    private static final String TAG = "ShakeGeminiA11y";
    private static volatile GeminiVoiceAccessibilityService instance = null;
    private static volatile boolean pendingAutoClick = false;
    private static volatile long requestTimestamp = 0;
    private static final long TIMEOUT_MS = 7000;

    private Handler mainHandler;

    public static void requestAutoVoiceClick() {
        pendingAutoClick = true;
        requestTimestamp = SystemClock.uptimeMillis();
        Log.d(TAG, "Gemini voice auto-trigger armed");

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
        Log.d(TAG, "Accessibility service connected and armed");

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
                    Log.d(TAG, "Voice button successfully clicked by polling loop!");
                    return;
                }

                // Poll again in 200ms
                mainHandler.postDelayed(this, 200);
            }
        };

        // Delay first check slightly to let the Gemini window animate in
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

        if (pkg.contains("apps.bard") || pkg.contains("googlequicksearchbox")) {
            if (tryClickVoiceOnActiveScreen()) {
                pendingAutoClick = false;
                Log.d(TAG, "Voice button clicked via accessibility event!");
            }
        }
    }

    private boolean tryClickVoiceOnActiveScreen() {
        // 1. Check primary active window root
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            try {
                if (isGeminiWindow(root)) {
                    if (searchAndClickVoice(root, 0)) {
                        return true;
                    }
                    if (fallbackBottomRightClick(root)) {
                        return true;
                    }
                }
            } finally {
                root.recycle();
            }
        }

        // 2. Also inspect all interactive windows (e.g., bottom sheets, overlays)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo win : windows) {
                    AccessibilityNodeInfo winRoot = win.getRoot();
                    if (winRoot != null) {
                        try {
                            if (isGeminiWindow(winRoot)) {
                                if (searchAndClickVoice(winRoot, 0)) {
                                    return true;
                                }
                                if (fallbackBottomRightClick(winRoot)) {
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

    private boolean isGeminiWindow(AccessibilityNodeInfo node) {
        CharSequence pkg = node.getPackageName();
        if (pkg != null) {
            String p = pkg.toString();
            return p.contains("apps.bard") || p.contains("googlequicksearchbox");
        }
        return false;
    }

    private boolean searchAndClickVoice(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 25) return false;

        if (isVoiceNode(node)) {
            Log.d(TAG, "Found voice node: " + node.getContentDescription() + " | " + node.getViewIdResourceName());
            if (clickNodeWithGesture(node)) {
                return true;
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    if (searchAndClickVoice(child, depth + 1)) {
                        return true;
                    }
                } finally {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private boolean isVoiceNode(AccessibilityNodeInfo node) {
        // 1. Content Description
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString().toLowerCase(Locale.ROOT);
            if (d.contains("mic") || d.contains("voice") || d.contains("speak") ||
                d.contains("dictat") || d.contains("talk") || d.contains("live") ||
                d.contains("audio") || d.contains("record") || d.contains("speech")) {
                return true;
            }
        }

        // 2. View ID
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String v = viewId.toLowerCase(Locale.ROOT);
            if (v.contains("mic") || v.contains("voice") || v.contains("speech") ||
                v.contains("live") || v.contains("audio") || v.contains("dictat")) {
                return true;
            }
        }

        // 3. Text
        CharSequence text = node.getText();
        if (text != null) {
            String t = text.toString().toLowerCase(Locale.ROOT);
            if (t.contains("live") || t.contains("voice") || t.contains("mic") || t.contains("speak")) {
                return true;
            }
        }

        return false;
    }

    // Fallback: If no node matched the keyword search, find the bottom prompt bar's mic/live button
    private boolean fallbackBottomRightClick(AccessibilityNodeInfo root) {
        Rect screenBounds = new Rect();
        root.getBoundsInScreen(screenBounds);
        int screenHeight = screenBounds.height();
        if (screenHeight <= 0) screenHeight = 2400;

        return findCandidateNearBottom(root, screenHeight, 0);
    }

    private boolean findCandidateNearBottom(AccessibilityNodeInfo node, int screenHeight, int depth) {
        if (node == null || depth > 25) return false;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        // Look for buttons located in the bottom 30% of the screen
        if (bounds.bottom > (screenHeight * 0.70) && bounds.width() > 30 && bounds.height() > 30) {
            CharSequence className = node.getClassName();
            if (className != null) {
                String cls = className.toString();
                if (cls.contains("Button") || cls.contains("ImageView") || node.isClickable()) {
                    // Check if it's placed on the right side of the bottom row (where mic icon sits)
                    if (bounds.centerX() > (screenBoundsWidth() * 0.55)) {
                        Log.d(TAG, "Fallback click on bottom-right action icon at: " + bounds);
                        if (clickNodeWithGesture(node)) {
                            return true;
                        }
                    }
                }
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    if (findCandidateNearBottom(child, screenHeight, depth + 1)) {
                        return true;
                    }
                } finally {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private int screenBoundsWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private boolean clickNodeWithGesture(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return false;
        }

        int x = bounds.centerX();
        int y = bounds.centerY();

        // 1. Accessibility click on node
        boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);

        // 2. Accessibility click on parent if node was not clickable
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

        // 3. Physical touch tap via dispatchGesture (simulates physical finger tap at exact icon coordinates)
        boolean gestureSuccess = dispatchTap(x, y);

        Log.d(TAG, "Click attempt at (" + x + ", " + y + ") a11y=" + clicked + " gesture=" + gestureSuccess);
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
