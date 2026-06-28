package com.vernu.sms.services;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.vernu.sms.R;
import com.vernu.sms.activities.MainActivity;
import com.vernu.sms.receivers.SMSBroadcastReceiver;
import com.vernu.sms.AppConstants;
import com.vernu.sms.helpers.OutboundSmsPullHelper;
import com.vernu.sms.helpers.SharedPreferenceHelper;

public class StickyNotificationService extends Service {

    private static final String TAG = "StickyNotificationService";
    // Lower polling interval to reduce fallback transport latency in local/dev setups.
    private static final long OUTBOUND_PULL_INTERVAL_MS = 2000L;
    private final Handler pullHandler = new Handler(Looper.getMainLooper());
    private final Runnable outboundPullRunnable = new Runnable() {
        @Override
        public void run() {
            tryPullOutboundSms();
            pullHandler.postDelayed(this, OUTBOUND_PULL_INTERVAL_MS);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Service onBind " + intent.getAction());
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service Started");

        boolean gatewayEnabled = SharedPreferenceHelper.getSharedPreferenceBoolean(
                getApplicationContext(),
                AppConstants.SHARED_PREFS_GATEWAY_ENABLED_KEY,
                false
        );
        if (!gatewayEnabled) {
            Log.i(TAG, "Gateway disabled, stopping service");
            stopSelf();
            return;
        }

        boolean stickyNotificationEnabled = SharedPreferenceHelper.getSharedPreferenceBoolean(
                getApplicationContext(),
                AppConstants.SHARED_PREFS_STICKY_NOTIFICATION_ENABLED_KEY,
                false
        );

        Notification notification = createNotification(stickyNotificationEnabled);
        try {
            startForeground(1, notification);
            Log.i(TAG, stickyNotificationEnabled
                    ? "Started foreground service with sticky notification"
                    : "Started foreground service with silent gateway notification");
        } catch (ForegroundServiceStartNotAllowedException e) {
            Log.w(TAG, "Cannot start foreground from background, stopping service: " + e.getMessage());
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        pullHandler.removeCallbacks(outboundPullRunnable);
        pullHandler.post(outboundPullRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pullHandler.removeCallbacks(outboundPullRunnable);
        
        Log.i(TAG, "StickyNotificationService destroyed");
    }

    private void tryPullOutboundSms() {
        boolean gatewayEnabled = SharedPreferenceHelper.getSharedPreferenceBoolean(
                getApplicationContext(),
                AppConstants.SHARED_PREFS_GATEWAY_ENABLED_KEY,
                false
        );
        if (!gatewayEnabled) {
            return;
        }

        String deviceId = SharedPreferenceHelper.getSharedPreferenceString(
                getApplicationContext(),
                AppConstants.SHARED_PREFS_DEVICE_ID_KEY,
                ""
        );
        String apiKey = SharedPreferenceHelper.getSharedPreferenceString(
                getApplicationContext(),
                AppConstants.SHARED_PREFS_API_KEY_KEY,
                ""
        );
        OutboundSmsPullHelper.pullAndEnqueue(getApplicationContext(), deviceId, apiKey);
    }

    private Notification createNotification(boolean stickyNotificationEnabled) {
        String notificationChannelId = stickyNotificationEnabled
                ? "stickyNotificationChannel"
                : "gatewayServiceChannel";

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            int importance = stickyNotificationEnabled
                    ? NotificationManager.IMPORTANCE_HIGH
                    : NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(
                    notificationChannelId,
                    stickyNotificationEnabled ? "RCS Blink Active" : "RCS Blink Gateway",
                    importance
            );
            channel.enableVibration(false);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);

            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            String title = stickyNotificationEnabled ? "RCS Blink Active" : "RCS Blink Gateway";
            String text = stickyNotificationEnabled
                    ? "SMS gateway service is active"
                    : "Listening for outbound messages";

            return new Notification.Builder(this, notificationChannelId)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_notification)
                    .build();
        }

        int priority = stickyNotificationEnabled
                ? NotificationCompat.PRIORITY_HIGH
                : NotificationCompat.PRIORITY_LOW;
        return new NotificationCompat.Builder(this, notificationChannelId)
                .setContentTitle(stickyNotificationEnabled ? "RCS Blink Active" : "RCS Blink Gateway")
                .setContentText(stickyNotificationEnabled
                        ? "SMS gateway service is active"
                        : "Listening for outbound messages")
                .setOngoing(true)
                .setPriority(priority)
                .setSmallIcon(R.drawable.ic_notification)
                .build();
    }
}