package com.vernu.sms.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.vernu.sms.helpers.ComposerTapHelper;
import com.vernu.sms.helpers.TextBeeForegroundHelper;

public class RCSAccessibilityService extends AccessibilityService {

    private static final String TAG = "RCSAccessibilityService";
    private static RCSAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility connected");
    }

    public static boolean isConnected() {
        return instance != null;
    }

    /**
     * Tap + return to TextBee only if the compose field has prefilled text.
     */
    public static void tryAutoClickIfComposerPrefilled(android.content.Context context) {
        if (instance == null) {
            Log.e(TAG, "Accessibility service not connected");
            ComposerTapHelper.clearAutoTapSession();
            return;
        }
        if (!ComposerTapHelper.isAwaitingPrefillTap()) {
            return;
        }

        String composeText = instance.readComposeFieldText();
        if (!instance.isComposeFieldPrefilled(composeText)) {
            Log.d(TAG, "Compose field empty — skip auto-click, stay in Messages");
            ComposerTapHelper.clearAutoTapSession();
            return;
        }

        Log.d(TAG, "Compose prefilled (\"" + composeText.length() + " chars) — auto-tapping Send");
        int x = ComposerTapHelper.getTapX(context);
        int y = ComposerTapHelper.getTapY(context);
        instance.performClick(x, y, true);
    }

    /** Test tap — no prefill check, no return to TextBee. */
    public static void performClickAtSavedPositionForced(android.content.Context context) {
        if (instance == null) {
            Log.e(TAG, "Accessibility service not connected");
            return;
        }
        int x = ComposerTapHelper.getTapX(context);
        int y = ComposerTapHelper.getTapY(context);
        instance.performClick(x, y, false);
    }

    private boolean isComposeFieldPrefilled(String composeText) {
        if (composeText == null || composeText.trim().isEmpty()) {
            return false;
        }
        String expected = ComposerTapHelper.getExpectedComposeText();
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        String actual = composeText.trim();
        if (actual.equals(expected)) {
            return true;
        }
        return actual.contains(expected) || expected.contains(actual);
    }

    private String readComposeFieldText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return "";
        }
        try {
            String fromComposeId = findComposeTextByViewId(root);
            if (fromComposeId != null && !fromComposeId.trim().isEmpty()) {
                return fromComposeId.trim();
            }
            String fromEditable = findComposeTextInEditable(root);
            return fromEditable != null ? fromEditable.trim() : "";
        } finally {
            root.recycle();
        }
    }

    private String findComposeTextByViewId(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String lower = viewId.toLowerCase();
            if ((lower.contains("compose") || lower.contains("message"))
                    && node.isEditable()) {
                CharSequence text = node.getText();
                if (text != null && text.length() > 0) {
                    return text.toString();
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            String found = findComposeTextByViewId(child);
            child.recycle();
            if (found != null && !found.isEmpty()) {
                return found;
            }
        }
        return null;
    }

    private String findComposeTextInEditable(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isEditable()) {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                return text.toString();
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            String found = findComposeTextInEditable(child);
            child.recycle();
            if (found != null && !found.isEmpty()) {
                return found;
            }
        }
        return null;
    }

    private void performClick(int x, int y, boolean returnToTextBeeAfter) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();

        boolean started = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "Auto tap completed at " + x + ", " + y);
                ComposerTapHelper.clearAutoTapSession();
                if (returnToTextBeeAfter) {
                    TextBeeForegroundHelper.returnToTextBeeAfterTap(RCSAccessibilityService.this);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.e(TAG, "Auto tap cancelled at " + x + ", " + y);
                ComposerTapHelper.clearAutoTapSession();
                Toast.makeText(
                        RCSAccessibilityService.this,
                        "Auto tap cancelled — check Accessibility is on",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }, handler);

        Log.d(TAG, "dispatchGesture started: " + started);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !ComposerTapHelper.isAwaitingPrefillTap()) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (!ComposerTapHelper.isMessagingPackage(pkg != null ? pkg.toString() : null)) {
            return;
        }
        ComposerTapHelper.onMessagingWindowOpened(this);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
