package org.solovyev.android.calculator.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import org.solovyev.android.calculator.BuildConfig;
import org.solovyev.android.calculator.Preferences;
import org.solovyev.android.calculator.R;

import java.util.Locale;

/**
 * Two-point accessibility autoclicker.
 *
 * Product rules enforced here:
 *  - the settings switch only enables/disables the feature and its overlays;
 *  - the two reticles and the floating status indicator are drag-only;
 *  - VOLUME_UP starts clicking and VOLUME_DOWN stops clicking;
 *  - while the feature is enabled both volume keys are consumed, so system volume does not
 *    change as a side effect;
 *  - overlay positions are stored as normalized ratios and survive orientation/resolution
 *    changes;
 *  - overlay attachment is verified instead of trusting an in-memory boolean, allowing the
 *    service to self-heal if Android/another full-screen app drops an overlay window.
 */
public class AutoClickerService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "AutoClickerService";

    private static final int FAILURE_NONE = 0;
    private static final int FAILURE_OVERLAY_PERMISSION = 1;
    private static final int FAILURE_BAD_TOKEN = 2;
    private static final int FAILURE_INVALID_DISPLAY = 3;
    private static final int FAILURE_UNKNOWN = 4;
    private static final int FAILURE_A11Y_OFF = 5;
    private static final int FAILURE_SERVICE_CONNECT_FAILED = 6;
    private static final int FAILURE_TIMEOUT_NO_CIRCLES = 7;

    private static final int CLICK_NOTIFICATION_ID = 1001;
    private static final String CLICK_NOTIFICATION_TAG = "autoclicker";
    private static final String NOTIF_CHANNEL_ID = "autoclicker_channel";

    public static final String ACTION_RECONCILE =
            "org.solovyev.android.calculator.autoclicker.RECONCILE";

    private static final int CIRCLE_SIZE_DP = 26;
    private static final int DEFAULT_INTERVAL_MS = 30;
    private static final int DEFAULT_DURATION_SEC = 60;
    private static final int MIN_INTERVAL_MS = 40;
    private static final long NO_SHOW_WATCHDOG_MS = 4000L;
    private static final long OVERLAY_HEALTH_CHECK_MS = 750L;
    private static final long OVERLAY_ATTACH_VERIFY_MS = 1000L;
    private static final long GESTURE_TIMEOUT_MS = 80L;
    private static final int MAX_CONSECUTIVE_CANCELS = 5;
    private static final int MAX_INIT_RETRIES = 3;
    private static final long INIT_RETRY_BASE_MS = 1000L;

    private int circleSizePx;
    private int screenW;
    private int screenH;
    private int currentIntervalMs = DEFAULT_INTERVAL_MS;
    private int currentDurationSec = DEFAULT_DURATION_SEC;

    private WindowManager windowManager;
    private LayoutInflater inflater;
    private int overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

    private final View[] overlayViews = new View[2];
    private final WindowManager.LayoutParams[] paramsArr = new WindowManager.LayoutParams[2];
    private final boolean[] viewAdded = new boolean[2];

    private View floatingButton;
    private WindowManager.LayoutParams floatingParams;

    private boolean serviceConnected;
    private boolean overlayReady;
    private boolean overlayAttachPending;
    private boolean isClicking;
    private int lastFailure = FAILURE_NONE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private BroadcastReceiver screenOffReceiver;
    private BroadcastReceiver reconcileReceiver;
    private boolean screenOffReceiverRegistered;
    private boolean reconcileReceiverRegistered;
    private AutoClickerDisplayMonitor displayMonitor;

    private long clickStartTime;
    private int targetIndex;
    private boolean isGesturePending;
    private int consecutiveCancelCount;
    private long clickGeneration;
    private long gestureSerial;
    private long pendingGestureSerial;
    private Runnable pendingGestureTimeout;
    private long lastOverlayHealthCheckMs;
    private int initRetryCount;

    private static volatile String sLastErrorType = "";
    private static volatile int sLastRetryCount = 0;

    private final Runnable noShowRunnable = new Runnable() {
        @Override
        public void run() {
            if (preferences == null || !Preferences.AutoClicker.intent.getPreference(preferences)) {
                return;
            }
            if (overlayAttachPending) {
                armNoShowWatchdog();
                return;
            }
            if (isOverlayActuallyReady()) {
                overlayReady = true;
                clearFailureIfRecovered();
                setEffective(true);
                return;
            }
            overlayReady = false;
            // A live onServiceConnected() callback is authoritative proof that accessibility
            // is granted. Do not block on Settings.Secure here: some OEMs update that string
            // late, which was one cause of "enabled but no circles" after returning to the app.
            if (!serviceConnected) {
                markFailure(FAILURE_A11Y_OFF, null);
            } else if (lastFailure == FAILURE_NONE) {
                markFailure(FAILURE_TIMEOUT_NO_CIRCLES, null);
            }
            // One final self-heal attempt. This is intentionally delayed: transient window
            // changes while switching into a game should not require a phone/app restart.
            reconcileState();
        }
    };

    private final Runnable overlayAttachVerifyRunnable = new Runnable() {
        @Override
        public void run() {
            overlayAttachPending = false;
            if (preferences == null
                    || !Preferences.AutoClicker.intent.getPreference(preferences)
                    || !serviceConnected) {
                return;
            }
            if (isOverlayActuallyReady()) {
                overlayReady = true;
                handler.removeCallbacks(noShowRunnable);
                clearFailureIfRecovered();
                setEffective(true);
                return;
            }

            // WindowManager.addView() returning successfully means Android accepted the window,
            // but some OEMs do not report View.isAttachedToWindow() until a later UI traversal.
            // Only treat the overlay as genuinely lost after this delayed verification.
            Log.w(TAG, "Overlay add was accepted but views are still detached after attach grace period");
            overlayReady = false;
            setEffective(false);
            removeOverlayViewsOnly();
            armNoShowWatchdog();
        }
    };

    private final Runnable initRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (serviceConnected && isOverlayActuallyReady()) {
                initRetryCount = 0;
                return;
            }
            if (initRetryCount >= MAX_INIT_RETRIES) {
                initRetryCount = 0;
                return;
            }
            initRetryCount++;
            sLastRetryCount = initRetryCount;
            if (!initService() && initRetryCount < MAX_INIT_RETRIES) {
                handler.postDelayed(this, INIT_RETRY_BASE_MS * initRetryCount);
            } else {
                initRetryCount = 0;
            }
        }
    };

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isClicking) return;

            if (!isOverlayActuallyReady()) {
                // A full-screen transition or OEM window-manager reset may detach overlays.
                // Stop safely, rebuild them, and leave the feature in standby. The user can
                // start again with volume-up after the indicators are restored.
                stopClickingInternal(false);
                overlayReady = false;
                reconcileState();
                return;
            }

            long durationMs = currentDurationSec * 1000L;
            if (System.currentTimeMillis() - clickStartTime >= durationMs) {
                stopClicking();
                return;
            }

            if (isGesturePending) return;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                scheduleNextClick();
                return;
            }

            final View overlay = overlayViews[targetIndex];
            targetIndex = (targetIndex + 1) % overlayViews.length;
            if (overlay == null || !overlay.isAttachedToWindow()) {
                scheduleNextClick();
                return;
            }

            final int width = overlay.getWidth();
            final int height = overlay.getHeight();
            if (width <= 0 || height <= 0) {
                scheduleNextClick();
                return;
            }

            final int[] location = new int[2];
            overlay.getLocationOnScreen(location);
            final int cx = location[0] + width / 2;
            final int cy = location[1] + height / 2;
            dispatchClick(cx, cy);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unable to request key filtering at runtime", t);
        }

        if (!initService()) {
            serviceConnected = false;
            markFailure(FAILURE_SERVICE_CONNECT_FAILED, null);
            setEffective(false);
            scheduleInitRetry();
        }
    }

    private boolean initService() {
        try {
            DisplayMetrics densityMetrics = getResources().getDisplayMetrics();
            circleSizePx = Math.max(1,
                    (int) (CIRCLE_SIZE_DP * densityMetrics.density + 0.5f));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                overlayType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            inflater = LayoutInflater.from(this);
            preferences = PreferenceManager.getDefaultSharedPreferences(this);
            preferences.unregisterOnSharedPreferenceChangeListener(this);
            preferences.registerOnSharedPreferenceChangeListener(this);
            refreshParamsCache();
            refreshDisplayBounds();
            registerScreenOffReceiver();
            registerReconcileReceiver();
            startDisplayMonitor();

            serviceConnected = true;
            cancelInitRetry();
            sLastRetryCount = 0;
            reconcileState();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "initService failed", t);
            markFailure(FAILURE_SERVICE_CONNECT_FAILED, t);
            return false;
        }
    }

    private void scheduleInitRetry() {
        handler.removeCallbacks(initRetryRunnable);
        initRetryCount = 0;
        handler.postDelayed(initRetryRunnable, INIT_RETRY_BASE_MS);
    }

    private void cancelInitRetry() {
        handler.removeCallbacks(initRetryRunnable);
        initRetryCount = 0;
    }

    private void startDisplayMonitor() {
        if (displayMonitor == null) {
            displayMonitor = new AutoClickerDisplayMonitor(this, handler,
                    new AutoClickerDisplayMonitor.Callback() {
                        @Override
                        public void onDisplayGeometryMayHaveChanged() {
                            handleDisplayGeometryChange();
                        }
                    });
        }
        displayMonitor.start();
    }

    private void stopDisplayMonitor() {
        if (displayMonitor != null) {
            displayMonitor.stop();
            displayMonitor = null;
        }
    }

    private void handleDisplayGeometryChange() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!refreshDisplayBounds()) return;

                // Never continue firing at stale coordinates after a display-mode transition.
                // Reposition the deterministic saved targets and require a fresh Volume+ start.
                if (isClicking) {
                    stopClickingInternal(false);
                }
                if (isOverlayActuallyReady()) {
                    repositionVisibleOverlays();
                } else if (preferences != null
                        && Preferences.AutoClicker.intent.getPreference(preferences)) {
                    overlayReady = false;
                    reconcileState();
                }
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (preferences == null || !Preferences.AutoClicker.intent.getPreference(preferences)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastOverlayHealthCheckMs < OVERLAY_HEALTH_CHECK_MS) return;
        lastOverlayHealthCheckMs = now;

        handler.post(new Runnable() {
            @Override
            public void run() {
                boolean displayChanged = refreshDisplayBounds();
                if (displayChanged && isOverlayActuallyReady()) {
                    repositionVisibleOverlays();
                }
                if (!isOverlayActuallyReady()) {
                    overlayReady = false;
                    reconcileState();
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handler.post(new Runnable() {
            @Override
            public void run() {
                refreshDisplayBounds();
                if (isOverlayActuallyReady()) {
                    repositionVisibleOverlays();
                } else if (preferences != null
                        && Preferences.AutoClicker.intent.getPreference(preferences)) {
                    overlayReady = false;
                    reconcileState();
                }
            }
        });
    }

    @Override
    public void onInterrupt() {
        // Do not stop on normal accessibility interruptions. Explicit stop is volume-down,
        // disabling the feature, task removal, lock screen, or duration expiry.
    }

    @Override
    public boolean onUnbind(Intent intent) {
        serviceConnected = false;
        stopDisplayMonitor();
        cancelInitRetry();
        stopClickingInternal(false);
        removeOverlayViewsOnly();
        setEffective(false);
        // Keep auto_clicker_intent. If Android/OEM tears down and later re-binds the
        // accessibility service, onServiceConnected() will restore the overlays automatically.
        return super.onUnbind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Explicitly closing the app is different from an OEM/system service restart: release
        // volume keys and do not resurrect the feature after the task is removed.
        cancelInitRetry();
        stopClickingInternal(false);
        removeOverlayViewsOnly();
        setUserIntent(false);
        setEffective(false);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        serviceConnected = false;
        stopDisplayMonitor();
        handler.removeCallbacks(noShowRunnable);
        handler.removeCallbacks(overlayAttachVerifyRunnable);
        cancelInitRetry();
        stopClickingInternal(false);
        removeOverlayViewsOnly();
        setEffective(false);

        if (preferences != null) {
            try {
                preferences.unregisterOnSharedPreferenceChangeListener(this);
            } catch (Exception ignored) {
            }
        }
        unregisterReceiverSafe(screenOffReceiver);
        unregisterReceiverSafe(reconcileReceiver);
        screenOffReceiver = null;
        reconcileReceiver = null;
        screenOffReceiverRegistered = false;
        reconcileReceiverRegistered = false;

        // Deliberately DO NOT clear auto_clicker_intent here. onDestroy can be caused by the
        // OS/OEM. Keeping the intent is what allows a later service rebind to self-recover.
        super.onDestroy();
    }

    private void registerScreenOffReceiver() {
        if (screenOffReceiver == null) {
            screenOffReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                        onScreenOff();
                    }
                }
            };
        }
        if (screenOffReceiverRegistered) return;
        registerReceiverSafe(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        screenOffReceiverRegistered = true;
    }

    private void registerReconcileReceiver() {
        if (reconcileReceiver == null) {
            reconcileReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!ACTION_RECONCILE.equals(intent.getAction())) return;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            reconcileState();
                        }
                    });
                }
            };
        }
        if (reconcileReceiverRegistered) return;
        registerReceiverSafe(reconcileReceiver, new IntentFilter(ACTION_RECONCILE));
        reconcileReceiverRegistered = true;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiverSafe(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void unregisterReceiverSafe(BroadcastReceiver receiver) {
        if (receiver == null) return;
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
    }

    private void onScreenOff() {
        handler.removeCallbacks(noShowRunnable);
        handler.removeCallbacks(overlayAttachVerifyRunnable);
        cancelInitRetry();
        stopClickingInternal(false);
        removeOverlayViewsOnly();
        setUserIntent(false);
        setEffective(false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Preferences.AutoClicker.intent.getKey().equals(key)) {
            reconcileState();
        } else if (Preferences.AutoClicker.interval.getKey().equals(key)
                || Preferences.AutoClicker.duration.getKey().equals(key)) {
            refreshParamsCache();
        }
    }

    private void refreshParamsCache() {
        if (preferences == null) return;
        currentIntervalMs = Math.max(MIN_INTERVAL_MS,
                parsePref(Preferences.AutoClicker.interval.getPreference(preferences),
                        DEFAULT_INTERVAL_MS));
        currentDurationSec = Math.max(1,
                parsePref(Preferences.AutoClicker.duration.getPreference(preferences),
                        DEFAULT_DURATION_SEC));
    }

    private int parsePref(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * Refresh the overlay coordinate space. API 30+ uses WindowMetrics rather than the deprecated
     * default-display API; older Android retains getRealMetrics through the compatibility helper.
     *
     * @return true when width/height changed.
     */
    private boolean refreshDisplayBounds() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }
        if (windowManager == null) return false;

        AutoClickerDisplayBounds.Bounds bounds = AutoClickerDisplayBounds.read(
                this, windowManager, circleSizePx, circleSizePx);
        boolean changed = bounds.width != screenW || bounds.height != screenH;
        screenW = bounds.width;
        screenH = bounds.height;
        return changed;
    }

    private void reconcileState() {
        if (preferences == null) return;
        refreshDisplayBounds();

        boolean intent = Preferences.AutoClicker.intent.getPreference(preferences);
        if (!intent) {
            handler.removeCallbacks(noShowRunnable);
            handler.removeCallbacks(overlayAttachVerifyRunnable);
            cancelInitRetry();
            stopClickingInternal(false);
            removeOverlayViewsOnly();
            setEffective(false);
            lastFailure = FAILURE_NONE;
            persistFailure();
            return;
        }

        // Being inside this bound AccessibilityService is authoritative. Settings.Secure may
        // briefly lag behind on some OEM ROMs, so using it as a second gate can deadlock the
        // UI in "enabled but no circles" until a reboot/re-toggle.
        if (!serviceConnected) {
            stopClickingInternal(false);
            removeOverlayViewsOnly();
            setEffective(false);
            armNoShowWatchdog();
            return;
        }

        // addView() can be accepted before isAttachedToWindow() becomes true. This pending state
        // must be authoritative even if an accessibility/configuration callback already reset
        // overlayReady=false; otherwise that callback can tear down the brand-new windows before
        // the OEM WindowManager finishes its first attach pass.
        if (overlayAttachPending) {
            if (!isOverlayActuallyReady()) {
                return;
            }
            overlayAttachPending = false;
            handler.removeCallbacks(overlayAttachVerifyRunnable);
        }

        if (overlayReady && !isOverlayActuallyReady()) {
            Log.w(TAG, "Overlay flag was stale; rebuilding detached views");
            overlayReady = false;
            removeOverlayViewsOnly();
        }

        if (!isOverlayActuallyReady() && !addOverlayAtomically()) {
            setEffective(false);
            armNoShowWatchdog();
            return;
        }

        overlayReady = true;
        handler.removeCallbacks(noShowRunnable);
        cancelInitRetry();
        clearFailureIfRecovered();
        setEffective(true);
    }

    private void armNoShowWatchdog() {
        handler.removeCallbacks(noShowRunnable);
        handler.postDelayed(noShowRunnable, NO_SHOW_WATCHDOG_MS);
    }

    private boolean isOverlayActuallyReady() {
        if (windowManager == null) return false;
        for (int i = 0; i < overlayViews.length; i++) {
            View view = overlayViews[i];
            if (!viewAdded[i] || view == null || paramsArr[i] == null
                    || !view.isAttachedToWindow()) {
                return false;
            }
        }
        return floatingButton != null && floatingParams != null
                && floatingButton.isAttachedToWindow();
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean addOverlayAtomically() {
        if (isOverlayActuallyReady()) {
            overlayAttachPending = false;
            overlayReady = true;
            return true;
        }
        removeOverlayViewsOnly();
        try {
            createAndAddOverlay(0);
            createAndAddOverlay(1);
            createAndAddFloatingButton();

            // Do not require isAttachedToWindow() synchronously here. On some OEM builds,
            // WindowManager.addView() returns before the first attach/layout traversal. Treat
            // successful addView() calls as provisionally accepted and verify attachment after
            // a short grace period. This prevents false FAILURE_UNKNOWN/code 4 loops.
            overlayAttachPending = true;
            overlayReady = true;
            handler.removeCallbacks(overlayAttachVerifyRunnable);
            handler.postDelayed(overlayAttachVerifyRunnable, OVERLAY_ATTACH_VERIFY_MS);
            lastFailure = FAILURE_NONE;
            sLastErrorType = "";
            persistFailure();
            return true;
        } catch (SecurityException e) {
            markFailure(FAILURE_OVERLAY_PERMISSION, e);
        } catch (WindowManager.BadTokenException e) {
            markFailure(FAILURE_BAD_TOKEN, e);
        } catch (WindowManager.InvalidDisplayException e) {
            markFailure(FAILURE_INVALID_DISPLAY, e);
        } catch (RuntimeException e) {
            markFailure(FAILURE_UNKNOWN, e);
        }
        removeOverlayViewsOnly();
        return false;
    }

    private void createAndAddOverlay(int index) {
        View view = inflater.inflate(R.layout.auto_clicker_circle, null);
        ImageView reticle = view.findViewById(R.id.auto_clicker_reticle);
        if (reticle != null) {
            reticle.setImageResource(index == 0
                    ? R.drawable.ic_reticle_red : R.drawable.ic_reticle_blue);
        }

        WindowManager.LayoutParams params = baseParams(overlayType);
        params.width = circleSizePx;
        params.height = circleSizePx;
        int baseY = Math.max(0, screenH - Math.max(circleSizePx, 400));
        params.x = index == 0 ? Math.min(100, maxCircleX())
                : Math.max(0, screenW - circleSizePx - 100);
        params.y = Math.min(baseY, maxCircleY());

        overlayViews[index] = view;
        paramsArr[index] = params;
        applySavedCirclePosition(index);
        view.setOnTouchListener(makeCircleTouchListener(index));
        windowManager.addView(view, params);
        viewAdded[index] = true;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createAndAddFloatingButton() {
        TextView button = new TextView(this);
        button.setText("● 待命");
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(14);
        button.setMinWidth(dp(100));
        button.setPadding(dp(14), dp(7), dp(14), dp(7));
        button.setGravity(Gravity.CENTER);
        button.setBackground(makeIndicatorBackground(false));

        WindowManager.LayoutParams params = baseParams(overlayType);
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.x = Math.min(dp(12), Math.max(0, screenW - dp(100)));
        params.y = Math.min(dp(70), Math.max(0, screenH - dp(48)));

        floatingButton = button;
        floatingParams = params;
        applySavedFloatingPosition(false);
        button.setOnTouchListener(makeFloatingTouchListener());
        windowManager.addView(button, params);

        // WRAP_CONTENT has no final width until after addView(). Apply the saved normalized
        // position again once measured, so right-edge positions are exact.
        button.post(new Runnable() {
            @Override
            public void run() {
                applySavedFloatingPosition(true);
            }
        });
        updateFloatingButtonState();
    }

    private GradientDrawable makeIndicatorBackground(boolean running) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(running ? 0xFF2E7D32 : 0xDD1B1B1B);
        bg.setCornerRadius(dp(24));
        return bg;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.max(1, (int) (value * density + 0.5f));
    }

    private WindowManager.LayoutParams baseParams(int type) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private View.OnTouchListener makeCircleTouchListener(final int index) {
        return new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (paramsArr[index] == null) return true;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        refreshDisplayBounds();
                        initialX = paramsArr[index].x;
                        initialY = paramsArr[index].y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        refreshDisplayBounds();
                        paramsArr[index].x = clamp(
                                initialX + Math.round(event.getRawX() - initialTouchX),
                                0, maxCircleX());
                        paramsArr[index].y = clamp(
                                initialY + Math.round(event.getRawY() - initialTouchY),
                                0, maxCircleY());
                        safeUpdateViewLayout(overlayViews[index], paramsArr[index]);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        saveCirclePositions();
                        return true;

                    default:
                        return true;
                }
            }
        };
    }

    private View.OnTouchListener makeFloatingTouchListener() {
        return new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (floatingParams == null) return true;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        refreshDisplayBounds();
                        initialX = floatingParams.x;
                        initialY = floatingParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        refreshDisplayBounds();
                        floatingParams.x = clamp(
                                initialX + Math.round(event.getRawX() - initialTouchX),
                                0, maxFloatingX());
                        floatingParams.y = clamp(
                                initialY + Math.round(event.getRawY() - initialTouchY),
                                0, maxFloatingY());
                        safeUpdateViewLayout(floatingButton, floatingParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        saveFloatingPosition();
                        return true;

                    default:
                        return true;
                }
            }
        };
    }

    private void safeUpdateViewLayout(View view, WindowManager.LayoutParams params) {
        if (windowManager == null || view == null || params == null || !view.isAttachedToWindow()) {
            return;
        }
        try {
            windowManager.updateViewLayout(view, params);
        } catch (RuntimeException e) {
            Log.w(TAG, "updateViewLayout failed; scheduling overlay repair", e);
            overlayReady = false;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    reconcileState();
                }
            });
        }
    }

    private int maxCircleX() {
        return Math.max(0, screenW - circleSizePx);
    }

    private int maxCircleY() {
        return Math.max(0, screenH - circleSizePx);
    }

    private int maxFloatingX() {
        int width = floatingButton != null && floatingButton.getWidth() > 0
                ? floatingButton.getWidth() : dp(100);
        return Math.max(0, screenW - width);
    }

    private int maxFloatingY() {
        int height = floatingButton != null && floatingButton.getHeight() > 0
                ? floatingButton.getHeight() : dp(44);
        return Math.max(0, screenH - height);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void saveCirclePositions() {
        if (preferences == null) return;
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < paramsArr.length; i++) {
            WindowManager.LayoutParams params = paramsArr[i];
            float xr = params == null || maxCircleX() == 0 ? 0f
                    : clamp01((float) params.x / (float) maxCircleX());
            float yr = params == null || maxCircleY() == 0 ? 0f
                    : clamp01((float) params.y / (float) maxCircleY());
            if (i > 0) value.append(';');
            value.append("r:")
                    .append(String.format(Locale.US, "%.6f", xr))
                    .append(',')
                    .append(String.format(Locale.US, "%.6f", yr));
        }
        preferences.edit()
                .putString(Preferences.AutoClicker.positions.getKey(), value.toString())
                .apply();
    }

    private void applySavedCirclePosition(int index) {
        if (preferences == null || paramsArr[index] == null) return;
        String raw = Preferences.AutoClicker.positions.getPreference(preferences);
        if (TextUtils.isEmpty(raw)) return;
        String[] parts = raw.split(";");
        if (index >= parts.length) return;
        applyPositionPart(parts[index], paramsArr[index], maxCircleX(), maxCircleY());
    }

    private void saveFloatingPosition() {
        if (preferences == null || floatingParams == null) return;
        float xr = maxFloatingX() == 0 ? 0f
                : clamp01((float) floatingParams.x / (float) maxFloatingX());
        float yr = maxFloatingY() == 0 ? 0f
                : clamp01((float) floatingParams.y / (float) maxFloatingY());
        String value = "r:" + String.format(Locale.US, "%.6f", xr)
                + "," + String.format(Locale.US, "%.6f", yr);
        preferences.edit()
                .putString(Preferences.AutoClicker.floatingPosition.getKey(), value)
                .apply();
    }

    private void applySavedFloatingPosition(boolean updateWindow) {
        if (preferences == null || floatingParams == null) return;
        String raw = Preferences.AutoClicker.floatingPosition.getPreference(preferences);
        if (!TextUtils.isEmpty(raw)) {
            applyPositionPart(raw, floatingParams, maxFloatingX(), maxFloatingY());
        }
        if (updateWindow) safeUpdateViewLayout(floatingButton, floatingParams);
    }

    /**
     * New format: r:xRatio,yRatio. Legacy absolute x,y is still accepted so existing users
     * do not lose their saved positions on upgrade.
     */
    private void applyPositionPart(String raw, WindowManager.LayoutParams params,
                                   int maxX, int maxY) {
        if (raw == null) return;
        String value = raw.trim();
        boolean ratio = value.startsWith("r:");
        if (ratio) value = value.substring(2);
        String[] xy = value.split(",");
        if (xy.length != 2) return;
        try {
            if (ratio) {
                float xr = clamp01(Float.parseFloat(xy[0].trim()));
                float yr = clamp01(Float.parseFloat(xy[1].trim()));
                params.x = clamp(Math.round(xr * maxX), 0, maxX);
                params.y = clamp(Math.round(yr * maxY), 0, maxY);
            } else {
                int x = Integer.parseInt(xy[0].trim());
                int y = Integer.parseInt(xy[1].trim());
                params.x = clamp(x, 0, maxX);
                params.y = clamp(y, 0, maxY);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(value, 1f));
    }

    private void repositionVisibleOverlays() {
        refreshDisplayBounds();
        for (int i = 0; i < paramsArr.length; i++) {
            if (paramsArr[i] == null || overlayViews[i] == null) continue;
            applySavedCirclePosition(i);
            paramsArr[i].x = clamp(paramsArr[i].x, 0, maxCircleX());
            paramsArr[i].y = clamp(paramsArr[i].y, 0, maxCircleY());
            safeUpdateViewLayout(overlayViews[i], paramsArr[i]);
        }
        applySavedFloatingPosition(true);
    }

    private void removeOverlayViewsOnly() {
        handler.removeCallbacks(overlayAttachVerifyRunnable);
        overlayAttachPending = false;
        if (windowManager != null) {
            for (int i = 0; i < overlayViews.length; i++) {
                View view = overlayViews[i];
                if (view != null) {
                    try {
                        windowManager.removeViewImmediate(view);
                    } catch (RuntimeException ignored) {
                    }
                }
                overlayViews[i] = null;
                paramsArr[i] = null;
                viewAdded[i] = false;
            }
            if (floatingButton != null) {
                try {
                    windowManager.removeViewImmediate(floatingButton);
                } catch (RuntimeException ignored) {
                }
            }
        }
        floatingButton = null;
        floatingParams = null;
        overlayReady = false;
    }

    private void setUserIntent(boolean enabled) {
        if (preferences == null) return;
        if (Preferences.AutoClicker.intent.getPreference(preferences) != enabled) {
            preferences.edit()
                    .putBoolean(Preferences.AutoClicker.intent.getKey(), enabled)
                    .apply();
        }
    }

    private void setEffective(boolean enabled) {
        if (preferences == null) return;
        if (Preferences.AutoClicker.enabled.getPreference(preferences) != enabled) {
            preferences.edit()
                    .putBoolean(Preferences.AutoClicker.enabled.getKey(), enabled)
                    .apply();
        }
    }

    private void persistFailure() {
        if (preferences == null) return;
        String code = String.valueOf(lastFailure);
        String existing = Preferences.AutoClicker.lastFailure.getPreference(preferences);
        if (!TextUtils.equals(code, existing)) {
            preferences.edit()
                    .putString(Preferences.AutoClicker.lastFailure.getKey(), code)
                    .apply();
        }
    }

    private void markFailure(int code, Throwable error) {
        lastFailure = code;
        sLastErrorType = error == null ? "failure code " + code
                : error.getClass().getSimpleName();
        persistFailure();
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Autoclicker failure " + code, error);
        } else {
            Log.w(TAG, "Autoclicker failure " + code + ": " + sLastErrorType);
        }
    }

    private void clearFailureIfRecovered() {
        if (!isOverlayActuallyReady()) return;
        lastFailure = FAILURE_NONE;
        sLastErrorType = "";
        persistFailure();
    }

    private void startClicking() {
        if (isClicking) return;

        refreshDisplayBounds();
        if (!isOverlayActuallyReady()) {
            overlayReady = false;
            reconcileState();
        }
        if (!isOverlayActuallyReady()) return;

        refreshParamsCache();
        clickGeneration++;
        isClicking = true;
        isGesturePending = false;
        pendingGestureSerial = 0L;
        consecutiveCancelCount = 0;
        targetIndex = 0;
        clickStartTime = System.currentTimeMillis();

        // All overlays are placement handles while idle and passive visual indicators while
        // running. None of them may steal touches from the target app during an active run.
        for (int i = 0; i < paramsArr.length; i++) {
            if (paramsArr[i] == null || overlayViews[i] == null) continue;
            paramsArr[i].flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            safeUpdateViewLayout(overlayViews[i], paramsArr[i]);
        }
        if (floatingParams != null && floatingButton != null) {
            floatingParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            safeUpdateViewLayout(floatingButton, floatingParams);
        }

        handler.removeCallbacks(clickRunnable);
        handler.post(clickRunnable);
        showStatusNotification();
        updateFloatingButtonState();
        Toast.makeText(this,
                "连点已开启 · 音量-停止 · 间隔 " + currentIntervalMs + "ms",
                Toast.LENGTH_SHORT).show();
    }

    private void stopClicking() {
        stopClickingInternal(true);
    }

    private void stopClickingInternal(boolean showToast) {
        boolean wasClicking = isClicking;
        isClicking = false;
        clickGeneration++;
        clearPendingGesture();
        handler.removeCallbacks(clickRunnable);
        cancelStatusNotification();

        for (int i = 0; i < paramsArr.length; i++) {
            if (paramsArr[i] == null) continue;
            paramsArr[i].flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (overlayViews[i] != null) {
                safeUpdateViewLayout(overlayViews[i], paramsArr[i]);
            }
        }
        if (floatingParams != null) {
            floatingParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (floatingButton != null) {
                safeUpdateViewLayout(floatingButton, floatingParams);
            }
        }
        updateFloatingButtonState();
        if (showToast && wasClicking) {
            Toast.makeText(this, "连点已停止", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearPendingGesture() {
        isGesturePending = false;
        pendingGestureSerial = 0L;
        if (pendingGestureTimeout != null) {
            handler.removeCallbacks(pendingGestureTimeout);
            pendingGestureTimeout = null;
        }
    }

    private void updateFloatingButtonState() {
        if (!(floatingButton instanceof TextView)) return;
        final TextView button = (TextView) floatingButton;
        button.clearAnimation();
        if (isClicking) {
            button.setText("● 连点中");
            button.setBackground(makeIndicatorBackground(true));
            android.view.animation.AlphaAnimation pulse =
                    new android.view.animation.AlphaAnimation(0.60f, 1.0f);
            pulse.setDuration(700L);
            pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
            pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
            button.startAnimation(pulse);
        } else {
            button.setText("● 待命");
            button.setBackground(makeIndicatorBackground(false));
        }
        // Text width can change between idle/running. Re-apply the stored normalized position
        // after the next layout pass so a right-edge indicator never ends up partly off-screen.
        button.post(new Runnable() {
            @Override
            public void run() {
                applySavedFloatingPosition(true);
            }
        });
    }

    /**
     * Hardware controls are deliberately asymmetric and deterministic:
     * VOLUME_UP = start, VOLUME_DOWN = stop. Neither reticle nor status indicator can toggle.
     */
    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (preferences == null
                || !Preferences.AutoClicker.intent.getPreference(preferences)) {
            return false;
        }

        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP
                && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }

        // Consume DOWN, UP and repeat events while the feature is enabled so changing the
        // click state never also changes system media volume.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                startClicking();
            } else {
                stopClicking();
            }
        }
        return true;
    }

    private void scheduleNextClick() {
        if (!isClicking) return;
        handler.removeCallbacks(clickRunnable);
        handler.postDelayed(clickRunnable, currentIntervalMs);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void dispatchClick(final int x, final int y) {
        if (!isClicking || isGesturePending) return;

        final long runGeneration = clickGeneration;
        final long serial = ++gestureSerial;
        pendingGestureSerial = serial;
        isGesturePending = true;

        final GestureDescription gesture = AutoClickerGestureFactory.stationaryTap(x, y, 10L);

        pendingGestureTimeout = new Runnable() {
            @Override
            public void run() {
                if (runGeneration != clickGeneration
                        || serial != pendingGestureSerial
                        || !isGesturePending) {
                    return;
                }
                isGesturePending = false;
                pendingGestureSerial = 0L;
                pendingGestureTimeout = null;
                if (isClicking) scheduleNextClick();
            }
        };
        handler.postDelayed(pendingGestureTimeout, GESTURE_TIMEOUT_MS);

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                if (!finishGestureIfCurrent(runGeneration, serial)) return;
                consecutiveCancelCount = 0;
                if (isClicking) scheduleNextClick();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                if (!finishGestureIfCurrent(runGeneration, serial)) return;
                consecutiveCancelCount++;
                Log.w(TAG, "Gesture cancelled (" + consecutiveCancelCount + "/"
                        + MAX_CONSECUTIVE_CANCELS + ")");
                if (consecutiveCancelCount >= MAX_CONSECUTIVE_CANCELS) {
                    stopClickingInternal(false);
                    Toast.makeText(AutoClickerService.this,
                            "目标应用连续拦截无障碍手势，连点已停止",
                            Toast.LENGTH_LONG).show();
                } else if (isClicking) {
                    scheduleNextClick();
                }
            }
        }, null);

        if (!accepted && finishGestureIfCurrent(runGeneration, serial)) {
            consecutiveCancelCount++;
            if (consecutiveCancelCount >= MAX_CONSECUTIVE_CANCELS) {
                stopClickingInternal(false);
            } else if (isClicking) {
                scheduleNextClick();
            }
        }
    }

    private boolean finishGestureIfCurrent(long runGeneration, long serial) {
        if (runGeneration != clickGeneration || serial != pendingGestureSerial) {
            return false;
        }
        if (pendingGestureTimeout != null) {
            handler.removeCallbacks(pendingGestureTimeout);
            pendingGestureTimeout = null;
        }
        isGesturePending = false;
        pendingGestureSerial = 0L;
        return true;
    }

    private void showStatusNotification() {
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && manager.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIF_CHANNEL_ID, "连点服务", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("连点运行时状态提醒");
                manager.createNotificationChannel(channel);
            }

            Intent contentIntent = new Intent(
                    this, org.solovyev.android.calculator.CalculatorActivity.class);
            contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentPi = PendingIntent.getActivity(
                    this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE);

            Notification notification = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                    .setContentTitle("连点运行中")
                    .setContentText("音量-停止 · 音量+开始")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setOngoing(true)
                    .setContentIntent(contentPi)
                    .build();
            manager.notify(CLICK_NOTIFICATION_TAG, CLICK_NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            // Notification permission is optional to the click engine. Do not crash the
            // accessibility service merely because Android 13+ notification permission is off.
            Log.w(TAG, "Notification permission denied; autoclick continues", e);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to show autoclick status notification", e);
        }
    }

    private void cancelStatusNotification() {
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(CLICK_NOTIFICATION_TAG, CLICK_NOTIFICATION_ID);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public static boolean isAccessibilityEnabled(Context context) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(value)) return false;
            ComponentName target = new ComponentName(context, AutoClickerService.class);
            for (String item : value.split(":")) {
                item = item.trim();
                if (item.isEmpty()) continue;
                if (target.flattenToString().equalsIgnoreCase(item)) return true;
                ComponentName component = ComponentName.unflattenFromString(item);
                if (target.equals(component)) return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String getDiagnosticSnapshot(Context context, int code) {
        boolean granted = isAccessibilityEnabled(context);
        boolean effective = Preferences.AutoClicker.enabled.getPreference(
                PreferenceManager.getDefaultSharedPreferences(context));
        return "错误码: " + code + "\n"
                + "SDK: " + Build.VERSION.SDK_INT + "\n"
                + "厂商: " + Build.MANUFACTURER + "\n"
                + "型号: " + Build.MODEL + "\n"
                + "无障碍已授权: " + granted + "\n"
                + "双圆圈已生效: " + effective + "\n"
                + "最近异常: " + (sLastErrorType.isEmpty() ? "无" : sLastErrorType) + "\n"
                + "重试次数: " + sLastRetryCount;
    }

    public static String getFailureMessage(Context context, int code) {
        int resId;
        switch (code) {
            case FAILURE_OVERLAY_PERMISSION:
                resId = R.string.auto_clicker_failure_1;
                break;
            case FAILURE_BAD_TOKEN:
                resId = R.string.auto_clicker_failure_2;
                break;
            case FAILURE_INVALID_DISPLAY:
                resId = R.string.auto_clicker_failure_3;
                break;
            case FAILURE_UNKNOWN:
                resId = R.string.auto_clicker_failure_4;
                break;
            case FAILURE_A11Y_OFF:
                resId = R.string.auto_clicker_failure_5;
                break;
            case FAILURE_SERVICE_CONNECT_FAILED:
                resId = R.string.auto_clicker_failure_6;
                break;
            case FAILURE_TIMEOUT_NO_CIRCLES:
                resId = R.string.auto_clicker_failure_7;
                break;
            default:
                return "";
        }
        return context.getString(resId);
    }
}