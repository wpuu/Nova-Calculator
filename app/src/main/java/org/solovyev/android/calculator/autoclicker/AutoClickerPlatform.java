package org.solovyev.android.calculator.autoclicker;

/** Platform gate for the gesture-based AutoTap feature. */
public final class AutoClickerPlatform {

    public static final int MIN_SUPPORTED_SDK = 24;

    private AutoClickerPlatform() {
    }

    public static boolean isSupportedSdk(int sdkInt) {
        return sdkInt >= MIN_SUPPORTED_SDK;
    }
}
