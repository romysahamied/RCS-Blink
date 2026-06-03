package com.vernu.sms.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import com.vernu.sms.helpers.ComposerTapHelper;

/**
 * Draggable overlay to mark the Send-button screen position (saved once, used for every RCS auto-tap).
 */
public class FloatingCursorService extends Service {

    private static final String TAG = "FloatingCursorService";
    public static final String ACTION_SHOW = "com.vernu.sms.action.SHOW_FLOATING_CURSOR";
    public static final String ACTION_HIDE = "com.vernu.sms.action.HIDE_FLOATING_CURSOR";

    private static FloatingCursorService runningInstance;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private TextView cursorView;
    private WindowManager.LayoutParams params;
    private boolean viewAttached;

    public static void show(Context context) {
        Intent intent = new Intent(context, FloatingCursorService.class);
        intent.setAction(ACTION_SHOW);
        context.startService(intent);
        mainHandler.postDelayed(() -> {
            if (runningInstance != null) {
                runningInstance.attachOverlayIfNeeded();
            }
        }, 150);
        mainHandler.postDelayed(() -> {
            if (runningInstance != null) {
                runningInstance.attachOverlayIfNeeded();
            }
        }, 500);
    }

    public static void hide(Context context) {
        Intent intent = new Intent(context, FloatingCursorService.class);
        intent.setAction(ACTION_HIDE);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_SHOW;
        if (ACTION_HIDE.equals(action)) {
            detachOverlay();
        } else {
            attachOverlayIfNeeded();
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        runningInstance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prepareCursorView();
    }

    private void prepareCursorView() {
        if (cursorView != null) {
            return;
        }
        cursorView = new TextView(this);
        cursorView.setText("+");
        cursorView.setTextSize(28);
        cursorView.setTextColor(Color.RED);
        cursorView.setAlpha(0.9f);
        cursorView.setPadding(8, 8, 8, 8);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        cursorView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = Math.max(0, (int) (event.getRawX() - (v.getWidth() / 2f)));
                    params.y = Math.max(0, (int) (event.getRawY() - (v.getHeight() / 2f)));
                    if (viewAttached) {
                        windowManager.updateViewLayout(cursorView, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    persistCenterFromView();
                    return true;
            }
            return false;
        });
    }

    private void attachOverlayIfNeeded() {
        prepareCursorView();
        if (windowManager == null || cursorView == null) {
            return;
        }
        if (viewAttached) {
            return;
        }

        int centerX = ComposerTapHelper.getTapX(this);
        int centerY = ComposerTapHelper.getTapY(this);
        params.x = Math.max(0, centerX - 24);
        params.y = Math.max(0, centerY - 24);

        try {
            if (cursorView.getParent() != null) {
                try {
                    windowManager.removeView(cursorView);
                } catch (Exception ignored) {
                }
            }
            windowManager.addView(cursorView, params);
            viewAttached = true;
            cursorView.post(this::persistCenterFromView);
            Log.d(TAG, "Floating + attached");
        } catch (Exception e) {
            viewAttached = false;
            Log.e(TAG, "Failed to attach floating +", e);
        }
    }

    private void detachOverlay() {
        if (!viewAttached || cursorView == null || windowManager == null) {
            viewAttached = false;
            return;
        }
        try {
            windowManager.removeView(cursorView);
        } catch (Exception ignored) {
        }
        viewAttached = false;
    }

    private void persistCenterFromView() {
        if (cursorView == null || !viewAttached) {
            return;
        }
        int[] location = new int[2];
        cursorView.getLocationOnScreen(location);
        int w = cursorView.getWidth();
        int h = cursorView.getHeight();
        if (w <= 0 || h <= 0) {
            cursorView.post(this::persistCenterFromView);
            return;
        }
        int centerX = location[0] + (w / 2);
        int centerY = location[1] + (h / 2);
        ComposerTapHelper.saveTapPosition(this, centerX, centerY);
    }

    @Override
    public void onDestroy() {
        detachOverlay();
        runningInstance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
