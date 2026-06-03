package com.vernu.sms.activities;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import com.vernu.sms.activities.SMSFilterActivity;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.vernu.sms.ApiManager;
import com.vernu.sms.AppConstants;
import com.vernu.sms.BuildConfig;
import com.vernu.sms.TextBeeUtils;
import com.vernu.sms.R;
import com.vernu.sms.dtos.RegisterDeviceInputDTO;
import com.vernu.sms.dtos.RegisterDeviceResponseDTO;
import com.vernu.sms.dtos.SimInfoCollectionDTO;
import com.vernu.sms.helpers.ComposerTapHelper;
import com.vernu.sms.helpers.SharedPreferenceHelper;
import com.vernu.sms.services.RCSAccessibilityService;
import com.vernu.sms.helpers.VersionTracker;
import com.vernu.sms.helpers.HeartbeatManager;
import com.vernu.sms.helpers.OutboundSmsPullHelper;
import com.vernu.sms.services.FloatingCursorService;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.gson.Gson;
import okhttp3.ResponseBody;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private Context mContext;
    private Switch gatewaySwitch, receiveSMSSwitch, stickyNotificationSwitch, rcsAutoClickSwitch;
    private EditText apiKeyEditText, fcmTokenEditText, deviceIdEditText, deviceNameEditText, smsSendDelayEditText;
    private Button registerDeviceBtn, grantSMSPermissionBtn, scanQRBtn, checkUpdatesBtn, configureFilterBtn;
    private Button rcsOpenAccessibilityBtn, rcsTestTapBtn, rcsOverlayPermissionBtn;
    private ImageButton copyDeviceIdImgBtn;
    private TextView deviceBrandAndModelTxt, deviceIdTxt, appVersionNameTxt, appVersionCodeTxt, apiBaseUrlTxt, outboundPullDebugTxt, rcsTapPositionTxt;
    private RadioGroup defaultSimSlotRadioGroup;
    private static final int SCAN_QR_REQUEST_CODE = 49374;
    private static final int PERMISSION_REQUEST_CODE = 0;
    private static final long SMS_DELAY_SAVE_DEBOUNCE_MS = 3000L;
    private final Handler smsDelaySaveHandler = new Handler(Looper.getMainLooper());
    private final Handler pullDebugHandler = new Handler(Looper.getMainLooper());
    private final Runnable pullDebugRunnable = new Runnable() {
        @Override
        public void run() {
            refreshOutboundPullDebugText();
            pullDebugHandler.postDelayed(this, 2000L);
        }
    };
    private Runnable smsDelaySaveRunnable;
    private String deviceId = null;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        deviceId = SharedPreferenceHelper.getSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, "");
        setContentView(R.layout.activity_main);
        gatewaySwitch = findViewById(R.id.gatewaySwitch);
        receiveSMSSwitch = findViewById(R.id.receiveSMSSwitch);
        stickyNotificationSwitch = findViewById(R.id.stickyNotificationSwitch);
        apiKeyEditText = findViewById(R.id.apiKeyEditText);
        fcmTokenEditText = findViewById(R.id.fcmTokenEditText);
        deviceIdEditText = findViewById(R.id.deviceIdEditText);
        deviceNameEditText = findViewById(R.id.deviceNameEditText);
        registerDeviceBtn = findViewById(R.id.registerDeviceBtn);
        grantSMSPermissionBtn = findViewById(R.id.grantSMSPermissionBtn);
        scanQRBtn = findViewById(R.id.scanQRButton);
        deviceBrandAndModelTxt = findViewById(R.id.deviceBrandAndModelTxt);
        deviceIdTxt = findViewById(R.id.deviceIdTxt);
        copyDeviceIdImgBtn = findViewById(R.id.copyDeviceIdImgBtn);
        defaultSimSlotRadioGroup = findViewById(R.id.defaultSimSlotRadioGroup);
        appVersionNameTxt = findViewById(R.id.appVersionNameTxt);
        appVersionCodeTxt = findViewById(R.id.appVersionCodeTxt);
        apiBaseUrlTxt = findViewById(R.id.apiBaseUrlTxt);
        outboundPullDebugTxt = findViewById(R.id.outboundPullDebugTxt);
        checkUpdatesBtn = findViewById(R.id.checkUpdatesBtn);
        configureFilterBtn = findViewById(R.id.configureFilterBtn);
        smsSendDelayEditText = findViewById(R.id.smsSendDelayEditText);
        rcsAutoClickSwitch = findViewById(R.id.rcsAutoClickSwitch);
        rcsTapPositionTxt = findViewById(R.id.rcsTapPositionTxt);
        rcsOpenAccessibilityBtn = findViewById(R.id.rcsOpenAccessibilityBtn);
        rcsTestTapBtn = findViewById(R.id.rcsTestTapBtn);
        rcsOverlayPermissionBtn = findViewById(R.id.rcsOverlayPermissionBtn);
        setupRcsTapTargetUi();

        deviceIdTxt.setText(deviceId);
        deviceIdEditText.setText(deviceId);
        deviceBrandAndModelTxt.setText(Build.BRAND + " " + Build.MODEL);
        
        // Set app version information
        String versionName = BuildConfig.VERSION_NAME;
        appVersionNameTxt.setText(versionName);
        appVersionCodeTxt.setText(String.valueOf(BuildConfig.VERSION_CODE));
        apiBaseUrlTxt.setText("API Base URL: " + BuildConfig.API_BASE_URL);
        refreshOutboundPullDebugText();
        
        // Check for app version changes and report if needed
        if (VersionTracker.hasVersionChanged(mContext)) {
            Log.d(TAG, "App version changed or first launch, reporting to server");
            VersionTracker.reportVersionToServer(mContext);
        }
        
        // Keep app boot resilient in local/dev builds where Firebase config can be absent/placeholder.
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCustomKey("device_id", deviceId != null ? deviceId : "not_registered");
            crashlytics.setCustomKey("device_model", Build.MODEL);
            crashlytics.setCustomKey("app_version", versionName);
            crashlytics.setCustomKey("app_version_code", BuildConfig.VERSION_CODE);
        } catch (Exception e) {
            Log.w(TAG, "Crashlytics unavailable; continuing without it", e);
        }

        // Start sticky notification service if enabled
        boolean gatewayEnabled = SharedPreferenceHelper.getSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_GATEWAY_ENABLED_KEY, false);
        boolean stickyNotificationEnabled = SharedPreferenceHelper.getSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_STICKY_NOTIFICATION_ENABLED_KEY, false);
        if (gatewayEnabled && stickyNotificationEnabled) {
            TextBeeUtils.startStickyNotificationService(mContext);
            Log.d(TAG, "Starting sticky notification service on app start");
        }

        // Schedule heartbeat if device is enabled and registered
        if (gatewayEnabled && deviceId != null && !deviceId.isEmpty()) {
            HeartbeatManager.scheduleHeartbeat(mContext);
            Log.d(TAG, "Scheduling heartbeat on app start");
            String startupApiKey = SharedPreferenceHelper.getSharedPreferenceString(
                    mContext, AppConstants.SHARED_PREFS_API_KEY_KEY, "");
            OutboundSmsPullHelper.pullAndEnqueue(mContext, deviceId, startupApiKey);
        }

        if (deviceId == null || deviceId.isEmpty()) {
            registerDeviceBtn.setText("Register");
        } else {
            registerDeviceBtn.setText("Update");
        }

        String[] missingPermissions = Arrays.stream(AppConstants.requiredPermissions).filter(permission -> !TextBeeUtils.isPermissionGranted(mContext, permission)).toArray(String[]::new);
        if (missingPermissions.length == 0) {
            grantSMSPermissionBtn.setEnabled(false);
            grantSMSPermissionBtn.setText("Permission Granted");
            renderAvailableSimOptions();
        } else {
            Snackbar.make(grantSMSPermissionBtn, "Please Grant Required Permissions to continue: " + Arrays.toString(missingPermissions), Snackbar.LENGTH_SHORT).show();
            grantSMSPermissionBtn.setEnabled(true);
            grantSMSPermissionBtn.setOnClickListener(this::handleRequestPermissions);
        }

