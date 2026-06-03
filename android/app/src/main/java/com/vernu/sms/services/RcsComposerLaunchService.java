package com.vernu.sms.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.vernu.sms.R;
import com.vernu.sms.activities.ComposerLaunchActivity;
import com.vernu.sms.helpers.ComposerTapHelper;
import com.vernu.sms.helpers.MessagingComposerHelper;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Launches messaging composer immediately for each RCS message (no queue).
 */
public class RcsComposerLaunchService extends Service {

    private static final String TAG = "RcsComposerLaunchService";
    private static final String CHANNEL_ID = "rcs_composer_launch";
    private static final int NOTIFICATION_ID = 9102;

    public static final String EXTRA_PHONE = ComposerLaunchActivity.EXTRA_PHONE;
    public static final String EXTRA_MESSAGE = ComposerLaunchActivity.EXTRA_MESSAGE;
    public static final String EXTRA_SMS_ID = ComposerLaunchActivity.EXTRA_SMS_ID;
    public static final String EXTRA_SMS_BATCH_ID = ComposerLaunchActivity.EXTRA_SMS_BATCH_ID;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private PowerManager.WakeLock wakeLock;

    /** Fire composer for this message immediately (parallel with other RCS sends). */
    public static void launchNow(
            Context context,
            String phone,
            String message,
            String smsId,
            String smsBatchId
    ) {
        Intent intent = new Intent(context.getApplicationContext(), RcsComposerLaunchService.class);
        intent.putExtra(EXTRA_PHONE, phone);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_SMS_ID, smsId);
        intent.putExtra(EXTRA_SMS_BATCH_ID, smsBatchId);

        Log.d(TAG, "Launch now smsId=" + smsId + " phone=" + phone);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getApplicationContext().startForegroundService(intent);
        } else {
            context.getApplicationContext().startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getStringExtra(EXTRA_PHONE) == null) {
            stopIfIdle();
            return START_NOT_STICKY;
        }

        String phone = intent.getStringExtra(EXTRA_PHONE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String smsId = intent.getStringExtra(EXTRA_SMS_ID);
        String smsBatchId = intent.getStringExtra(EXTRA_SMS_BATCH_ID);

        inFlight.incrementAndGet();
        acquireWakeLock();

        int requestCode = smsId != null ? smsId.hashCode() : startId;
        Intent launch = buildLaunchIntent(phone, message, smsId, smsBatchId);
        PendingIntent fullScreen = PendingIntent.getActivity(
                this,
                requestCode,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        startForeground(NOTIFICATION_ID, buildNotification(phone, fullScreen));

        mainHandler.post(() -> {
            boolean opened = MessagingComposerHelper.launchComposerFromForeground(
                    getApplicationContext(),
                    phone,
                    message,
                    smsId,
                    smsBatchId
            );
            if (!opened) {
                try {
                    startActivity(launch);
                    opened = true;
                } catch (Exception e) {
                    Log.e(TAG, "Composer launch failed for " + phone, e);
                }
            }
            if (opened) {
                ComposerTapHelper.armAutoClick(getApplicationContext(), message);
            }
            mainHandler.postDelayed(this::stopIfIdle, 400);
        });

        return START_NOT_STICKY;
    }

    private void stopIfIdle() {
        if (inFlight.decrementAndGet() > 0) {
            return;
        }
        releaseWakeLock();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private Intent buildLaunchIntent(String phone, String message, String smsId, String smsBatchId) {
        Intent launch = new Intent(this, ComposerLaunchActivity.class);
        launch.putExtra(EXTRA_PHONE, phone);
        launch.putExtra(EXTRA_MESSAGE, message);
        launch.putExtra(EXTRA_SMS_ID, smsId);
        launch.putExtra(EXTRA_SMS_BATCH_ID, smsBatchId);
        launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        );
        return launch;
    }

    private Notification buildNotification(String phone, PendingIntent fullScreen) {
        ensureChannel();
        String label = phone != null ? phone : "recipient";
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("RCS composer")
                .setContentText(label)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .setAutoCancel(true)
                .build();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "textbee:rcs_launch");
            wakeLock.acquire(60_000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "RCS composer",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Opens messaging composer for RCS sends");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
