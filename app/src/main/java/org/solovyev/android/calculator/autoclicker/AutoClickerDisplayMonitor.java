package org.solovyev.android.calculator.autoclicker;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Converts Android display add/change/remove callbacks into one idempotent AutoTap refresh signal.
 * The click service can therefore react even when a game changes display mode without delivering an
 * Activity configuration callback to Nova.
 */
final class AutoClickerDisplayMonitor implements DisplayManager.DisplayListener {

    interface Callback {
        void onDisplayGeometryMayHaveChanged();
    }

    @Nullable
    private final DisplayManager displayManager;
    private final Handler handler;
    private final Callback callback;
    private boolean registered;

    AutoClickerDisplayMonitor(@NonNull Context context,
                              @NonNull Handler handler,
                              @NonNull Callback callback) {
        this.displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        this.handler = handler;
        this.callback = callback;
    }

    void start() {
        if (registered || displayManager == null) return;
        displayManager.registerDisplayListener(this, handler);
        registered = true;
    }

    void stop() {
        if (!registered || displayManager == null) return;
        displayManager.unregisterDisplayListener(this);
        registered = false;
    }

    boolean isRegistered() {
        return registered;
    }

    @Override
    public void onDisplayAdded(int displayId) {
        callback.onDisplayGeometryMayHaveChanged();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        callback.onDisplayGeometryMayHaveChanged();
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        callback.onDisplayGeometryMayHaveChanged();
    }
}