//        TextBeeUtils.startStickyNotificationService(mContext);

        copyDeviceIdImgBtn.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Device ID", deviceId);
            clipboard.setPrimaryClip(clip);
            Snackbar.make(view, "Copied", Snackbar.LENGTH_LONG).show();
        });

        apiKeyEditText.setText(SharedPreferenceHelper.getSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_API_KEY_KEY, ""));
        String storedDeviceName = SharedPreferenceHelper.getSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_NAME_KEY, "");
        if (storedDeviceName.isEmpty()) {
            deviceNameEditText.setText(Build.BRAND + " " + Build.MODEL);
        } else {
            deviceNameEditText.setText(storedDeviceName);
        }
        gatewaySwitch.setChecked(SharedPreferenceHelper.getSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_GATEWAY_ENABLED_KEY, false));
        gatewaySwitch.setOnCheckedChangeListener((compoundButton, isCheked) -> {
            View view = compoundButton.getRootView();
            compoundButton.setEnabled(false);
            String key = apiKeyEditText.getText().toString();

            RegisterDeviceInputDTO registerDeviceInput = new RegisterDeviceInputDTO();
            registerDeviceInput.setEnabled(isCheked);
            registerDeviceInput.setAppVersionCode(BuildConfig.VERSION_CODE);
            registerDeviceInput.setAppVersionName(BuildConfig.VERSION_NAME);

            Call<RegisterDeviceResponseDTO> apiCall = ApiManager.getApiService().updateDevice(deviceId, key, registerDeviceInput);
            apiCall.enqueue(new Callback<RegisterDeviceResponseDTO>() {
                @Override
                public void onResponse(Call<RegisterDeviceResponseDTO> call, Response<RegisterDeviceResponseDTO> response) {
                    Log.d(TAG, response.toString());
                    if (!response.isSuccessful()) {
                        Snackbar.make(view, extractErrorMessage(response), Snackbar.LENGTH_LONG).show();
                        compoundButton.setEnabled(true);
                        return;
                    }
                    Snackbar.make(view, "Gateway " + (isCheked ? "enabled" : "disabled"), Snackbar.LENGTH_LONG).show();
                    SharedPreferenceHelper.setSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_GATEWAY_ENABLED_KEY, isCheked);
                    boolean enabled = Boolean.TRUE.equals(Objects.requireNonNull(response.body()).data.get("enabled"));
                    compoundButton.setChecked(enabled);
                    if (enabled) {
                        // Check if sticky notification is enabled
                        if (SharedPreferenceHelper.getSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_STICKY_NOTIFICATION_ENABLED_KEY, false)) {
                            TextBeeUtils.startStickyNotificationService(mContext);
                        }
                        // Schedule heartbeat
                        HeartbeatManager.scheduleHeartbeat(mContext);
                    } else {
                        TextBeeUtils.stopStickyNotificationService(mContext);
                        // Cancel heartbeat
                        HeartbeatManager.cancelHeartbeat(mContext);
                    }
                    compoundButton.setEnabled(true);
                }
                @Override
                public void onFailure(Call<RegisterDeviceResponseDTO> call, Throwable t) {
                    Snackbar.make(view, "An error occurred :(", Snackbar.LENGTH_LONG).show();
                    Log.e(TAG, "API_ERROR "+ t.getMessage());
                    Log.e(TAG, "API_ERROR "+ t.getLocalizedMessage());
                    TextBeeUtils.logException(t, "Error updating device");
                    compoundButton.setEnabled(true);
                }
            });
        });

        receiveSMSSwitch.setChecked(SharedPreferenceHelper.getSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_RECEIVE_SMS_ENABLED_KEY, false));
        receiveSMSSwitch.setOnCheckedChangeListener((compoundButton, isCheked) -> {
            View view = compoundButton.getRootView();
            SharedPreferenceHelper.setSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_RECEIVE_SMS_ENABLED_KEY, isCheked);
            compoundButton.setChecked(isCheked);
            Snackbar.make(view, "Receive SMS " + (isCheked ? "enabled" : "disabled"), Snackbar.LENGTH_LONG).show();
        });

        // Setup sticky notification switch
        stickyNotificationSwitch.setChecked(SharedPreferenceHelper.getSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_STICKY_NOTIFICATION_ENABLED_KEY, false));
        stickyNotificationSwitch.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            View view = compoundButton.getRootView();
            SharedPreferenceHelper.setSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_STICKY_NOTIFICATION_ENABLED_KEY, isChecked);
            
            if (isChecked) {
                TextBeeUtils.startStickyNotificationService(mContext);
                Snackbar.make(view, "Background service enabled - app will be more reliable", Snackbar.LENGTH_LONG).show();
            } else {
                TextBeeUtils.stopStickyNotificationService(mContext);
                Snackbar.make(view, "Background service disabled - app may be killed when in background", Snackbar.LENGTH_LONG).show();
            }
        });

        // TODO: check gateway status/api key/device validity and update UI accordingly
        registerDeviceBtn.setOnClickListener(view -> {
            String _deviceId = SharedPreferenceHelper.getSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, "");
            if (_deviceId == null || _deviceId.isEmpty()) {
                handleRegisterDevice();
            } else {
                handleUpdateDevice();
            }
        });
        scanQRBtn.setOnClickListener(view -> {
            IntentIntegrator intentIntegrator = new IntentIntegrator(MainActivity.this);
            intentIntegrator.setPrompt("Open your RCS Blink web dashboard and click Register Device to generate QR Code");
            intentIntegrator.setRequestCode(SCAN_QR_REQUEST_CODE);
            intentIntegrator.initiateScan();
        });
        
        checkUpdatesBtn.setOnClickListener(view -> {
            String versionInfo = BuildConfig.VERSION_NAME + "(" + BuildConfig.VERSION_CODE + ")";
            String encodedVersionInfo = android.net.Uri.encode(versionInfo);
            String downloadUrl = "https://textbee.dev/download?currentVersion=" + encodedVersionInfo;
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl));
            startActivity(browserIntent);
        });

        configureFilterBtn.setOnClickListener(view -> {
            Intent filterIntent = new Intent(MainActivity.this, SMSFilterActivity.class);
            startActivity(filterIntent);
        });

        // SMS Send Delay setting: save 3 seconds after user stops typing
        int currentDelay = SharedPreferenceHelper.getSharedPreferenceInt(
                mContext, AppConstants.SHARED_PREFS_SMS_SEND_DELAY_SECONDS_KEY, AppConstants.DEFAULT_SMS_SEND_DELAY_SECONDS);
        smsSendDelayEditText.setText(String.valueOf(currentDelay));
        smsDelaySaveRunnable = this::saveSendDelay;
        smsSendDelayEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                smsDelaySaveHandler.removeCallbacks(smsDelaySaveRunnable);
                smsDelaySaveHandler.postDelayed(smsDelaySaveRunnable, SMS_DELAY_SAVE_DEBOUNCE_MS);
            }
        });
        smsSendDelayEditText.setOnEditorActionListener((v, actionId, event) -> {
            smsDelaySaveHandler.removeCallbacks(smsDelaySaveRunnable);
            saveSendDelay();
            return false;
        });
    }

    private void saveSendDelay() {
        String text = smsSendDelayEditText.getText().toString().trim();
        if (text.isEmpty()) {
            int defaultDelay = AppConstants.DEFAULT_SMS_SEND_DELAY_SECONDS;
            smsSendDelayEditText.setText(String.valueOf(defaultDelay));
            SharedPreferenceHelper.setSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_SMS_SEND_DELAY_SECONDS_KEY, defaultDelay);
            Snackbar.make(smsSendDelayEditText, "SMS send delay saved (" + defaultDelay + " sec)", Snackbar.LENGTH_SHORT).show();
            return;
        }
        try {
            int value = Integer.parseInt(text);
            if (value < 0) {
                value = 0;
                smsSendDelayEditText.setText("0");
                SharedPreferenceHelper.setSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_SMS_SEND_DELAY_SECONDS_KEY, 0);
                Snackbar.make(smsSendDelayEditText, "Minimum delay is 0 seconds. Saved.", Snackbar.LENGTH_SHORT).show();
            } else if (value > 3600) {
                value = 3600;
                smsSendDelayEditText.setText("3600");
                SharedPreferenceHelper.setSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_SMS_SEND_DELAY_SECONDS_KEY, 3600);
                Snackbar.make(smsSendDelayEditText, "Maximum delay is 3600 seconds. Saved.", Snackbar.LENGTH_SHORT).show();
            } else {
                SharedPreferenceHelper.setSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_SMS_SEND_DELAY_SECONDS_KEY, value);
                Snackbar.make(smsSendDelayEditText, "SMS send delay saved (" + value + " sec)", Snackbar.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            int defaultDelay = AppConstants.DEFAULT_SMS_SEND_DELAY_SECONDS;
            smsSendDelayEditText.setText(String.valueOf(defaultDelay));
            SharedPreferenceHelper.setSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_SMS_SEND_DELAY_SECONDS_KEY, defaultDelay);
            Snackbar.make(smsSendDelayEditText, "Invalid value. Reset to " + defaultDelay + " sec.", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void renderAvailableSimOptions() {
        try {
            defaultSimSlotRadioGroup.removeAllViews();
            
            // Set radio group styling for dark mode compatibility
            defaultSimSlotRadioGroup.setBackgroundColor(getResources().getColor(R.color.background_secondary));
            defaultSimSlotRadioGroup.setPadding(16, 8, 16, 8);
            
            // Create the default radio button with proper styling
            RadioButton defaultSimSlotRadioBtn = new RadioButton(mContext);
            defaultSimSlotRadioBtn.setText("Device Default");
            defaultSimSlotRadioBtn.setId((int)123456);
            applyRadioButtonStyle(defaultSimSlotRadioBtn);
            defaultSimSlotRadioGroup.addView(defaultSimSlotRadioBtn);
            
            // Create radio buttons for each SIM with proper styling
            TextBeeUtils.getAvailableSimSlots(mContext).forEach(subscriptionInfo -> {
                String displayName = subscriptionInfo.getDisplayName() != null ? subscriptionInfo.getDisplayName().toString() : "Unknown";
                String simInfo = displayName + " (Subscription ID: " + subscriptionInfo.getSubscriptionId() + ")";
                RadioButton radioButton = new RadioButton(mContext);
                radioButton.setText(simInfo);
                radioButton.setId(subscriptionInfo.getSubscriptionId());
                applyRadioButtonStyle(radioButton);
                defaultSimSlotRadioGroup.addView(radioButton);
            });

            // Check the preferred SIM based on saved preferences
            int preferredSim = SharedPreferenceHelper.getSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_PREFERRED_SIM_KEY, -1);
            if (preferredSim == -1) {
                defaultSimSlotRadioGroup.check(defaultSimSlotRadioBtn.getId());
            } else {
                defaultSimSlotRadioGroup.check(preferredSim);
            }
            
            // Set the listener for SIM selection changes
            defaultSimSlotRadioGroup.setOnCheckedChangeListener((radioGroup, i) -> {
                RadioButton radioButton = findViewById(i);
                if (radioButton == null) {
                    return;
                }
                radioButton.setChecked(true);
                if("Device Default".equals(radioButton.getText().toString())) {
                    SharedPreferenceHelper.clearSharedPreference(mContext, AppConstants.SHARED_PREFS_PREFERRED_SIM_KEY);
                } else {
                    SharedPreferenceHelper.setSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_PREFERRED_SIM_KEY, radioButton.getId());
                }
            });
        } catch (Exception e) {
            Snackbar.make(defaultSimSlotRadioGroup.getRootView(), "Error: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
            Log.e(TAG, "SIM_SLOT_ERROR "+ e.getMessage());
        }
    }
    
    /**
     * Extracts error message from API response, trying multiple sources:
     * 1. Error message from response body (error field)
     * 2. Response message from HTTP headers
     * 3. Generic error with status code as fallback
     */
    private String extractErrorMessage(Response<?> response) {
        // Try to parse error from response body
        try {
            ResponseBody errorBody = response.errorBody();
            if (errorBody != null) {
                String errorBodyString = errorBody.string();
                if (errorBodyString != null && !errorBodyString.isEmpty()) {
                    try {
                        Gson gson = new Gson();
                        RegisterDeviceResponseDTO errorResponse = gson.fromJson(errorBodyString, RegisterDeviceResponseDTO.class);
                        if (errorResponse != null && errorResponse.error != null && !errorResponse.error.isEmpty()) {
                            return errorResponse.error;
                        }
                    } catch (Exception e) {
                        // If JSON parsing fails, try to extract message from raw string
                        Log.d(TAG, "Could not parse error response as JSON: " + errorBodyString);
                    }
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Could not read error body: " + e.getMessage());
        }
        
        // Fall back to response message
        if (response.message() != null && !response.message().isEmpty()) {
            return response.message();
        }
        
        // Final fallback to generic error with status code
        return "An error occurred :( " + response.code();
    }

    private String extractFailureMessage(Throwable throwable) {
        if (throwable == null) {
            return "Network request failed. Please verify API Base URL and API server.";
        }
        String message = throwable.getMessage() != null ? throwable.getMessage().trim() : "";
        if (message.isEmpty()) {
            return "Network request failed. Please verify API Base URL and API server.";
        }
        return message;
    }
    
    /**
     * Apply the custom radio button style to a programmatically created radio button
     */
    private void applyRadioButtonStyle(RadioButton radioButton) {
        // Set text color using the color state list for proper dark/light mode handling
        setRadioButtonTextColor(radioButton);
        
        // Set button tint for the radio circle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                radioButton.setButtonTintList(getResources().getColorStateList(R.color.radio_button_tint, getTheme()));
            } else {
                radioButton.setButtonTintList(getResources().getColorStateList(R.color.radio_button_tint));
            }
        }
        
        // Add proper padding for better touch experience
        radioButton.setPadding(
            radioButton.getPaddingLeft() + 8,
            radioButton.getPaddingTop() + 12,
            radioButton.getPaddingRight(),
            radioButton.getPaddingBottom() + 12
        );
    }
    
    /**
     * Helper method to set radio button text color in a backward-compatible way
     */
    private void setRadioButtonTextColor(RadioButton radioButton) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            radioButton.setTextColor(getResources().getColorStateList(R.color.radio_button_text_color, getTheme()));
        } else {
            radioButton.setTextColor(getResources().getColorStateList(R.color.radio_button_text_color));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != PERMISSION_REQUEST_CODE) {
            return;
        }
        boolean allPermissionsGranted = Arrays.stream(permissions).allMatch(permission -> TextBeeUtils.isPermissionGranted(mContext, permission));
        if (allPermissionsGranted) {
            Snackbar.make(findViewById(R.id.grantSMSPermissionBtn), "All Permissions Granted", Snackbar.LENGTH_SHORT).show();
            grantSMSPermissionBtn.setEnabled(false);
            grantSMSPermissionBtn.setText("Permission Granted");
            renderAvailableSimOptions();
        } else {
            Snackbar.make(findViewById(R.id.grantSMSPermissionBtn), "Please Grant Required Permissions to continue", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void handleRegisterDevice() {
        String newKey = normalizeApiKeyInput(apiKeyEditText.getText().toString());
        apiKeyEditText.setText(newKey);
        String deviceIdInput = deviceIdEditText.getText().toString();
        
        registerDeviceBtn.setEnabled(false);
        registerDeviceBtn.setText("Loading...");
        View view = findViewById(R.id.registerDeviceBtn);

        try {
            FirebaseApp.initializeApp(mContext);
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    String token = "";
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Failed to obtain FCM token during register", task.getException());
                        String cachedToken = fcmTokenEditText.getText() != null
                                ? fcmTokenEditText.getText().toString().trim()
                                : "";
                        token = cachedToken;
                        Snackbar.make(
                                view,
                                "FCM token unavailable. Continuing without push token.",
                                Snackbar.LENGTH_LONG
                        ).show();
                    } else {
                        token = task.getResult();
                    }
                    fcmTokenEditText.setText(token);

                    RegisterDeviceInputDTO registerDeviceInput = new RegisterDeviceInputDTO();
                    registerDeviceInput.setEnabled(true);
                    registerDeviceInput.setFcmToken(token);
                    registerDeviceInput.setBrand(Build.BRAND);
                    registerDeviceInput.setManufacturer(Build.MANUFACTURER);
                    registerDeviceInput.setModel(Build.MODEL);
                    registerDeviceInput.setBuildId(Build.ID);
                    registerDeviceInput.setOs(Build.VERSION.BASE_OS);
                    registerDeviceInput.setAppVersionCode(BuildConfig.VERSION_CODE);
                    registerDeviceInput.setAppVersionName(BuildConfig.VERSION_NAME);
                    
                    // Get device name from input field or default to "brand model"
                    String deviceName = deviceNameEditText.getText().toString().trim();
                    if (deviceName.isEmpty()) {
                        deviceName = Build.BRAND + " " + Build.MODEL;
                    }
                    registerDeviceInput.setName(deviceName);
                    
                    // Collect SIM information
                    SimInfoCollectionDTO simInfoCollection = new SimInfoCollectionDTO();
                    simInfoCollection.setLastUpdated(System.currentTimeMillis());
                    simInfoCollection.setSims(TextBeeUtils.collectSimInfo(mContext));
                    registerDeviceInput.setSimInfo(simInfoCollection);
                    
                    // If the user provided a device ID, use it for updating instead of creating new
                    if (!deviceIdInput.isEmpty()) {
                        Log.d(TAG, "Updating device with deviceId: "+ deviceIdInput);
                        Call<RegisterDeviceResponseDTO> apiCall = ApiManager.getApiService().updateDevice(deviceIdInput, newKey, registerDeviceInput);
                        apiCall.enqueue(new Callback<RegisterDeviceResponseDTO>() {
                            @Override
                            public void onResponse(Call<RegisterDeviceResponseDTO> call, Response<RegisterDeviceResponseDTO> response) {
                                Log.d(TAG, response.toString());
                                if (!response.isSuccessful()) {
                                    Snackbar.make(view, extractErrorMessage(response), Snackbar.LENGTH_LONG).show();
                                    registerDeviceBtn.setEnabled(true);
                                    registerDeviceBtn.setText("Update");
                                    return;
                                }
                                SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_API_KEY_KEY, newKey);
                                Snackbar.make(view, "Device Updated Successfully :)", Snackbar.LENGTH_LONG).show();
                                persistDeviceIdFromResponseOrFallback(response, deviceIdInput);
                                OutboundSmsPullHelper.pullAndEnqueue(mContext, deviceIdInput, newKey);
                                
                                // Update deviceId from response if available
                                if (response.body() != null && response.body().data != null && response.body().data.get("_id") != null) {
                                    deviceId = response.body().data.get("_id").toString();
                                    deviceIdTxt.setText(deviceId);
                                    deviceIdEditText.setText(deviceId);
                                    SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, deviceId);
                                    SharedPreferenceHelper.setSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_GATEWAY_ENABLED_KEY, registerDeviceInput.isEnabled());
                                    gatewaySwitch.setChecked(registerDeviceInput.isEnabled());
                                    
                                    // Sync heartbeatIntervalMinutes from server response
                                    if (response.body().data.get("heartbeatIntervalMinutes") != null) {
                                        Object intervalObj = response.body().data.get("heartbeatIntervalMinutes");
                                        if (intervalObj instanceof Number) {
                                            int intervalMinutes = ((Number) intervalObj).intValue();
                                            SharedPreferenceHelper.setSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_HEARTBEAT_INTERVAL_MINUTES_KEY, intervalMinutes);
                                            Log.d(TAG, "Synced heartbeat interval from server: " + intervalMinutes + " minutes");
                                        }
                                    }
                                    
                                    // Sync device name from server response
                                    if (response.body().data.get("name") != null) {
                                        String deviceName = response.body().data.get("name").toString();
                                        SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_NAME_KEY, deviceName);
                                        deviceNameEditText.setText(deviceName);
                                        Log.d(TAG, "Synced device name from server: " + deviceName);
                                    }
                                    
                                    // Schedule heartbeat if device is enabled
                                    if (registerDeviceInput.isEnabled()) {
                                        HeartbeatManager.scheduleHeartbeat(mContext);
                                    }
                                }
                                
                                // Update stored version information
                                VersionTracker.updateStoredVersion(mContext);
                                
                                registerDeviceBtn.setEnabled(true);
                                registerDeviceBtn.setText("Update");
                            }
                            
                            @Override
                            public void onFailure(Call<RegisterDeviceResponseDTO> call, Throwable t) {
                                Snackbar.make(view, extractFailureMessage(t), Snackbar.LENGTH_LONG).show();
                                Log.e(TAG, "API_ERROR "+ t.getMessage());
                                Log.e(TAG, "API_ERROR "+ t.getLocalizedMessage());
                                TextBeeUtils.logException(t, "Error registering device");
                                registerDeviceBtn.setEnabled(true);
                                registerDeviceBtn.setText("Update");
                            }
                        });
                        return;
                    }

                    Call<RegisterDeviceResponseDTO> apiCall = ApiManager.getApiService().registerDevice(newKey, registerDeviceInput);
                        apiCall.enqueue(new Callback<RegisterDeviceResponseDTO>() {
                            @Override
                            public void onResponse(Call<RegisterDeviceResponseDTO> call, Response<RegisterDeviceResponseDTO> response) {
                                Log.d(TAG, response.toString());
                                if (!response.isSuccessful()) {
                                    Snackbar.make(view, extractErrorMessage(response), Snackbar.LENGTH_LONG).show();
                                    registerDeviceBtn.setEnabled(true);
                                    registerDeviceBtn.setText("Update");
                                    return;
                                }
                                SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_API_KEY_KEY, newKey);
                                Snackbar.make(view, "Device Registration Successful :)", Snackbar.LENGTH_LONG).show();
                            
                            if (response.body() != null && response.body().data != null && response.body().data.get("_id") != null) {
                                deviceId = response.body().data.get("_id").toString();
                                deviceIdTxt.setText(deviceId);
                                deviceIdEditText.setText(deviceId);
                                SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, deviceId);
                                SharedPreferenceHelper.setSharedPreferenceBoolean(mContext, AppConstants.SHARED_PREFS_GATEWAY_ENABLED_KEY, registerDeviceInput.isEnabled());
                                gatewaySwitch.setChecked(registerDeviceInput.isEnabled());
                                
                                // Sync heartbeatIntervalMinutes from server response
                                if (response.body().data.get("heartbeatIntervalMinutes") != null) {
                                    Object intervalObj = response.body().data.get("heartbeatIntervalMinutes");
                                    if (intervalObj instanceof Number) {
                                        int intervalMinutes = ((Number) intervalObj).intValue();
                                        SharedPreferenceHelper.setSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_HEARTBEAT_INTERVAL_MINUTES_KEY, intervalMinutes);
                                        Log.d(TAG, "Synced heartbeat interval from server: " + intervalMinutes + " minutes");
                                    }
                                }
                                
                                // Sync device name from server response
                                if (response.body().data.get("name") != null) {
                                    String deviceName = response.body().data.get("name").toString();
                                    SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_NAME_KEY, deviceName);
                                    deviceNameEditText.setText(deviceName);
                                    Log.d(TAG, "Synced device name from server: " + deviceName);
                                }
                                
                                // Schedule heartbeat if device is enabled
                                if (registerDeviceInput.isEnabled()) {
                                    HeartbeatManager.scheduleHeartbeat(mContext);
                                }
                            }
                            OutboundSmsPullHelper.pullAndEnqueue(mContext, deviceId, newKey);
                            
                            // Update stored version information
                            VersionTracker.updateStoredVersion(mContext);
                            
                            registerDeviceBtn.setEnabled(true);
                            registerDeviceBtn.setText("Update");
                        }
                        @Override
                        public void onFailure(Call<RegisterDeviceResponseDTO> call, Throwable t) {
                            Snackbar.make(view, extractFailureMessage(t), Snackbar.LENGTH_LONG).show();
                            Log.e(TAG, "API_ERROR "+ t.getMessage());
                            Log.e(TAG, "API_ERROR "+ t.getLocalizedMessage());
                            TextBeeUtils.logException(t, "Error registering device");
                            registerDeviceBtn.setEnabled(true);
                            registerDeviceBtn.setText("Update");
                        }
                    });
                });
        } catch (Exception e) {
            Log.w(TAG, "Firebase unavailable during register; continuing without FCM token", e);
            RegisterDeviceInputDTO registerDeviceInput = new RegisterDeviceInputDTO();
            registerDeviceInput.setEnabled(true);
            registerDeviceInput.setFcmToken("");
            registerDeviceInput.setBrand(Build.BRAND);
            registerDeviceInput.setManufacturer(Build.MANUFACTURER);
            registerDeviceInput.setModel(Build.MODEL);
            registerDeviceInput.setBuildId(Build.ID);
            registerDeviceInput.setOs(Build.VERSION.BASE_OS);
            registerDeviceInput.setAppVersionCode(BuildConfig.VERSION_CODE);
            registerDeviceInput.setAppVersionName(BuildConfig.VERSION_NAME);
            String deviceName = deviceNameEditText.getText().toString().trim();
            if (deviceName.isEmpty()) {
                deviceName = Build.BRAND + " " + Build.MODEL;
            }
            registerDeviceInput.setName(deviceName);
            SimInfoCollectionDTO simInfoCollection = new SimInfoCollectionDTO();
            simInfoCollection.setLastUpdated(System.currentTimeMillis());
            simInfoCollection.setSims(TextBeeUtils.collectSimInfo(mContext));
            registerDeviceInput.setSimInfo(simInfoCollection);
            if (!deviceIdInput.isEmpty()) {
                ApiManager.getApiService().updateDevice(deviceIdInput, newKey, registerDeviceInput).enqueue(new Callback<RegisterDeviceResponseDTO>() {
                    @Override
                    public void onResponse(Call<RegisterDeviceResponseDTO> call, Response<RegisterDeviceResponseDTO> response) {
                        if (!response.isSuccessful()) {
                            Snackbar.make(view, extractErrorMessage(response), Snackbar.LENGTH_LONG).show();
                            registerDeviceBtn.setEnabled(true);
                            registerDeviceBtn.setText("Update");
                            return;
                        }
                        SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_API_KEY_KEY, newKey);
                        Snackbar.make(view, "Device Updated Successfully :)", Snackbar.LENGTH_LONG).show();
                        persistDeviceIdFromResponseOrFallback(response, deviceIdInput);
                        OutboundSmsPullHelper.pullAndEnqueue(mContext, deviceIdInput, newKey);
                        registerDeviceBtn.setEnabled(true);
                        registerDeviceBtn.setText("Update");
                    }

                    @Override
                    public void onFailure(Call<RegisterDeviceResponseDTO> call, Throwable t) {
                        Snackbar.make(view, "An error occurred :(", Snackbar.LENGTH_LONG).show();
                        registerDeviceBtn.setEnabled(true);
                        registerDeviceBtn.setText("Update");
                    }
                });
                return;
            }
            ApiManager.getApiService().registerDevice(newKey, registerDeviceInput).enqueue(new Callback<RegisterDeviceResponseDTO>() {
                @Override
                public void onResponse(Call<RegisterDeviceResponseDTO> call, Response<RegisterDeviceResponseDTO> response) {
                    if (!response.isSuccessful()) {
                        Snackbar.make(view, extractErrorMessage(response), Snackbar.LENGTH_LONG).show();
                        registerDeviceBtn.setEnabled(true);
                        registerDeviceBtn.setText("Update");
                        return;
                    }
                    SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_API_KEY_KEY, newKey);
                    Snackbar.make(view, "Device Registration Successful :)", Snackbar.LENGTH_LONG).show();
                    if (response.body() != null && response.body().data != null && response.body().data.get("_id") != null) {
                        deviceId = response.body().data.get("_id").toString();
                    }
                    OutboundSmsPullHelper.pullAndEnqueue(mContext, deviceId, newKey);
                    registerDeviceBtn.setEnabled(true);
                    registerDeviceBtn.setText("Update");
                }

                @Override
                public void onFailure(Call<RegisterDeviceResponseDTO> call, Throwable t) {
                    Snackbar.make(view, extractFailureMessage(t), Snackbar.LENGTH_LONG).show();
                    registerDeviceBtn.setEnabled(true);
                    registerDeviceBtn.setText("Update");
                }
            });
        }
    }

    private void handleUpdateDevice() {
        String apiKey = normalizeApiKeyInput(apiKeyEditText.getText().toString());
        apiKeyEditText.setText(apiKey);
        String deviceIdInput = deviceIdEditText.getText().toString();
        String deviceIdToUse = !deviceIdInput.isEmpty() ? deviceIdInput : deviceId;
        
        registerDeviceBtn.setEnabled(false);
        registerDeviceBtn.setText("Loading...");
        View view = findViewById(R.id.registerDeviceBtn);

        try {
            FirebaseApp.initializeApp(mContext);
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    String token = "";
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Failed to obtain FCM token during update", task.getException());
                        String cachedToken = fcmTokenEditText.getText() != null
                                ? fcmTokenEditText.getText().toString().trim()
                                : "";
                        token = cachedToken;
                        Snackbar.make(
                                view,
                                "FCM token unavailable. Continuing without push token.",
                                Snackbar.LENGTH_LONG
                        ).show();
                    } else {
                        token = task.getResult();
                    }
                    fcmTokenEditText.setText(token);

                    RegisterDeviceInputDTO updateDeviceInput = new RegisterDeviceInputDTO();
                    updateDeviceInput.setEnabled(true);
                    updateDeviceInput.setFcmToken(token);
                    updateDeviceInput.setBrand(Build.BRAND);
                    updateDeviceInput.setManufacturer(Build.MANUFACTURER);
                    updateDeviceInput.setModel(Build.MODEL);
                    updateDeviceInput.setBuildId(Build.ID);
                    updateDeviceInput.setOs(Build.VERSION.BASE_OS);
                    updateDeviceInput.setAppVersionCode(BuildConfig.VERSION_CODE);
                    updateDeviceInput.setAppVersionName(BuildConfig.VERSION_NAME);

                    // Get device name from input field or default to "brand model"
                    String deviceName = deviceNameEditText.getText().toString().trim();
                    if (deviceName.isEmpty()) {
                        deviceName = Build.BRAND + " " + Build.MODEL;
                    }
                    updateDeviceInput.setName(deviceName);

                    // Collect SIM information
                    SimInfoCollectionDTO simInfoCollection = new SimInfoCollectionDTO();
                    simInfoCollection.setLastUpdated(System.currentTimeMillis());
                    simInfoCollection.setSims(TextBeeUtils.collectSimInfo(mContext));
                    updateDeviceInput.setSimInfo(simInfoCollection);

                    Call<RegisterDeviceResponseDTO> apiCall = ApiManager.getApiService().updateDevice(deviceIdToUse, apiKey, updateDeviceInput);
                    apiCall.enqueue(new Callback<RegisterDeviceResponseDTO>() {
                        @Override
                        public void onResponse(Call<RegisterDeviceResponseDTO> call, Response<RegisterDeviceResponseDTO> response) {
                            Log.d(TAG, response.toString());
                            if (!response.isSuccessful()) {
                                Snackbar.make(view, extractErrorMessage(response), Snackbar.LENGTH_LONG).show();
                                registerDeviceBtn.setEnabled(true);
                                registerDeviceBtn.setText("Update");
                                return;
                            }
                            SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_API_KEY_KEY, apiKey);
                            persistDeviceIdFromResponseOrFallback(response, deviceIdToUse);
                            
                            // Update deviceId from response if available
                            if (response.body() != null && response.body().data != null && response.body().data.get("_id") != null) {
                                deviceId = response.body().data.get("_id").toString();
                                SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, deviceId);
                                deviceIdTxt.setText(deviceId);
                                deviceIdEditText.setText(deviceId);
                                
                                // Sync heartbeatIntervalMinutes from server response
                                if (response.body().data.get("heartbeatIntervalMinutes") != null) {
                                    Object intervalObj = response.body().data.get("heartbeatIntervalMinutes");
                                    if (intervalObj instanceof Number) {
                                        int intervalMinutes = ((Number) intervalObj).intValue();
                                        SharedPreferenceHelper.setSharedPreferenceInt(mContext, AppConstants.SHARED_PREFS_HEARTBEAT_INTERVAL_MINUTES_KEY, intervalMinutes);
                                        Log.d(TAG, "Synced heartbeat interval from server: " + intervalMinutes + " minutes");
                                    }
                                }
                                
                                // Sync device name from server response
                                if (response.body().data.get("name") != null) {
                                    String deviceName = response.body().data.get("name").toString();
                                    SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_NAME_KEY, deviceName);
                                    deviceNameEditText.setText(deviceName);
                                    Log.d(TAG, "Synced device name from server: " + deviceName);
                                }
                                
                                // Schedule heartbeat if device is enabled
                                if (updateDeviceInput.isEnabled()) {
                                    HeartbeatManager.scheduleHeartbeat(mContext);
                                }
                            }
                            
                            // Update stored version information
                            VersionTracker.updateStoredVersion(mContext);
                            
                            Snackbar.make(view, "Device Updated Successfully :)", Snackbar.LENGTH_LONG).show();
                            OutboundSmsPullHelper.pullAndEnqueue(mContext, deviceIdToUse, apiKey);
                            registerDeviceBtn.setEnabled(true);
                            registerDeviceBtn.setText("Update");
                        }

                        @Override
                        public void onFailure(Call<RegisterDeviceResponseDTO> call, Throwable t) {
                            Snackbar.make(view, extractFailureMessage(t), Snackbar.LENGTH_LONG).show();
                            Log.e(TAG, "API_ERROR "+ t.getMessage());
                            Log.e(TAG, "API_ERROR "+ t.getLocalizedMessage());
                            TextBeeUtils.logException(t, "Error updating device");
                            registerDeviceBtn.setEnabled(true);
                            registerDeviceBtn.setText("Update");
                        }
                    });
                });
        } catch (Exception e) {
            Log.w(TAG, "Firebase unavailable during update; continuing without FCM token", e);
            RegisterDeviceInputDTO updateDeviceInput = new RegisterDeviceInputDTO();
            updateDeviceInput.setEnabled(true);
            updateDeviceInput.setFcmToken("");
            updateDeviceInput.setBrand(Build.BRAND);
            updateDeviceInput.setManufacturer(Build.MANUFACTURER);
            updateDeviceInput.setModel(Build.MODEL);
            updateDeviceInput.setBuildId(Build.ID);
            updateDeviceInput.setOs(Build.VERSION.BASE_OS);
            updateDeviceInput.setAppVersionCode(BuildConfig.VERSION_CODE);
            updateDeviceInput.setAppVersionName(BuildConfig.VERSION_NAME);
            String deviceName = deviceNameEditText.getText().toString().trim();
            if (deviceName.isEmpty()) {
                deviceName = Build.BRAND + " " + Build.MODEL;
            }
            updateDeviceInput.setName(deviceName);
            SimInfoCollectionDTO simInfoCollection = new SimInfoCollectionDTO();
            simInfoCollection.setLastUpdated(System.currentTimeMillis());
            simInfoCollection.setSims(TextBeeUtils.collectSimInfo(mContext));
            updateDeviceInput.setSimInfo(simInfoCollection);
            ApiManager.getApiService().updateDevice(deviceIdToUse, apiKey, updateDeviceInput).enqueue(new Callback<RegisterDeviceResponseDTO>() {
                @Override
                public void onResponse(Call<RegisterDeviceResponseDTO> call, Response<RegisterDeviceResponseDTO> response) {
                    if (!response.isSuccessful()) {
                        Snackbar.make(view, extractErrorMessage(response), Snackbar.LENGTH_LONG).show();
                        registerDeviceBtn.setEnabled(true);
                        registerDeviceBtn.setText("Update");
                        return;
                    }
                    SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_API_KEY_KEY, apiKey);
                    persistDeviceIdFromResponseOrFallback(response, deviceIdToUse);
                    Snackbar.make(view, "Device Updated Successfully :)", Snackbar.LENGTH_LONG).show();
                    OutboundSmsPullHelper.pullAndEnqueue(mContext, deviceIdToUse, apiKey);
                    registerDeviceBtn.setEnabled(true);
                    registerDeviceBtn.setText("Update");
                }

                @Override
                public void onFailure(Call<RegisterDeviceResponseDTO> call, Throwable t) {
                    Snackbar.make(view, extractFailureMessage(t), Snackbar.LENGTH_LONG).show();
                    registerDeviceBtn.setEnabled(true);
                    registerDeviceBtn.setText("Update");
                }
            });
        }
    }

    private void handleRequestPermissions(View view) {
        boolean allPermissionsGranted = Arrays.stream(AppConstants.requiredPermissions).allMatch(permission -> TextBeeUtils.isPermissionGranted(mContext, permission));
        if (allPermissionsGranted) {
            Snackbar.make(view, "Already got permissions", Snackbar.LENGTH_SHORT).show();
            return;
        }
        String[] permissionsToRequest = Arrays.stream(AppConstants.requiredPermissions).filter(permission -> !TextBeeUtils.isPermissionGranted(mContext, permission)).toArray(String[]::new);
        Snackbar.make(view, "Please Grant Required Permissions to continue", Snackbar.LENGTH_SHORT).show();
        ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCAN_QR_REQUEST_CODE) {
            IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (intentResult == null || intentResult.getContents() == null) {
                Toast.makeText(getBaseContext(), "Canceled", Toast.LENGTH_SHORT).show();
                return;
            }
            String scannedQR = intentResult.getContents();
            String normalizedApiKey = normalizeApiKeyInput(scannedQR);
            if (normalizedApiKey.isEmpty()) {
                Snackbar.make(findViewById(R.id.scanQRButton), "Invalid QR code. Scan Register Device QR from dashboard.", Snackbar.LENGTH_LONG).show();
                return;
            }
            String existingApiKey = SharedPreferenceHelper.getSharedPreferenceString(
                mContext,
                AppConstants.SHARED_PREFS_API_KEY_KEY,
                ""
            );

            // If scanned key changed, clear previously linked device to avoid
            // unauthorized updates against a device owned by another key/account/backend.
            if (!existingApiKey.isEmpty() && !existingApiKey.equals(normalizedApiKey)) {
                deviceId = "";
                deviceIdTxt.setText("");
                deviceIdEditText.setText("");
                SharedPreferenceHelper.clearSharedPreference(mContext, AppConstants.SHARED_PREFS_DEVICE_ID_KEY);
            }

            apiKeyEditText.setText(normalizedApiKey);
            if(deviceIdEditText.getText().toString().isEmpty()) {
                handleRegisterDevice();
            } else {
                handleUpdateDevice();
            }
        }
    }

    private String normalizeApiKeyInput(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }

        try {
            Uri uri = Uri.parse(normalized);
            String apiKeyQueryParam = uri.getQueryParameter("apiKey");
            if (apiKeyQueryParam != null && !apiKeyQueryParam.trim().isEmpty()) {
                return apiKeyQueryParam.trim();
            }
        } catch (Exception ignored) {
        }

        // Some scanners include labels/newlines, keep only the last non-empty token.
        String[] tokens = normalized.split("\\s+");
        if (tokens.length > 1) {
            return tokens[tokens.length - 1].trim();
        }

        return normalized;
    }

    private void persistDeviceIdFromResponseOrFallback(Response<RegisterDeviceResponseDTO> response, String fallbackDeviceId) {
        String resolvedDeviceId = null;
        if (response != null && response.body() != null && response.body().data != null && response.body().data.get("_id") != null) {
            resolvedDeviceId = response.body().data.get("_id").toString();
        } else if (fallbackDeviceId != null && !fallbackDeviceId.trim().isEmpty()) {
            resolvedDeviceId = fallbackDeviceId.trim();
        }

        if (resolvedDeviceId == null || resolvedDeviceId.isEmpty()) {
            return;
        }

        deviceId = resolvedDeviceId;
        deviceIdTxt.setText(resolvedDeviceId);
        deviceIdEditText.setText(resolvedDeviceId);
        SharedPreferenceHelper.setSharedPreferenceString(mContext, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, resolvedDeviceId);
    }

    private void refreshOutboundPullDebugText() {
        long lastPullAtMs = SharedPreferenceHelper.getSharedPreferenceLong(
                mContext,
                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_AT_MS_KEY,
                0L
        );
        int lastCount = SharedPreferenceHelper.getSharedPreferenceInt(
                mContext,
                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_COUNT_KEY,
                0
        );
        String lastError = SharedPreferenceHelper.getSharedPreferenceString(
                mContext,
                AppConstants.SHARED_PREFS_OUTBOUND_PULL_LAST_ERROR_KEY,
                ""
        );

        String timePart = "never";
        if (lastPullAtMs > 0) {
            timePart = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
                    .format(new Date(lastPullAtMs));
        }

        String errorPart = (lastError == null || lastError.trim().isEmpty()) ? "none" : lastError;
        outboundPullDebugTxt.setText(
                "Pull Debug | Last: " + timePart + " | Count: " + lastCount + " | Error: " + errorPart
        );
    }

    private void setupRcsTapTargetUi() {
        rcsAutoClickSwitch.setChecked(ComposerTapHelper.isAutoClickEnabled(mContext));
        refreshRcsTapPositionText();

        rcsAutoClickSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ComposerTapHelper.setAutoClickEnabled(mContext, isChecked);
            if (isChecked && !RCSAccessibilityService.isConnected()) {
                Toast.makeText(mContext, "Enable RCS Blink in Accessibility settings", Toast.LENGTH_LONG).show();
            }
        });

        rcsOpenAccessibilityBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        rcsOverlayPermissionBtn.setOnClickListener(v -> requestOverlayPermission());

        rcsTestTapBtn.setOnClickListener(v -> {
            if (!RCSAccessibilityService.isConnected()) {
                Toast.makeText(mContext, "Enable Accessibility for RCS Blink first", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            ComposerTapHelper.performTestClick(mContext);
            Toast.makeText(mContext, "Test tap sent at saved position", Toast.LENGTH_SHORT).show();
        });

        ComposerTapHelper.setPositionSavedListener((x, y) -> runOnUiThread(this::refreshRcsTapPositionText));
    }

    private void refreshRcsTapPositionText() {
        if (rcsTapPositionTxt == null) {
            return;
        }
        int x = ComposerTapHelper.getTapX(mContext);
        int y = ComposerTapHelper.getTapY(mContext);
        rcsTapPositionTxt.setText("Tap position (screen): " + x + ", " + y);
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            ));
        } else {
            Toast.makeText(mContext, "Overlay permission already granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFloatingCursorIfAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            FloatingCursorService.show(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        pullDebugHandler.removeCallbacks(pullDebugRunnable);
        pullDebugHandler.post(pullDebugRunnable);
        ComposerTapHelper.setPositionSavedListener((x, y) -> runOnUiThread(this::refreshRcsTapPositionText));
        refreshRcsTapPositionText();
        showFloatingCursorIfAllowed();
        smsDelaySaveHandler.postDelayed(this::showFloatingCursorIfAllowed, 400);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pullDebugHandler.removeCallbacks(pullDebugRunnable);
        ComposerTapHelper.setPositionSavedListener(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            FloatingCursorService.hide(this);
        }
    }

}
