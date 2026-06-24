package com.vernu.sms.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import com.vernu.sms.helpers.ComposerTapHelper;
import com.vernu.sms.helpers.MessagingComposerHelper;

/**
 * Trampoline so RCS composer can open from background (FCM / WorkManager).
 */
public class ComposerLaunchActivity extends Activity {

    private static final String TAG = "ComposerLaunchActivity";

    public static final String EXTRA_PHONE = "extra_phone";
    public static final String EXTRA_MESSAGE = "extra_message";
    public static final String EXTRA_SMS_ID = "extra_sms_id";
    public static final String EXTRA_SMS_BATCH_ID = "extra_sms_batch_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableShowWhenLocked();
        handleLaunch(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        enableShowWhenLocked();
        handleLaunch(intent);
    }

    private void enableShowWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }
    }

    private void handleLaunch(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }

        String phone = intent.getStringExtra(EXTRA_PHONE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String smsId = intent.getStringExtra(EXTRA_SMS_ID);
        String smsBatchId = intent.getStringExtra(EXTRA_SMS_BATCH_ID);

        boolean opened = MessagingComposerHelper.launchComposerFromForeground(
                this, phone, message, smsId, smsBatchId);
        if (opened) {
            ComposerTapHelper.armAutoClick(getApplicationContext(), message, smsId, smsBatchId);
            Log.d(TAG, "Composer launched for " + phone);
        } else {
            Log.e(TAG, "Composer launch failed for " + phone);
        }
        finish();
    }
}
