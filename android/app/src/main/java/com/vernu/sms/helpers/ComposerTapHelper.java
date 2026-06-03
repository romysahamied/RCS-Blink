package com.vernu.sms.helpers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.vernu.sms.services.RCSAccessibilityService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Auto-tap only when TextBee opened the composer with a non-empty prefilled message.
 */
public final class ComposerTapHelper {

    private static final String TAG = "ComposerTapHelper";

    public static final String PREF_TAP_X = "rcs_tap_x";
    public static final String PREF_TAP_Y = "rcs_tap_y";
    public static final String PREF_AUTO_CLICK_ENABLED = "rcs_auto_click_enabled";

    private static final int DEFAULT_TAP_X = 950;
    private static final int DEFAULT_TAP_Y = 2050;
    private static final long COMPOSER_CLICK_DELAY_MS = 2600L;
    private static final long ACCESSIBILITY_CLICK_DELAY_MS = 750L;
    private static final long AUTO_TAP_SESSION_EXPIRY_MS = 10000L;

    private static final Set<String> MESSAGING_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.microsoft.android.smsorganizer"
    ));

    private static volatile boolean armed;
    private static volatile boolean awaitingPrefillTap;
    private static volatile String expectedComposeText;
    private static Runnable scheduledTapRunnable;
    private static Runnable expiryRunnable;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static PositionSavedListener positionSavedListener;

    public interface PositionSavedListener {
        void onPositionSaved(int x, int y);
    }

    private ComposerTapHelper() {
    }

    public static void setPositionSavedListener(PositionSavedListener listener) {
        positionSavedListener = listener;
    }

    public static boolean isMessagingPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (MESSAGING_PACKAGES.contains(packageName)) {
            return true;
        }
        String lower = packageName.toLowerCase();
        return lower.contains("messaging") || lower.contains(".mms");
    }

    public static void saveTapPosition(Context context, int screenX, int screenY) {
        SharedPreferenceHelper.setSharedPreferenceInt(context, PREF_TAP_X, screenX);
        SharedPreferenceHelper.setSharedPreferenceInt(context, PREF_TAP_Y, screenY);
        Log.d(TAG, "Saved tap position: " + screenX + ", " + screenY);
        if (positionSavedListener != null) {
            positionSavedListener.onPositionSaved(screenX, screenY);
        }
    }

    public static int getTapX(Context context) {
        int x = SharedPreferenceHelper.getSharedPreferenceInt(context, PREF_TAP_X, -1);
        if (x < 0) {
            x = SharedPreferenceHelper.getSharedPreferenceInt(context, "rcs_cursor_x", DEFAULT_TAP_X);
        }
        return x;
    }

    public static int getTapY(Context context) {
        int y = SharedPreferenceHelper.getSharedPreferenceInt(context, PREF_TAP_Y, -1);
        if (y < 0) {
            y = SharedPreferenceHelper.getSharedPreferenceInt(context, "rcs_cursor_y", DEFAULT_TAP_Y);
        }
        return y;
    }

    public static boolean isAutoClickEnabled(Context context) {
        return SharedPreferenceHelper.getSharedPreferenceBoolean(context, PREF_AUTO_CLICK_ENABLED, false);
    }

    public static void setAutoClickEnabled(Context context, boolean enabled) {
        SharedPreferenceHelper.setSharedPreferenceBoolean(context, PREF_AUTO_CLICK_ENABLED, enabled);
    }

    public static boolean isArmed() {
        return armed && awaitingPrefillTap;
    }

    public static boolean isAwaitingPrefillTap() {
        return awaitingPrefillTap;
    }

    public static String getExpectedComposeText() {
        return expectedComposeText;
    }

    /**
     * Arm auto-tap only when TextBee launched composer with a message body from the web.
     */
    public static void armAutoClick(Context context, String messageBody) {
        if (!isAutoClickEnabled(context)) {
            Log.d(TAG, "Auto-click disabled; not arming");
            return;
        }
        if (messageBody == null || messageBody.trim().isEmpty()) {
            Log.d(TAG, "Empty message body — not arming auto-click");
            return;
        }

        cancelScheduledTap();
        awaitingPrefillTap = true;
        expectedComposeText = messageBody.trim();
        armed = true;

        Context appContext = context.getApplicationContext();
        scheduledTapRunnable = () -> {
            if (!awaitingPrefillTap || !isAutoClickEnabled(appContext)) {
                return;
            }
            Log.d(TAG, "Auto-click check (timer)");
            RCSAccessibilityService.tryAutoClickIfComposerPrefilled(appContext);
        };
        handler.postDelayed(scheduledTapRunnable, COMPOSER_CLICK_DELAY_MS);

        expiryRunnable = () -> {
            Log.d(TAG, "Auto-tap session expired");
            clearAutoTapSession();
        };
        handler.postDelayed(expiryRunnable, AUTO_TAP_SESSION_EXPIRY_MS);

        Log.d(TAG, "Armed auto-tap for prefilled message");
    }

    public static void armAutoClick(Context context) {
        armAutoClick(context, null);
    }

    public static void onMessagingWindowOpened(Context context) {
        if (!awaitingPrefillTap || !isAutoClickEnabled(context)) {
            return;
        }
        Context appContext = context.getApplicationContext();
        handler.postDelayed(() -> {
            if (!awaitingPrefillTap || !isAutoClickEnabled(appContext)) {
                return;
            }
            Log.d(TAG, "Auto-click check (accessibility)");
            RCSAccessibilityService.tryAutoClickIfComposerPrefilled(appContext);
        }, ACCESSIBILITY_CLICK_DELAY_MS);
    }

    public static void clearAutoTapSession() {
        awaitingPrefillTap = false;
        expectedComposeText = null;
        armed = false;
        cancelScheduledTap();
        if (expiryRunnable != null) {
            handler.removeCallbacks(expiryRunnable);
            expiryRunnable = null;
        }
    }

    private static void cancelScheduledTap() {
        if (scheduledTapRunnable != null) {
            handler.removeCallbacks(scheduledTapRunnable);
            scheduledTapRunnable = null;
        }
    }

    public static void disarm() {
        clearAutoTapSession();
    }

    /** Test button — always taps, does not return to TextBee. */
    public static void performTestClick(Context context) {
        RCSAccessibilityService.performClickAtSavedPositionForced(context);
    }
}
