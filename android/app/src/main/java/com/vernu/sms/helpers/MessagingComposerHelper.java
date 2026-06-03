package com.vernu.sms.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.util.Log;

import com.vernu.sms.AppConstants;
import com.vernu.sms.dtos.SMSDTO;
import com.vernu.sms.services.RcsComposerLaunchService;
import com.vernu.sms.workers.SMSStatusUpdateWorker;

import java.util.ArrayList;
import java.util.List;

public class MessagingComposerHelper {

    private static final String TAG = "MessagingComposerHelper";
    private static final String GOOGLE_MESSAGES = "com.google.android.apps.messaging";

    /**
     * Opens composer immediately for this recipient (no queue; parallel with other RCS sends).
     */
    public static boolean openComposer(
            Context context,
            String phoneNo,
            String message,
            String smsId,
            String smsBatchId
    ) {
        RcsComposerLaunchService.launchNow(
                context.getApplicationContext(),
                phoneNo,
                message,
                smsId,
                smsBatchId
        );
        return true;
    }

    public static boolean launchComposerFromForeground(
            Context context,
            String phoneNo,
            String message,
            String smsId,
            String smsBatchId
    ) {
        String normalized = normalizePhone(phoneNo);
        if (normalized.isEmpty()) {
            reportSendingError(context, smsId, smsBatchId, "Empty recipient");
            return false;
        }

        String finalMessage = message != null ? message : "";
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "textbee:rcs_composer"
                );
                wakeLock.acquire(10_000L);
            }

            boolean opened = tryLaunchComposerIntents(context, normalized, finalMessage);
            if (opened) {
                reportComposerOpened(context, smsId, smsBatchId);
                Log.d(TAG, "Composer opened for " + normalized);
            } else {
                reportSendingError(context, smsId, smsBatchId, "No messaging app handled smsto intent");
            }
            return opened;
        } catch (Exception e) {
            Log.e(TAG, "Composer open failed", e);
            reportSendingError(context, smsId, smsBatchId, e.getMessage());
            return false;
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private static boolean tryLaunchComposerIntents(Context context, String phone, String message) {
        List<Intent> candidates = buildComposerIntents(phone, message);
        for (Intent intent : candidates) {
            try {
                applyLaunchFlags(intent, context instanceof Activity);
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Composer intent failed: " + intent.getPackage(), e);
            }
        }
        return false;
    }

    private static void applyLaunchFlags(Intent intent, boolean fromActivity) {
        if (!fromActivity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        );
    }

    private static List<Intent> buildComposerIntents(String phone, String message) {
        List<Intent> list = new ArrayList<>();
        String encodedBody = Uri.encode(message != null ? message : "");
        Uri uriWithBody = Uri.parse("smsto:" + Uri.encode(phone) + "?body=" + encodedBody);
        Uri uriPlain = Uri.parse("smsto:" + Uri.encode(phone));

        Intent defaultIntent = new Intent(Intent.ACTION_SENDTO, uriWithBody);
        defaultIntent.setData(uriWithBody);
        putMessageExtras(defaultIntent, message);
        list.add(defaultIntent);

        Intent defaultPlain = new Intent(Intent.ACTION_SENDTO, uriPlain);
        defaultPlain.setData(uriPlain);
        putMessageExtras(defaultPlain, message);
        list.add(defaultPlain);

        Intent googleIntent = new Intent(Intent.ACTION_SENDTO, uriWithBody);
        googleIntent.setData(uriWithBody);
        googleIntent.setPackage(GOOGLE_MESSAGES);
        putMessageExtras(googleIntent, message);
        list.add(googleIntent);

        Intent viewIntent = new Intent(Intent.ACTION_VIEW, uriWithBody);
        viewIntent.setData(uriWithBody);
        putMessageExtras(viewIntent, message);
        list.add(viewIntent);

        return list;
    }

    private static void putMessageExtras(Intent intent, String message) {
        String body = message != null ? message : "";
        intent.putExtra("sms_body", body);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        intent.putExtra("android.intent.extra.TEXT", body);
    }

    private static String normalizePhone(String phoneNo) {
        if (phoneNo == null) {
            return "";
        }
        String trimmed = phoneNo.trim().replaceAll("\\s+", "");
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replaceAll("[^0-9+]", "");
    }

    private static void reportComposerOpened(Context context, String smsId, String smsBatchId) {
        if (smsId == null || smsId.trim().isEmpty()) {
            return;
        }

        SMSDTO smsDTO = new SMSDTO();
        smsDTO.setSmsId(smsId);
        smsDTO.setSmsBatchId(smsBatchId);
        smsDTO.setStatus("SENT");
        smsDTO.setSentAtInMillis(System.currentTimeMillis());

        updateSMSStatus(context, smsDTO);
    }

    private static void reportSendingError(
            Context context,
            String smsId,
            String smsBatchId,
            String errorMessage
    ) {
        if (smsId == null || smsId.trim().isEmpty()) {
            return;
        }

        SMSDTO smsDTO = new SMSDTO();
        smsDTO.setSmsId(smsId);
        smsDTO.setSmsBatchId(smsBatchId);
        smsDTO.setStatus("FAILED");
        smsDTO.setFailedAtInMillis(System.currentTimeMillis());
        smsDTO.setErrorCode("COMPOSER_EXCEPTION");
        smsDTO.setErrorMessage(errorMessage != null ? errorMessage : "Unknown composer error");

        updateSMSStatus(context, smsDTO);
    }

    private static void updateSMSStatus(Context context, SMSDTO smsDTO) {
        String deviceId = SharedPreferenceHelper.getSharedPreferenceString(
                context,
                AppConstants.SHARED_PREFS_DEVICE_ID_KEY,
                ""
        );

        String apiKey = SharedPreferenceHelper.getSharedPreferenceString(
                context,
                AppConstants.SHARED_PREFS_API_KEY_KEY,
                ""
        );

        if (deviceId.isEmpty() || apiKey.isEmpty()) {
            Log.e(TAG, "Device ID or API key not found");
            return;
        }

        SMSStatusUpdateWorker.enqueueWork(context, deviceId, apiKey, smsDTO);
    }
}
