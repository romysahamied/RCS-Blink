package com.vernu.sms.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.vernu.sms.AppConstants;
import com.vernu.sms.TextBeeUtils;
import com.vernu.sms.helpers.MessagingComposerHelper;
import com.vernu.sms.helpers.SMSHelper;
import com.vernu.sms.helpers.SharedPreferenceHelper;

public class SmsSendWorker extends Worker {
    private static final String TAG = "SmsSendWorker";
    private static final String QUEUE_NAME = "sms_send_queue";
    private static final String CHANNEL_RCS = "rcs";

    public static final String KEY_PHONE = "phone";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_SMS_ID = "sms_id";
    public static final String KEY_SMS_BATCH_ID = "sms_batch_id";
    public static final String KEY_SIM_SUBSCRIPTION_ID = "sim_subscription_id";
    public static final String KEY_CHANNEL = "channel";

    public SmsSendWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String phone = getInputData().getString(KEY_PHONE);
        String message = getInputData().getString(KEY_MESSAGE);
        String smsId = getInputData().getString(KEY_SMS_ID);
        String smsBatchId = getInputData().getString(KEY_SMS_BATCH_ID);
        int simSubscriptionId = getInputData().getInt(KEY_SIM_SUBSCRIPTION_ID, -1);

        if (phone == null || message == null || smsId == null) {
            Log.e(TAG, "Missing required parameters");
            return Result.failure();
        }

        Context context = getApplicationContext();

        String channel = getInputData().getString(KEY_CHANNEL);
        boolean useMessagingComposer =
                channel != null && CHANNEL_RCS.equalsIgnoreCase(channel.trim());

        if (useMessagingComposer) {
            // RCS P2P flow: hand off to the default messaging app composer.
            // The user's messaging app/carrier decides RCS vs SMS on send.
            boolean queued = MessagingComposerHelper.openComposer(
                    context,
                    phone,
                    message,
                    smsId,
                    smsBatchId
            );
            if (!queued) {
                Log.e(TAG, "Failed to queue composer for RCS channel. smsId=" + smsId);
            }
        } else {
            // Resolve SIM: backend-provided > app preference > device default
            Integer resolvedSim = resolveSim(context, simSubscriptionId);

            if (resolvedSim != null) {
                SMSHelper.sendSMSFromSpecificSim(phone, message, resolvedSim, smsId, smsBatchId, context);
            } else {
                SMSHelper.sendSMS(phone, message, smsId, smsBatchId, context);
            }
        }

        return Result.success();
    }

    private Integer resolveSim(Context context, int backendSimId) {
        // Priority 1: backend-provided SIM
        if (backendSimId != -1 && TextBeeUtils.isValidSubscriptionId(context, backendSimId)) {
            Log.d(TAG, "Using backend-provided SIM subscription ID: " + backendSimId);
            return backendSimId;
        }

        // Priority 2: app preference
        int preferredSim = SharedPreferenceHelper.getSharedPreferenceInt(
                context, AppConstants.SHARED_PREFS_PREFERRED_SIM_KEY, -1);
        if (preferredSim != -1 && TextBeeUtils.isValidSubscriptionId(context, preferredSim)) {
            Log.d(TAG, "Using app-preferred SIM subscription ID: " + preferredSim);
            return preferredSim;
        }

        // Priority 3: device default
        return null;
    }

    public static void enqueue(Context context, String phone, String message,
                               String smsId, String smsBatchId, Integer simSubscriptionId) {
        enqueue(context, phone, message, smsId, smsBatchId, simSubscriptionId, null);
    }

    public static void enqueue(Context context, String phone, String message,
                               String smsId, String smsBatchId, Integer simSubscriptionId, String channel) {
        Data.Builder data = new Data.Builder()
                .putString(KEY_PHONE, phone)
                .putString(KEY_MESSAGE, message)
                .putString(KEY_SMS_ID, smsId)
                .putString(KEY_SMS_BATCH_ID, smsBatchId)
                .putInt(KEY_SIM_SUBSCRIPTION_ID, simSubscriptionId != null ? simSubscriptionId : -1);
        if (channel != null && !channel.trim().isEmpty()) {
            data.putString(KEY_CHANNEL, channel.trim());
        }
        Data inputData = data.build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SmsSendWorker.class)
                .setInputData(inputData)
                .build();

        boolean isRcs = channel != null && CHANNEL_RCS.equalsIgnoreCase(channel.trim());

        if (isRcs) {
            // Each RCS message runs in parallel — opens composer for its recipient immediately.
            WorkManager.getInstance(context).enqueue(workRequest);
        } else {
            WorkManager.getInstance(context)
                    .beginUniqueWork(QUEUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
                    .enqueue();
        }

        Log.d(TAG, "SMS enqueued for sending - ID: " + smsId + ", Phone: " + phone);
    }
}
