package org.solovyev.android.calculator.autoclicker;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/** Creates deterministic accessibility gestures used by AutoTap. */
final class AutoClickerGestureFactory {

    private AutoClickerGestureFactory() {
    }

    /**
     * Android documents a zero-length Path (a single moveTo) as a touch that does not move.
     * Keeping this separate prevents accidental regression back to a 1 px swipe.
     */
    @NonNull
    static Path stationaryPath(int x, int y) {
        final Path path = new Path();
        path.moveTo(Math.max(0, x), Math.max(0, y));
        return path;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @NonNull
    static GestureDescription stationaryTap(int x, int y, long durationMs) {
        final long safeDurationMs = Math.max(1L, durationMs);
        final GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(
                stationaryPath(x, y), 0L, safeDurationMs));
        return builder.build();
    }
}
