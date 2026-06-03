package com.vernu.sms.helpers;

import android.content.Context;
import android.util.Log;

import com.vernu.sms.AppConstants;
import com.vernu.sms.ApiManager;
import com.vernu.sms.dtos.OutboundSmsPayloadDTO;
import com.vernu.sms.dtos.PullPendingSmsResponseDTO;
import com.vernu.sms.workers.SmsSendWorker;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OutboundSmsPullHelper {
    private static final String TAG = "OutboundSmsPullHelper";
    private static final int DEFAULT_PULL_LIMIT = 25;

    public static void pullAndEnqueue(Context context, String deviceId, String apiKey) {
        SharedPreferenceHelper.setSharedPreferenceString(
                context,
                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_ERROR_KEY,
                ""
        );

        String resolvedDeviceId = deviceId;
        if (resolvedDeviceId == null || resolvedDeviceId.trim().isEmpty()) {
            resolvedDeviceId = SharedPreferenceHelper.getSharedPreferenceString(
                    context,
                    AppConstants.SHARED_PREFS_DEVICE_ID_KEY,
                    ""
            );
        }

        String resolvedApiKey = apiKey;
        if (resolvedApiKey == null || resolvedApiKey.trim().isEmpty()) {
            resolvedApiKey = SharedPreferenceHelper.getSharedPreferenceString(
                    context,
                    AppConstants.SHARED_PREFS_API_KEY_KEY,
                    ""
            );
        }

        if (resolvedDeviceId == null || resolvedDeviceId.trim().isEmpty() || resolvedApiKey == null || resolvedApiKey.trim().isEmpty()) {
            SharedPreferenceHelper.setSharedPreferenceString(
                    context,
                    AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_ERROR_KEY,
                    "Missing " +
                            ((resolvedDeviceId == null || resolvedDeviceId.trim().isEmpty()) ? "deviceId" : "") +
                            (((resolvedDeviceId == null || resolvedDeviceId.trim().isEmpty()) &&
                                    (resolvedApiKey == null || resolvedApiKey.trim().isEmpty())) ? "/" : "") +
                            ((resolvedApiKey == null || resolvedApiKey.trim().isEmpty()) ? "apiKey" : "")
            );
            return;
        }

        ApiManager.getApiService()
                .pullPendingSms(resolvedDeviceId, resolvedApiKey, DEFAULT_PULL_LIMIT)
                .enqueue(new Callback<PullPendingSmsResponseDTO>() {
                    @Override
                    public void onResponse(Call<PullPendingSmsResponseDTO> call, Response<PullPendingSmsResponseDTO> response) {
                        if (!response.isSuccessful() || response.body() == null || response.body().data == null) {
                            Log.d(TAG, "No pending outbound SMS pulled. HTTP code: " + response.code());
                            SharedPreferenceHelper.setSharedPreferenceLong(
                                    context,
                                    AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_AT_MS_KEY,
                                    System.currentTimeMillis()
                            );
                            SharedPreferenceHelper.setSharedPreferenceInt(
                                    context,
                                    AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_COUNT_KEY,
                                    0
                            );
                            SharedPreferenceHelper.setSharedPreferenceString(
                                    context,
                                    AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_ERROR_KEY,
                                    "HTTP " + response.code()
                            );
                            return;
                        }

                        List<OutboundSmsPayloadDTO> pending = response.body().data;
                        for (OutboundSmsPayloadDTO sms : pending) {
                            if (sms == null || sms.recipients == null || sms.recipients.length == 0 || sms.message == null) {
                                continue;
                            }
                            for (String recipient : sms.recipients) {
                                if (recipient == null || recipient.trim().isEmpty()) {
                                    continue;
                                }
                                SmsSendWorker.enqueue(
                                        context,
                                        recipient.trim(),
                                        sms.message,
                                        sms.smsId,
                                        sms.smsBatchId,
                                        sms.simSubscriptionId,
                                        sms.channel
                                );
                            }
                        }
                        if (!pending.isEmpty()) {
                            Log.d(TAG, "Pulled and enqueued outbound SMS count: " + pending.size());
                        }
                        SharedPreferenceHelper.setSharedPreferenceLong(
                                context,
                                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_AT_MS_KEY,
                                System.currentTimeMillis()
                        );
                        SharedPreferenceHelper.setSharedPreferenceInt(
                                context,
                                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_COUNT_KEY,
                                pending.size()
                        );
                        SharedPreferenceHelper.setSharedPreferenceString(
                                context,
                                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_ERROR_KEY,
                                ""
                        );
                    }

                    @Override
                    public void onFailure(Call<PullPendingSmsResponseDTO> call, Throwable t) {
                        Log.d(TAG, "Failed to pull pending outbound SMS: " + t.getMessage());
                        SharedPreferenceHelper.setSharedPreferenceLong(
                                context,
                                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_AT_MS_KEY,
                                System.currentTimeMillis()
                        );
                        SharedPreferenceHelper.setSharedPreferenceInt(
                                context,
                                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_COUNT_KEY,
                                0
                        );
                        SharedPreferenceHelper.setSharedPreferenceString(
                                context,
                                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_ERROR_KEY,
                                t.getMessage() == null ? "Network failure" : t.getMessage()
                        );
                    }
                });
    }
}
