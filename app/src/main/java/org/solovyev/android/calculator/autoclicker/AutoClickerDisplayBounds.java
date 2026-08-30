package org.solovyev.android.calculator.autoclicker;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;

/**
 * Reads the usable full-display coordinate space for AutoTap overlays.
 *
 * API 30 deprecated WindowManager#getDefaultDisplay(). On modern Android we use WindowMetrics and
 * choose maximum bounds only when they are strictly larger than the current bounds. This recovers
 * from a transient half-screen/task bound without replacing a valid current landscape orientation
 * merely because current and maximum have the same pixel area. Legacy Android keeps realMetrics.
 */
final class AutoClickerDisplayBounds {

    private static final String TAG = "AutoClickerBounds";

    private AutoClickerDisplayBounds() {
    }

    @NonNull
    static Bounds read(@NonNull Context context,
                       @NonNull WindowManager windowManager,
                       int minimumWidth,
                       int minimumHeight) {
        int width = 0;
        int height = 0;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                final Rect current = windowManager.getCurrentWindowMetrics().getBounds();
                final Rect maximum = windowManager.getMaximumWindowMetrics().getBounds();
                final Rect selected = chooseBest(current, maximum);
                width = selected.width();
                height = selected.height();
            } else {
                final DisplayMetrics metrics = new DisplayMetrics();
                final Display display = windowManager.getDefaultDisplay();
                if (display != null) {
                    //noinspection deprecation
                    display.getRealMetrics(metrics);
                    width = metrics.widthPixels;
                    height = metrics.heightPixels;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to read WindowManager display bounds", error);
        }

        if (width <= 0 || height <= 0) {
            final DisplayMetrics fallback = context.getResources().getDisplayMetrics();
            width = fallback.widthPixels;
            height = fallback.heightPixels;
        }

        return new Bounds(
                Math.max(Math.max(1, minimumWidth), width),
                Math.max(Math.max(1, minimumHeight), height));
    }

    @NonNull
    static Rect chooseBest(@NonNull Rect current, @NonNull Rect maximum) {
        final long currentArea = area(current);
        final long maximumArea = area(maximum);
        return new Rect(maximumArea > currentArea ? maximum : current);
    }

    private static long area(@NonNull Rect bounds) {
        return Math.max(0, bounds.width()) * (long) Math.max(0, bounds.height());
    }

    static final class Bounds {
        final int width;
        final int height;

        Bounds(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
