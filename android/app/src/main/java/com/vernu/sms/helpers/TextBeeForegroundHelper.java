package com.vernu.sms.helpers;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.vernu.sms.activities.MainActivity;
import com.vernu.sms.services.FloatingCursorService;

/**
 * Brings TextBee back to the foreground after RCS auto-tap (Messages goes to background).
 */
public final class TextBeeForegroundHelper {

    private static final String TAG = "TextBeeForegroundHelper";
    /** Brief pause so Messages can register the Send tap before we switch apps. */
    private static final long RETURN_DELAY_MS = 450L;

    private static final Handler handler = new Handler(Looper.getMainLooper());

    private TextBeeForegroundHelper() {
    }

    public static void returnToTextBeeAfterTap(Context context) {
        Context app = context.getApplicationContext();
        handler.postDelayed(() -> bringToFront(app), RETURN_DELAY_MS);
    }

    public static void bringToFront(Context context) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            );
            context.startActivity(intent);
            FloatingCursorService.show(context);
            Log.d(TAG, "Brought TextBee to foreground");
        } catch (Exception e) {
            Log.e(TAG, "Failed to bring TextBee to foreground", e);
        }
    }
}
