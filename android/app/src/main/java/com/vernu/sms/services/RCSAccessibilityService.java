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
import com.vernu.sms.helpers.MessagingComposerHelper;
import com.vernu.sms.helpers.TextBeeForegroundHelper;

public class RCSAccessibilityService extends AccessibilityService {

    private static final String TAG = "RCSAccessibilityService";
    private static RCSAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private enum RcsAvailability {
        AVAILABLE,
        SMS_ONLY,
        UNKNOWN,
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility connected");
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public enum AutoClickResult {
        SUCCESS,
        RETRY,
    }

    /**
     * Tap + return to TextBee only if the compose field has prefilled text AND the
     * conversation is positively detected as RCS. SMS-only conversations are blocked
     * immediately; inconclusive signals keep retrying until the session expires
     * (the message is then reported as failed — strict RCS-only delivery).
     */
    public static AutoClickResult tryAutoClickIfComposerPrefilled(
            android.content.Context context
    ) {
        if (instance == null) {
            Log.w(TAG, "Accessibility service not connected — will retry");
            return AutoClickResult.RETRY;
        }
        if (!ComposerTapHelper.isAwaitingPrefillTap()) {
            return AutoClickResult.SUCCESS;
        }

        String composeText = instance.readComposeFieldText();
        if (!instance.isComposeFieldPrefilled(composeText)) {
            Log.d(TAG, "Compose field not ready — will retry");
            return AutoClickResult.RETRY;
        }

        RcsAvailability rcsAvailability = instance.detectRcsAvailability();
        if (rcsAvailability == RcsAvailability.UNKNOWN) {
            Log.d(TAG, "RCS availability unknown — will retry until session expiry");
            return AutoClickResult.RETRY;
        }
        if (rcsAvailability == RcsAvailability.SMS_ONLY) {
            Log.w(TAG, "Recipient is SMS-only — blocking RCS send");
            String smsId = ComposerTapHelper.getPendingSmsId();
            String smsBatchId = ComposerTapHelper.getPendingSmsBatchId();
            MessagingComposerHelper.reportRcsUnavailable(context, smsId, smsBatchId);
            ComposerTapHelper.clearAutoTapSession();
            TextBeeForegroundHelper.returnToTextBeeAfterTap(instance);
            Toast.makeText(
                    instance,
                    "RCS unavailable for this recipient",
                    Toast.LENGTH_LONG
            ).show();
            return AutoClickResult.SUCCESS;
        }

        Log.d(TAG, "Compose prefilled (\"" + composeText.length() + " chars) — auto-tapping Send");
        int x = ComposerTapHelper.getTapX(context);
        int y = ComposerTapHelper.getTapY(context);
        return instance.performClick(context, x, y, true);
    }

    /** Test tap — no prefill check, no return to TextBee. */
    public static void performClickAtSavedPositionForced(android.content.Context context) {
        if (instance == null) {
            Log.e(TAG, "Accessibility service not connected");
            return;
        }
        int x = ComposerTapHelper.getTapX(context);
        int y = ComposerTapHelper.getTapY(context);
        instance.performClick(context, x, y, false);
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

    private static volatile String lastSignalsSummary = "";

    /** Diagnostics from the most recent detection pass (for failure reports). */
    public static String getLastSignalsSummary() {
        return lastSignalsSummary;
    }

    /**
     * Decide RCS vs SMS from compose-area signals: send button description, compose
     * field hint/description, and hints/descriptions of nodes around the input bar.
     * Newer Google Messages (Jetpack Compose UI) exposes no view IDs, so we also
     * scan content descriptions and hint texts of all nodes, but never message text
     * itself — conversation content cannot fake a capability signal.
     */
    private RcsAvailability detectRcsAvailability() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            lastSignalsSummary = "(no window root)";
            return RcsAvailability.UNKNOWN;
        }
        try {
            StringBuilder signals = new StringBuilder();
            collectCapabilitySignals(root, signals, 0);
            String collected = signals.toString().trim();
            lastSignalsSummary = collected.length() > 400
                    ? collected.substring(0, 400)
                    : collected;

            RcsAvailability result = classifyRcsSignal(collected);
            Log.d(TAG, "RCS detection -> " + result + " from signals: " + lastSignalsSummary);
            return result;
        } finally {
            root.recycle();
        }
    }

    /**
     * Collect hint texts and content descriptions (never message text) from the
     * whole tree. These come from UI chrome (send button, compose placeholder,
     * toolbar) rather than conversation content.
     */
    private void collectCapabilitySignals(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 25) {
            return;
        }

        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0 && desc.length() < 120) {
            out.append(desc).append(" | ");
        }

        CharSequence hint = node.getHintText();
        if (hint != null && hint.length() > 0 && hint.length() < 120) {
            out.append(hint).append(" | ");
        }

        // For editable fields (compose box), text may be the hint when empty; skip
        // since we prefill it. For non-editable text nodes we skip text entirely to
        // avoid reading conversation messages.

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            collectCapabilitySignals(child, out, depth + 1);
            child.recycle();
        }
    }

    private RcsAvailability classifyRcsSignal(String rawSignal) {
        if (rawSignal == null || rawSignal.isEmpty()) {
            return RcsAvailability.UNKNOWN;
        }

        String lower = rawSignal.toLowerCase();

        // SMS markers win over RCS markers: an SMS-only conversation can still
        // contain the word "chat" elsewhere in UI chrome, but "Send SMS" /
        // "Text message" labels are authoritative.
        if (lower.contains("send sms") || lower.contains("text message")
                || lower.contains("sms message") || lower.contains("send mms")
                || lower.contains("mms message")) {
            return RcsAvailability.SMS_ONLY;
        }

        if (lower.contains("rcs") || lower.contains("chat message")
                || lower.contains("send chat") || lower.contains("end-to-end")) {
            return RcsAvailability.AVAILABLE;
        }

        return RcsAvailability.UNKNOWN;
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

    private AutoClickResult performClick(
            android.content.Context context,
            int x,
            int y,
            boolean returnToTextBeeAfter
    ) {
        final String smsId = ComposerTapHelper.getPendingSmsId();
        final String smsBatchId = ComposerTapHelper.getPendingSmsBatchId();

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
                MessagingComposerHelper.reportRcsSent(context, smsId, smsBatchId);
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
        return started ? AutoClickResult.SUCCESS : AutoClickResult.RETRY;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !ComposerTapHelper.isAwaitingPrefillTap()) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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
