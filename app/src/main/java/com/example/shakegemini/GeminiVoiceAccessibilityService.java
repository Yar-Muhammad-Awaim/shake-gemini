package com.example.shakegemini;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

public class GeminiVoiceAccessibilityService extends AccessibilityService {

    private static final String TAG = "ShakeGeminiA11y";
    private static volatile boolean serviceRunning = false;
    private static volatile boolean pendingAutoClick = false;
    private static volatile long requestTimestamp = 0;
    private static final long TIMEOUT_MS = 7000;

    public static void requestAutoVoiceClick() {
        pendingAutoClick = true;
        requestTimestamp = SystemClock.uptimeMillis();
        Log.d(TAG, "Gemini voice auto-trigger armed");
    }

    public static boolean isServiceRunning() {
        return serviceRunning;
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
        serviceRunning = true;
        Log.d(TAG, "Accessibility service connected and ready");

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
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
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                try {
                    if (findAndClickVoiceButton(root, 0)) {
                        pendingAutoClick = false;
                        Log.d(TAG, "Successfully triggered Gemini voice button!");
                    }
                } finally {
                    root.recycle();
                }
            }
        }
    }

    private boolean findAndClickVoiceButton(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 20) return false;

        if (isVoiceNode(node)) {
            if (clickNodeOrParent(node)) {
                return true;
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    if (findAndClickVoiceButton(child, depth + 1)) {
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
        // 1. Content Description (e.g. "Microphone", "Voice message", "Gemini Live", "Live", "Use microphone")
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString().toLowerCase(Locale.ROOT);
            if (d.contains("mic") || d.contains("voice") || d.contains("speak") ||
                d.contains("live") || d.contains("audio") || d.contains("record")) {
                return true;
            }
        }

        // 2. View Resource ID
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String v = viewId.toLowerCase(Locale.ROOT);
            if (v.contains("mic") || v.contains("voice") || v.contains("speech") ||
                v.contains("live") || v.contains("audio")) {
                return true;
            }
        }

        // 3. Visible text
        CharSequence text = node.getText();
        if (text != null) {
            String t = text.toString().toLowerCase(Locale.ROOT);
            if (t.contains("live") || t.contains("voice") || t.contains("mic")) {
                return true;
            }
        }

        return false;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        if (node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            try {
                if (parent.isClickable()) {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                AccessibilityNodeInfo grandParent = parent.getParent();
                if (grandParent != null) {
                    try {
                        if (grandParent.isClickable()) {
                            return grandParent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        }
                    } finally {
                        grandParent.recycle();
                    }
                }
            } finally {
                parent.recycle();
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        pendingAutoClick = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        serviceRunning = false;
        pendingAutoClick = false;
    }
}
